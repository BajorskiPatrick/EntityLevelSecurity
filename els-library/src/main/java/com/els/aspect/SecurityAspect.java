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

import java.util.Collections;
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
            // "IN" Strategy
            if ("IN".equals(condition.operator())) {
                List<Object> ids = condition.ids();
                if (ids.isEmpty()) {
                    // Empty IN list usually means "Deny All".
                    // Hibernate filter parameter list cannot be empty.
                    // Hack: Pass a dummy impossible ID like -1.
                    ids = Collections.singletonList(-1L);
                }

                // We assume a standard filter name "elsFilter" is defined on the entity
                // with a parameter "ids".
                // @FilterDef(name="elsFilter", parameters=@ParamDef(name="ids",
                // type=Long.class))
                // @Filter(name="elsFilter", condition="id IN (:ids)")
                session.enableFilter("elsFilter").setParameterList("ids", ids);
                filterEnabled = true;
            } else if ("NOT IN".equals(condition.operator())) {
                // Similar logic, if we had a blacklist filter
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
