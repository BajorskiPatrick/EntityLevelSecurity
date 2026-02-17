package com.els.service;

import com.els.domain.Action;
import com.els.domain.User;
import com.els.strategies.AccessStrategy;
import com.els.strategies.AccessStrategy.FilterCondition;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates security rules on the result of a SELECT query, recursively
 * checking
 * nested entities.
 * Ensures that even if an entity is fetched via JOIN/Eager Loading, the user
 * has
 * access to it.
 */
@Service
public class ResultSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ResultSecurityValidator.class);

    private final PermissionResolver permissionResolver;
    private final AccessStrategy activeStrategy;

    // Cache Class -> List of fields to traverse (non-primitive, non-ignored)
    private final Map<Class<?>, List<Field>> traversalCache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${els.join.behavior:STRICT}")
    private String joinBehaviorConfig;

    private JoinBehavior joinBehavior;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            this.joinBehavior = JoinBehavior.valueOf(joinBehaviorConfig.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid els.join.behavior value: {}. Defaulting to STRICT.", joinBehaviorConfig);
            this.joinBehavior = JoinBehavior.STRICT;
        }
    }

    public enum JoinBehavior {
        STRICT, // Filter out parent if child is denied
        ID_ONLY // Replace denied child with ID-only object
    }

    public ResultSecurityValidator(PermissionResolver permissionResolver,
            @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy) {
        this.permissionResolver = permissionResolver;
        this.activeStrategy = activeStrategy;
    }

    // For testing/manual config
    public void setJoinBehavior(JoinBehavior behavior) {
        this.joinBehavior = behavior;
    }

    /**
     * Validates the given result object graph.
     * Use Identity set to track visited instances and avoid cycles.
     */
    /**
     * Validates and filters the given result object graph.
     * Returns the filtered result.
     * If a single entity is invalid, returns null.
     * If a collection contains invalid entities, they are removed.
     */
    public Object validate(Object result, User user, Action action) {
        if (result == null) {
            return null;
        }
        // Cache validated results (Object -> ValidatedObject or null)
        Map<Object, Object> validationCache = new IdentityHashMap<>();
        // Track in-progress objects for cycle detection
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
        // Cache FilterCondition per entity name to avoid repeated DB lookups
        Map<String, FilterCondition> conditionCache = new HashMap<>();

        return traverseAndValidate(result, user, action, validationCache, inProgress, conditionCache);
    }

    private Object traverseAndValidate(Object node, User user, Action action, Map<Object, Object> validationCache,
            Set<Object> inProgress, Map<String, FilterCondition> conditionCache) {
        if (node == null) {
            return null;
        }

        // Check if already validated
        if (validationCache.containsKey(node)) {
            return validationCache.get(node);
        }

        // Check cycle (currently visiting)
        if (!inProgress.add(node)) {
            return node; // Assume valid to break cycle
        }

        Object result = doValidate(node, user, action, validationCache, inProgress, conditionCache);

        inProgress.remove(node);
        validationCache.put(node, result);

        return result;
    }

    private Object doValidate(Object node, User user, Action action, Map<Object, Object> validationCache,
            Set<Object> inProgress, Map<String, FilterCondition> conditionCache) {

        // Handle Iterables (List, Set, etc.)
        if (node instanceof Iterable<?> iterable) {
            Collection<Object> filteredCollection;
            if (node instanceof Set) {
                filteredCollection = new HashSet<>();
            } else {
                filteredCollection = new ArrayList<>();
            }

            for (Object item : iterable) {
                Object validatedItem = traverseAndValidate(item, user, action, validationCache, inProgress,
                        conditionCache);
                if (validatedItem != null) {
                    filteredCollection.add(validatedItem);
                }
            }
            return filteredCollection;
        }

        // Handle Maps
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> filteredMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object validatedValue = traverseAndValidate(entry.getValue(), user, action, validationCache, inProgress,
                        conditionCache);
                if (validatedValue != null) {
                    filteredMap.put(entry.getKey(), validatedValue);
                }
            }
            return filteredMap;
        }

        // Handle Arrays
        if (node.getClass().isArray()) {
            if (node instanceof Object[] objArray) {
                List<Object> filteredList = new ArrayList<>();
                for (Object item : objArray) {
                    Object validatedItem = traverseAndValidate(item, user, action, validationCache, inProgress,
                            conditionCache);
                    if (validatedItem != null) {
                        filteredList.add(validatedItem);
                    }
                }
                return filteredList.toArray();
            }
            return node;
        }

        // Check for Hibernate Proxy initialization
        if (!Hibernate.isInitialized(node)) {
            return node;
        }

        // It's a single object (potentially an Entity)
        Class<?> clazz = Hibernate.getClass(node);

        // Skip basic Java types to improve performance
        if (isBasicType(clazz)) {
            return node;
        }

        // If it's an Entity, validate permissions
        if (clazz.isAnnotationPresent(Entity.class)) {
            if (!isEntityAllowed(node, clazz, user, action, conditionCache)) {
                if (joinBehavior == JoinBehavior.ID_ONLY) {
                    Object idOnlyProxy = createIdOnlyProxy(node, clazz);
                    if (idOnlyProxy != null) {
                        return idOnlyProxy;
                    }
                }
                return null;
            }
        }

        // Traverse fields
        return traverseFields(node, clazz, user, action, validationCache, inProgress, conditionCache);
    }

    private Object createIdOnlyProxy(Object original, Class<?> clazz) {
        Long id = extractId(original, clazz);
        if (id == null)
            return null;

        try {
            Object proxy = clazz.getDeclaredConstructor().newInstance();
            // Try setId method
            try {
                Method setId = clazz.getMethod("setId", Long.class);
                setId.invoke(proxy, id);
                return proxy;
            } catch (Exception e1) {
                // Try field access
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.isAnnotationPresent(Id.class)) {
                        f.setAccessible(true);
                        f.set(proxy, id);
                        return proxy;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create ID-only proxy for {}", clazz.getName(), e);
        }
        return null;
    }

    private boolean isEntityAllowed(Object entity, Class<?> entityClass, User user, Action action,
            Map<String, FilterCondition> conditionCache) {
        String entityName = entityClass.getSimpleName();
        Long id = extractId(entity, entityClass);

        if (id == null) {
            log.warn("Could not extract ID for entity {}, skipping validation.", entityName);
            return true;
        }

        FilterCondition condition = conditionCache.computeIfAbsent(entityName,
                k -> permissionResolver.resolve(user, k, action));

        String operator = condition.operator();

        boolean allowed = true;

        if ("ALL".equals(operator)) {
            allowed = true;
        } else if ("NONE".equals(operator)) {
            allowed = false;
        } else {
            // IN or NOT IN
            Set<Long> permittedIds = new HashSet<>(condition.ids());
            allowed = activeStrategy.isIdPermitted(id, permittedIds);
        }

        if (!allowed) {
            log.warn("Result Validation Failed | User: {} | Entity: {} | ID: {} | Action: {} - Filtering out.",
                    user.getUsername(), entityName, id, action);
            return false;
        }
        return true;
    }

    private Long extractId(Object entity, Class<?> clazz) {
        try {
            // Try getId() method first
            Method getIdMethod = clazz.getMethod("getId");
            Object idVal = getIdMethod.invoke(entity);
            if (idVal instanceof Long l) {
                return l;
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try @Id field
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                try {
                    field.setAccessible(true);
                    Object idVal = field.get(entity);
                    if (idVal instanceof Long l) {
                        return l;
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to access @Id field on {}", clazz.getName());
                }
            }
        }
        return null;
    }

    private Object traverseFields(Object node, Class<?> clazz, User user, Action action,
            Map<Object, Object> validationCache,
            Set<Object> inProgress, Map<String, FilterCondition> conditionCache) {
        List<Field> fields = traversalCache.computeIfAbsent(clazz, this::getTraversableFields);

        for (Field field : fields) {
            try {
                Object value = field.get(node);
                if (value != null) {
                    Object validatedValue = traverseAndValidate(value, user, action, validationCache, inProgress,
                            conditionCache);

                    if (validatedValue != value) {
                        // Value has changed (filtered or sanitized)
                        if (validatedValue == null) {
                            // If STRICT mode, or if ID_ONLY failed to create proxy -> invalidate parent
                            // to avoid leaking partial state or unexpected nulls?
                            // Or should we just set to null in ID_ONLY mode?
                            // Let's assume STRICT requirement applies if we can't produce a valid result.
                            // BUT, for collections, null items are already removed by traverseAndValidate
                            // logic for Iterables.
                            // This check is for single fields.

                            if (joinBehavior == JoinBehavior.STRICT) {
                                return null;
                            }
                            // In ID_ONLY mode, if we get null here, it means even proxy creation failed.
                            // We can set field to null.
                            field.set(node, null);
                        } else {
                            // Value changed but is not null (e.g. sanitized proxy)
                            field.set(node, validatedValue);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to traverse field {} in {}", field.getName(), clazz.getName());
            }
        }
        return node;
    }

    private List<Field> getTraversableFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                // Skip static and transient fields?
                // For now, assume any object reference could be an entity or contain one.
                // Filter out primitives and basic types early to save reflection time in loop
                if (!isBasicType(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isBasicType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz.getName().startsWith("java.lang") ||
                clazz.getName().startsWith("java.math") ||
                clazz.getName().startsWith("java.time") ||
                clazz.isEnum();
    }
}
