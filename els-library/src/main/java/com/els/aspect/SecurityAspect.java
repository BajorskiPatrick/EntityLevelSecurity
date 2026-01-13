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
                // Deny access completely
                throw new SecurityException("Access Denied: You do not have permission to perform this action.");
            } else if ("ALL".equals(operator)) {
                // Allow everything, do not enable any filter
                return joinPoint.proceed();
            }

            // For INSERT, we expect either ALL or NONE.
            // If we get here with IN/NOT IN for INSERT, it's a structural error in logic,
            // but effectively means "allow specific IDs", which is weird for INSERT.
            // Strictly blocking Partial Insert permissions if needed, but per spec
            // "INSERT... user has access or no".
            // So if we are here for INSERT, it implies partial access? Spec says "row
            // ids... null", so partial shouldn't happen.
            // We'll treat partial filters for INSERT as ineffective and thus deny/warn or
            // just proceed (if filter ignored).
            // Safer to block given the spec.
            if ("INSERT".equals(secure.action().name())) {
                // Should have been handled by ALL or NONE above.
                // If we have specific IDs, it contradicts the spec "row ids = null".
                throw new SecurityException("Access Denied: Partial permissions not supported for INSERT.");
            }

            // FILTERING (Select, Update, Delete)
            if ("IN".equals(operator)) {
                List<Object> ids = condition.ids();
                if (ids.isEmpty()) {
                    // Start of safety check. Should be NONE really.
                    throw new SecurityException("Access Denied: Empty Allow List.");
                }

                session.enableFilter("elsFilter").setParameterList("ids", ids);
                filterEnabled = true;
            } else if ("NOT IN".equals(operator)) {
                session.enableFilter("elsBlacklistFilter").setParameterList("ids", condition.ids());
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
}
