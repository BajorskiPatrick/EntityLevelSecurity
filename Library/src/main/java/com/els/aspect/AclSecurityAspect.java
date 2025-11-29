package com.els.aspect;

import com.els.annotation.RequiresAcl;
import com.els.context.AclContext;
import com.els.context.AclUserProvider; // import nowego interfejsu
import com.els.logger.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;

@Aspect
public class AclSecurityAspect {
    private final Logger logger = Logger.getInstance();

    // Wstrzykujemy strategię pobierania usera.
    // Jeśli aplikacja jej nie dostarczy, mamy fallback.
    @Autowired(required = false)
    private AclUserProvider userProvider;

    @Around("@annotation(requiresAcl)")
    public Object manageSecurityContext(ProceedingJoinPoint joinPoint, RequiresAcl requiresAcl) throws Throwable {
        try {
            // Pobieramy ID dynamicznie
            String userId = (userProvider != null) ? userProvider.getCurrentUserId() : "anonymous";

            logger.log("Activating ACL Security for user: " + userId);
            AclContext.setCurrentUser(userId);

            return joinPoint.proceed();
        } finally {
            logger.log("Deactivating ACL Security");
            AclContext.clear();
        }
    }
}