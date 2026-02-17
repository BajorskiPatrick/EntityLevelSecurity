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

@Service
public class ResultSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ResultSecurityValidator.class);

    private final PermissionResolver permissionResolver;
    private final AccessStrategy activeStrategy;

    private final Map<Class<?>, List<Field>> traversalCache = new ConcurrentHashMap<>();

    public ResultSecurityValidator(PermissionResolver permissionResolver,
                                   @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy) {
        this.permissionResolver = permissionResolver;
        this.activeStrategy = activeStrategy;
    }

    public void validate(Object result, User user, Action action) {
        if (result == null) {
            return;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, FilterCondition> conditionCache = new HashMap<>();

        boolean allowed = traverseAndValidate(result, user, action, visited, conditionCache, true);

        if (!allowed) {
            throw new SecurityException("Access Denied: Root entity is not permitted.");
        }
    }

    private boolean traverseAndValidate(Object node,
                                        User user,
                                        Action action,
                                        Set<Object> visited,
                                        Map<String, FilterCondition> conditionCache,
                                        boolean isRoot) {

        if (node == null) {
            return true;
        }

        if (!visited.add(node)) {
            return true;
        }

        // Iterable handling
        if (node instanceof Iterable<?> iterable) {
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();
                boolean allowed = traverseAndValidate(item, user, action, visited, conditionCache, false);
                if (!allowed) {
                    iterator.remove();
                }
            }
            return true;
        }

        // Map handling
        if (node instanceof Map<?, ?> map) {
            Iterator<?> iterator = map.values().iterator();
            while (iterator.hasNext()) {
                Object value = iterator.next();
                boolean allowed = traverseAndValidate(value, user, action, visited, conditionCache, false);
                if (!allowed) {
                    iterator.remove();
                }
            }
            return true;
        }

        // Array handling
        if (node.getClass().isArray()) {
            if (node instanceof Object[] array) {
                for (int i = 0; i < array.length; i++) {
                    boolean allowed = traverseAndValidate(array[i], user, action, visited, conditionCache, false);
                    if (!allowed) {
                        array[i] = null;
                    }
                }
            }
            return true;
        }

        if (!Hibernate.isInitialized(node)) {
            return true;
        }

        Class<?> clazz = Hibernate.getClass(node);

        if (isBasicType(clazz)) {
            return true;
        }

        // Entity validation
        if (clazz.isAnnotationPresent(Entity.class)) {
            boolean entityAllowed = validateEntity(node, clazz, user, action, conditionCache, isRoot);
            if (!entityAllowed) {
                return false;
            }
        }

        // Traverse fields
        List<Field> fields = traversalCache.computeIfAbsent(clazz, this::getTraversableFields);

        for (Field field : fields) {
            try {
                Object value = field.get(node);
                if (value != null) {
                    boolean allowed = traverseAndValidate(value, user, action, visited, conditionCache, false);
                    if (!allowed) {
                        field.set(node, null);
                    }
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to traverse field {} in {}", field.getName(), clazz.getName());
            }
        }

        return true;
    }

    private boolean validateEntity(Object entity,
                                   Class<?> entityClass,
                                   User user,
                                   Action action,
                                   Map<String, FilterCondition> conditionCache,
                                   boolean isRoot) {

        String entityName = entityClass.getSimpleName();
        Long id = extractId(entity, entityClass);

        if (id == null) {
            log.warn("Could not extract ID for entity {}, skipping validation.", entityName);
            return true;
        }

        FilterCondition condition = conditionCache.computeIfAbsent(entityName,
                k -> permissionResolver.resolve(user, k, action));

        String operator = condition.operator();
        boolean allowed;

        if ("ALL".equals(operator)) {
            allowed = true;
        } else if ("NONE".equals(operator)) {
            allowed = false;
        } else {
            Set<Long> permittedIds = new HashSet<>(condition.ids());
            allowed = activeStrategy.isIdPermitted(id, permittedIds);
        }

        if (!allowed) {
            if (isRoot) {
                log.warn("Root entity denied | User: {} | Entity: {} | ID: {} | Action: {}",
                        user.getUsername(), entityName, id, action);
                return false;
            } else {
                log.warn("Nested entity filtered | User: {} | Entity: {} | ID: {} | Action: {}",
                        user.getUsername(), entityName, id, action);
                return false;
            }
        }

        return true;
    }

    private Long extractId(Object entity, Class<?> clazz) {
        try {
            Method getIdMethod = clazz.getMethod("getId");
            Object idVal = getIdMethod.invoke(entity);
            if (idVal instanceof Long l) {
                return l;
            }
        } catch (Exception ignored) {
        }

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
