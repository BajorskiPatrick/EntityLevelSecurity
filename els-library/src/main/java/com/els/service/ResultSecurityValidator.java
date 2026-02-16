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

    public ResultSecurityValidator(PermissionResolver permissionResolver,
            @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy) {
        this.permissionResolver = permissionResolver;
        this.activeStrategy = activeStrategy;
    }

    /**
     * Validates the given result object graph.
     * Use Identity set to track visited instances and avoid cycles.
     */
    public void validate(Object result, User user, Action action) {
        if (result == null) {
            return;
        }
        // Use IdentityHashMap to track visited objects to prevent cycles
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        // Cache FilterCondition per entity name to avoid repeated DB lookups
        Map<String, FilterCondition> conditionCache = new HashMap<>();

        traverseAndValidate(result, user, action, visited, conditionCache);
    }

    private void traverseAndValidate(Object node, User user, Action action, Set<Object> visited,
            Map<String, FilterCondition> conditionCache) {
        if (node == null) {
            return;
        }

        // Check cycle
        if (!visited.add(node)) {
            return;
        }

        // Handle Iterables (List, Set, etc.)
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                traverseAndValidate(item, user, action, visited, conditionCache);
            }
            return;
        }

        // Handle Maps
        if (node instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                traverseAndValidate(value, user, action, visited, conditionCache);
            }
            return;
        }

        // Handle Arrays
        if (node.getClass().isArray()) {
            if (node instanceof Object[] objArray) {
                for (Object item : objArray) {
                    traverseAndValidate(item, user, action, visited, conditionCache);
                }
            }
            return;
        }

        // Check for Hibernate Proxy initialization
        if (!Hibernate.isInitialized(node)) {
            return;
        }

        // It's a single object (potentially an Entity)
        Class<?> clazz = Hibernate.getClass(node);

        // Skip basic Java types to improve performance
        if (isBasicType(clazz)) {
            return;
        }

        // If it's an Entity, validate permissions
        if (clazz.isAnnotationPresent(Entity.class)) {
            validateEntity(node, clazz, user, action, conditionCache);
        }

        // Traverse fields
        traverseFields(node, clazz, user, action, visited, conditionCache);
    }

    private void validateEntity(Object entity, Class<?> entityClass, User user, Action action,
            Map<String, FilterCondition> conditionCache) {
        String entityName = entityClass.getSimpleName();
        Long id = extractId(entity, entityClass);

        if (id == null) {
            log.warn("Could not extract ID for entity {}, skipping validation.", entityName);
            return;
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
            log.warn("Result Validation Failed | User: {} | Entity: {} | ID: {} | Action: {}",
                    user.getUsername(), entityName, id, action);
            throw new SecurityException(
                    "Access Denied: You do not have permission to view " + entityName + " with ID " + id);
        }
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

    private void traverseFields(Object node, Class<?> clazz, User user, Action action, Set<Object> visited,
            Map<String, FilterCondition> conditionCache) {
        List<Field> fields = traversalCache.computeIfAbsent(clazz, this::getTraversableFields);

        for (Field field : fields) {
            try {
                Object value = field.get(node);
                if (value != null) {
                    traverseAndValidate(value, user, action, visited, conditionCache);
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to traverse field {} in {}", field.getName(), clazz.getName());
            }
        }
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
