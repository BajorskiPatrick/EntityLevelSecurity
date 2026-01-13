package com.els.aspect;

import com.els.annotation.Secure;
import com.els.context.SecurityContext;
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

import java.util.List;

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
            log.warn("No user in context. Security check skipped (or should deny?). Proceeding with caution.");
            // In strict mode, throw exception. For demo, maybe proceed or block.
            // Let's assume strict:
            throw new SecurityException("No authentication context found.");
        }

        String entityName = secure.entity().getSimpleName(); // e.g. "Product"
        FilterCondition condition = permissionResolver.resolve(currentUser, entityName, secure.action());

        log.info("Applying security for User: {} on Entity: {} -> Condition: {}", currentUser.getUsername(), entityName,
                condition);

        Session session = entityManager.unwrap(Session.class);
        boolean filterEnabled = false;

        try {
            // NEW LOGIC: Strict Enforcement
            String operator = condition.operator();

            if ("NONE".equals(operator)) {
                // Deny access by filtering everything out, instead of throwing exception
                // This allows returning empty lists for UI consistency
                session.enableFilter("elsFilter").setParameterList("ids", java.util.Collections.singletonList(-1L));
                filterEnabled = true;
                return joinPoint.proceed();
            } else if ("ALL".equals(operator)) {
                // Allow everything, do not enable any filter
                return joinPoint.proceed();
            }

            // For INSERT, we expect either ALL or NONE.
            if ("INSERT".equals(secure.action().name())) {
                // If not ALL or NONE, it implies partial. Spec says 'row ids = null'.
                // We treat partial inserts as Blocked (NONE) effectively if we reach here?
                // Or we can just throw exception as it's a configuration error.
                // Let's keep the exception for INSERT consistency check, but maybe relaxed if
                // needed.
                throw new SecurityException("Access Denied: Partial permissions not supported for INSERT.");
            }

            // FILTERING (Select, Update, Delete)
            if ("IN".equals(operator)) {
                List<Object> ids = condition.ids();
                if (ids.isEmpty()) {
                    // Empty whitelist = Block All
                    session.enableFilter("elsFilter").setParameterList("ids", java.util.Collections.singletonList(-1L));
                    filterEnabled = true;
                } else {
                    session.enableFilter("elsFilter").setParameterList("ids", castIds(ids));
                    filterEnabled = true;
                }
            } else if ("NOT IN".equals(operator)) {
                session.enableFilter("elsBlacklistFilter").setParameterList("ids", castIds(condition.ids()));
                filterEnabled = true;
            }

            return joinPoint.proceed();
        } finally {
            if (filterEnabled) {
                session.disableFilter("elsFilter");
                session.disableFilter("elsBlacklistFilter"); // safe to disable even if not enabled
            }
        }
    }

    private List<Object> castIds(List<Object> rawIds) {
        if (rawIds == null || rawIds.isEmpty())
            return rawIds;
        // Attempt to convert strings to Long if they look like numbers
        // This is a naive heuristic for this demo project where all IDs are Longs.
        try {
            return rawIds.stream()
                    .map(id -> {
                        if (id instanceof String s) {
                            // Handle wildcard if leaks here, though should be handled by ALL operator check
                            if ("*".equals(s))
                                return -1L;
                            return Long.valueOf(s);
                        }
                        return id;
                    })
                    .collect(java.util.stream.Collectors.toList());
        } catch (NumberFormatException e) {
            // Fallback: return raw if parsing fails (maybe they ARE strings)
            return rawIds;
        }
    }
}
