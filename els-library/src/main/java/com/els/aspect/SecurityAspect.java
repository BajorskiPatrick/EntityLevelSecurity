package com.els.aspect;

import com.els.annotation.Secure;
import com.els.context.SecurityContext;
import com.els.domain.Action;
import com.els.domain.User;
import com.els.service.PermissionResolver;
import com.els.strategies.AccessStrategy.FilterCondition;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP Aspect that intercepts methods annotated with {@link Secure}
 * and enforces Entity Level Security based on resolved permissions.
 *
 * <ul>
 * <li><b>SELECT</b> – uses Hibernate filters (IN / NOT IN) to restrict query
 * results</li>
 * <li><b>INSERT</b> – table-level check: either allowed (ALL) or denied
 * (NONE)</li>
 * <li><b>UPDATE / DELETE</b> – row-level check: extracts entity ID from method
 * arguments and verifies it against the resolved permission set</li>
 * </ul>
 */
@Aspect
@Component
public class SecurityAspect {

    private static final Logger log = LoggerFactory.getLogger(SecurityAspect.class);

    private final PermissionResolver permissionResolver;
    private final SecurityContext securityContext;
    private final EntityManager entityManager;

    public SecurityAspect(PermissionResolver permissionResolver, SecurityContext securityContext,
            EntityManager entityManager) {
        this.permissionResolver = permissionResolver;
        this.securityContext = securityContext;
        this.entityManager = entityManager;
    }

    @Around("@annotation(secure)")
    public Object applySecurity(ProceedingJoinPoint joinPoint, Secure secure) throws Throwable {
        User currentUser = securityContext.getCurrentUser();
        if (currentUser == null) {
            log.warn("No user in SecurityContext – denying access.");
            throw new SecurityException("No authentication context found.");
        }

        String entityName = secure.entity().getSimpleName();
        Action action = secure.action();
        FilterCondition condition = permissionResolver.resolve(currentUser, entityName, action);

        log.info("ELS | User: {} | Entity: {} | Action: {} | Condition: {}",
                currentUser.getUsername(), entityName, action, condition);

        switch (action) {
            case SELECT:
                return handleSelect(joinPoint, condition);
            case INSERT:
                return handleInsert(joinPoint, condition);
            case UPDATE:
            case DELETE:
                return handleUpdateOrDelete(joinPoint, condition, action, entityName);
            default:
                throw new SecurityException("Unknown action: " + action);
        }
    }

    // ---- SELECT: Hibernate filter-based row filtering ----

    private Object handleSelect(ProceedingJoinPoint joinPoint, FilterCondition condition) throws Throwable {
        String operator = condition.operator();
        Session session = entityManager.unwrap(Session.class);
        boolean filterEnabled = false;

        try {
            if ("NONE".equals(operator)) {
                // Return empty result set via impossible filter
                session.enableFilter("elsFilter")
                        .setParameterList("ids", Collections.singletonList(-1L));
                filterEnabled = true;
            } else if ("IN".equals(operator)) {
                List<Object> ids = condition.ids();
                if (ids.isEmpty()) {
                    session.enableFilter("elsFilter")
                            .setParameterList("ids", Collections.singletonList(-1L));
                } else {
                    session.enableFilter("elsFilter")
                            .setParameterList("ids", castToLongs(ids));
                }
                filterEnabled = true;
            } else if ("NOT IN".equals(operator)) {
                session.enableFilter("elsBlacklistFilter")
                        .setParameterList("ids", castToLongs(condition.ids()));
                filterEnabled = true;
            }
            // "ALL" – no filter needed

            return joinPoint.proceed();
        } finally {
            if (filterEnabled) {
                session.disableFilter("elsFilter");
                session.disableFilter("elsBlacklistFilter");
            }
        }
    }

    // ---- INSERT: table-level allow / deny ----

    private Object handleInsert(ProceedingJoinPoint joinPoint, FilterCondition condition) throws Throwable {
        String operator = condition.operator();

        if ("ALL".equals(operator)) {
            return joinPoint.proceed();
        }
        // Any other condition (NONE, IN, NOT IN) means no INSERT permission
        throw new SecurityException("Access Denied: INSERT not permitted on this entity.");
    }

    // ---- UPDATE / DELETE: row-level ID validation ----

    private Object handleUpdateOrDelete(ProceedingJoinPoint joinPoint, FilterCondition condition,
            Action action, String entityName) throws Throwable {
        String operator = condition.operator();

        if ("ALL".equals(operator)) {
            return joinPoint.proceed();
        }
        if ("NONE".equals(operator)) {
            throw new SecurityException(
                    "Access Denied: " + action + " not permitted on entity " + entityName + ".");
        }

        // Extract the target entity ID from method arguments
        Long targetId = extractEntityId(joinPoint);
        if (targetId == null) {
            log.warn("Could not extract entity ID from method arguments – denying {} on {}.", action, entityName);
            throw new SecurityException(
                    "Access Denied: Cannot determine target entity ID for " + action + ".");
        }

        Set<Long> permittedIds = condition.ids().stream()
                .map(this::toLong)
                .collect(Collectors.toSet());

        boolean allowed;
        if ("IN".equals(operator)) {
            allowed = permittedIds.contains(targetId);
        } else if ("NOT IN".equals(operator)) {
            allowed = !permittedIds.contains(targetId);
        } else {
            allowed = false;
        }

        if (!allowed) {
            log.warn("ELS DENIED | User tried to {} {} id={} – not in allowed set.",
                    action, entityName, targetId);
            throw new SecurityException(
                    "Access Denied: " + action + " not permitted on " + entityName + " id=" + targetId + ".");
        }

        return joinPoint.proceed();
    }

    // ---- Helpers ----

    /**
     * Tries to extract the entity ID from the method arguments.
     * Supports two conventions used in demo service methods:
     * 1) First argument is a Long (e.g. deleteById(Long id))
     * 2) First argument is an entity object with a getId() method
     */
    private Long extractEntityId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        Object firstArg = args[0];

        // Convention 1: direct Long ID argument
        if (firstArg instanceof Long id) {
            return id;
        }

        // Convention 2: entity object with getId()
        if (firstArg != null) {
            try {
                Method getIdMethod = firstArg.getClass().getMethod("getId");
                Object idValue = getIdMethod.invoke(firstArg);
                if (idValue instanceof Long id) {
                    return id;
                }
            } catch (Exception e) {
                log.debug("Could not extract ID via getId() from {}: {}",
                        firstArg.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
    }

    private List<Long> castToLongs(List<Object> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyList();
        }
        return rawIds.stream()
                .map(this::toLong)
                .collect(Collectors.toList());
    }

    private Long toLong(Object id) {
        if (id instanceof Long l)
            return l;
        if (id instanceof Number n)
            return n.longValue();
        if (id instanceof String s) {
            if ("*".equals(s))
                return -1L;
            return Long.valueOf(s);
        }
        throw new IllegalArgumentException("Cannot convert to Long: " + id);
    }
}
