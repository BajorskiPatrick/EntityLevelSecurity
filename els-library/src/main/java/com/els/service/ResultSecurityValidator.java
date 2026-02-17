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

@Service
public class ResultSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ResultSecurityValidator.class);

    private final PermissionResolver permissionResolver;
    private final AccessStrategy activeStrategy;

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
        ID_ONLY, // Replace denied child with ID-only object
        ALLOW // Do not validate children (allow all JOINs if parent is allowed)
    }

    public ResultSecurityValidator(PermissionResolver permissionResolver,
            @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy) {
        this.permissionResolver = permissionResolver;
        this.activeStrategy = activeStrategy;
    }

    public void setJoinBehavior(JoinBehavior behavior) {
        this.joinBehavior = behavior;
    }

    public Object validate(Object result, User user, Action action) {
        if (result == null) {
            return null;
        }
        // Track in-progress objects for cycle detection ONLY
        // No result caching (validationCache) and no permission caching
        // (conditionCache)
        Set<Object> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());

        return traverseAndValidate(result, user, action, inProgress);
    }

    private Object traverseAndValidate(Object node, User user, Action action, Set<Object> inProgress) {
        if (node == null) {
            return null;
        }

        // Check cycle (currently visiting)
        if (!inProgress.add(node)) {
            return node; // Assume valid to break cycle
        }

        Object result = doValidate(node, user, action, inProgress);

        inProgress.remove(node);

        return result;
    }

    private Object doValidate(Object node, User user, Action action, Set<Object> inProgress) {

        // Handle Iterables (List, Set, etc.)
        if (node instanceof Iterable<?> iterable) {
            Collection<Object> filteredCollection;
            if (node instanceof Set) {
                filteredCollection = new HashSet<>();
            } else {
                filteredCollection = new ArrayList<>();
            }

            for (Object item : iterable) {
                Object validatedItem = traverseAndValidate(item, user, action, inProgress);
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
                Object validatedValue = traverseAndValidate(entry.getValue(), user, action, inProgress);
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
                    Object validatedItem = traverseAndValidate(item, user, action, inProgress);
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
            if (!isEntityAllowed(node, clazz, user, action)) {
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
        return traverseFields(node, clazz, user, action, inProgress);
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

    private boolean isEntityAllowed(Object entity, Class<?> entityClass, User user, Action action) {
        String entityName = entityClass.getSimpleName();
        Long id = extractId(entity, entityClass);

        if (id == null) {
            log.warn("Could not extract ID for entity {}, skipping validation.", entityName);
            return true;
        }

        // No caching of conditions
        FilterCondition condition = permissionResolver.resolve(user, entityName, action);

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
            Set<Object> inProgress) {

        // If configured to ALLOW all joins, do not validate children
        if (joinBehavior == JoinBehavior.ALLOW) {
            return node;
        }

        // No caching of traversable fields
        List<Field> fields = getTraversableFields(clazz);

        for (Field field : fields) {
            try {
                Object value = field.get(node);
                if (value != null) {
                    Object validatedValue = traverseAndValidate(value, user, action, inProgress);

                    if (validatedValue != value) {
                        // Value has changed (filtered or sanitized)
                        if (validatedValue == null) {
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
