package com.els.aspect;

import com.els.annotation.Secure;
import com.els.context.SecurityContext;
import com.els.domain.Action;
import com.els.domain.User;
import com.els.service.PermissionResolver;
import com.els.service.ResultSecurityValidator;
import com.els.strategies.AccessStrategy;
import com.els.strategies.AccessStrategy.FilterCondition;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP Aspect that intercepts methods annotated with {@link Secure}
 * and enforces Entity Level Security based on resolved permissions.
 *
 * Action and entity are auto-detected:
 * 1. Primary: ASM bytecode analysis of called repository methods
 * 2. Fallback: method name conventions + return type / parameter type analysis
 *
 * SELECT – uses Hibernate filters to restrict query results
 * INSERT – table-level check: allowed or denied
 * UPDATE / DELETE – row-level check via AccessStrategy.isIdPermitted()
 */
@Aspect
@Component
public class SecurityAspect {

    private static final Logger log = LoggerFactory.getLogger(SecurityAspect.class);

    private final PermissionResolver permissionResolver;
    private final SecurityContext securityContext;
    private final EntityManager entityManager;
    private final AccessStrategy activeStrategy;

    /** Cache for resolved method metadata (Action + entity name). */
    private final Map<String, MethodSecurityMeta> metaCache = new ConcurrentHashMap<>();

    private final ResultSecurityValidator resultSecurityValidator;

    public SecurityAspect(PermissionResolver permissionResolver, SecurityContext securityContext,
            EntityManager entityManager,
            @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy,
            ResultSecurityValidator resultSecurityValidator) {
        this.permissionResolver = permissionResolver;
        this.securityContext = securityContext;
        this.entityManager = entityManager;
        this.activeStrategy = activeStrategy;
        this.resultSecurityValidator = resultSecurityValidator;
    }

    @Around("@annotation(com.els.annotation.Secure)")
    public Object applySecurity(ProceedingJoinPoint joinPoint) throws Throwable {
        User currentUser = securityContext.getCurrentUser();
        if (currentUser == null) {
            log.warn("No user in SecurityContext – denying access.");
            throw new SecurityException("No authentication context found.");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MethodSecurityMeta meta = resolveMethodMeta(method);

        String entityName = meta.entityName();
        Action action = meta.action();
        FilterCondition condition = permissionResolver.resolve(currentUser, entityName, action);

        log.info("ELS | User: {} | Entity: {} | Action: {} | Condition: {}",
                currentUser.getUsername(), entityName, action, condition);

        switch (action) {
            case SELECT:
                return handleSelect(joinPoint, condition, currentUser);
            case INSERT:
                return handleInsert(joinPoint, condition);
            case UPDATE:
            case DELETE:
                return handleUpdateOrDelete(joinPoint, condition, action, entityName);
            default:
                throw new SecurityException("Unknown action: " + action);
        }
    }

    // ---- SELECT: Hibernate filter application ----

    private Object handleSelect(ProceedingJoinPoint joinPoint, FilterCondition condition, User currentUser)
            throws Throwable {
        String operator = condition.operator();
        Session session = entityManager.unwrap(Session.class);
        boolean filterEnabled = false;
        Object result;

        try {
            if ("NONE".equals(operator)) {
                session.enableFilter("elsFilter")
                        .setParameterList("ids", Collections.singletonList(-1L));
                filterEnabled = true;
            } else if ("ALL".equals(operator)) {
                // Allow all: no filter needed
            } else if ("IN".equals(operator)) {
                List<Long> ids = condition.ids();
                if (ids.isEmpty()) {
                    session.enableFilter("elsFilter")
                            .setParameterList("ids", Collections.singletonList(-1L));
                } else {
                    session.enableFilter("elsFilter")
                            .setParameterList("ids", ids);
                }
                filterEnabled = true;
            } else if ("NOT IN".equals(operator)) {
                List<Long> ids = condition.ids();
                if (ids.isEmpty()) {
                    // NOT IN empty = allow all, no filter
                } else {
                    session.enableFilter("elsBlacklistFilter")
                            .setParameterList("ids", ids);
                    filterEnabled = true;
                }
            }

            result = joinPoint.proceed();
        } finally {
            if (filterEnabled) {
                session.disableFilter("elsFilter");
                session.disableFilter("elsBlacklistFilter");
            }
        }

        // Post-execution validation for JOINs / Eager loading
        Object validatedResult = resultSecurityValidator.validate(result, currentUser, Action.SELECT);

        return validatedResult;
    }

    // ---- INSERT: table-level allow / deny ----

    private Object handleInsert(ProceedingJoinPoint joinPoint, FilterCondition condition) throws Throwable {
        String operator = condition.operator();

        if ("ALL".equals(operator)) {
            return joinPoint.proceed();
        }
        throw new SecurityException("Access Denied: INSERT not permitted on this entity.");
    }

    // ---- UPDATE / DELETE: row-level access check ----

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

        Long targetId = extractEntityId(joinPoint);
        if (targetId == null) {
            log.warn("Could not extract entity ID from method arguments – denying {} on {}.", action, entityName);
            throw new SecurityException(
                    "Access Denied: Cannot determine target entity ID for " + action + ".");
        }

        Set<Long> permittedIds = new HashSet<>(condition.ids());
        boolean allowed = activeStrategy.isIdPermitted(targetId, permittedIds);

        if (!allowed) {
            log.warn("ELS DENIED | User tried to {} {} id={} – not in allowed set.",
                    action, entityName, targetId);
            throw new SecurityException(
                    "Access Denied: " + action + " not permitted on " + entityName + " id=" + targetId + ".");
        }

        return joinPoint.proceed();
    }

    // ---- Auto-detection of Action and Entity ----

    private MethodSecurityMeta resolveMethodMeta(Method method) {
        String cacheKey = method.getDeclaringClass().getName() + "#" + method.getName()
                + org.objectweb.asm.Type.getMethodDescriptor(method);
        return metaCache.computeIfAbsent(cacheKey, k -> detectMethodMeta(method));
    }

    /**
     * Detects Action and entity name by analyzing the bytecode of the given method
     * using ASM. Falls back to name-based conventions if bytecode analysis fails.
     */
    private MethodSecurityMeta detectMethodMeta(Method method) {
        try {
            // Use ASM to find repository method calls in the annotated method
            List<RepositoryCall> repoCalls = analyzeRepositoryCalls(method);

            if (!repoCalls.isEmpty()) {
                RepositoryCall call = repoCalls.get(0);
                Action action = mapRepositoryMethodToAction(call.methodName(), method.getName());
                String entityName = resolveEntityFromRepositoryClass(call.ownerClassName());

                if (action != null && entityName != null) {
                    log.debug("ASM detected | Method: {} | Action: {} | Entity: {}",
                            method.getName(), action, entityName);
                    return new MethodSecurityMeta(action, entityName);
                }
            }
        } catch (Exception e) {
            log.debug("ASM analysis failed for {}: {}, falling back to conventions.",
                    method.getName(), e.getMessage());
        }

        // Fallback: name + type based detection
        return detectFromConventions(method);
    }

    /**
     * Uses ASM to read the bytecode of the method and find calls to repository
     * interfaces (those extending JpaRepository).
     */
    private List<RepositoryCall> analyzeRepositoryCalls(Method method) throws Exception {
        Class<?> declaringClass = method.getDeclaringClass();
        String classResourcePath = declaringClass.getName().replace('.', '/') + ".class";

        List<RepositoryCall> calls = new ArrayList<>();

        try (InputStream is = declaringClass.getClassLoader().getResourceAsStream(classResourcePath)) {
            if (is == null) {
                return calls;
            }

            ClassReader reader = new ClassReader(is);
            String targetMethodName = method.getName();
            String targetMethodDesc = org.objectweb.asm.Type.getMethodDescriptor(method);

            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals(targetMethodName) && descriptor.equals(targetMethodDesc)) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String callName,
                                    String callDescriptor, boolean isInterface) {
                                // Check if the owner class is a JPA repository
                                String ownerClassName = owner.replace('/', '.');
                                if (isRepositoryClass(ownerClassName)) {
                                    calls.add(new RepositoryCall(ownerClassName, callName));
                                }
                            }
                        };
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        return calls;
    }

    /**
     * Checks if the given class name represents a Spring Data JPA repository.
     */
    private boolean isRepositoryClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return JpaRepository.class.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Maps a repository method name to an ELS Action.
     * Uses the service method name as a tiebreaker for ambiguous cases (save).
     */
    private Action mapRepositoryMethodToAction(String repoMethodName, String serviceMethodName) {
        String repoLower = repoMethodName.toLowerCase();

        if (repoLower.startsWith("find") || repoLower.startsWith("get")
                || repoLower.startsWith("count") || repoLower.startsWith("exists")) {
            return Action.SELECT;
        }
        if (repoLower.startsWith("delete") || repoLower.startsWith("remove")) {
            return Action.DELETE;
        }
        if (repoLower.startsWith("save") || repoLower.startsWith("saveall")) {
            // Ambiguous: save is used for both INSERT and UPDATE
            // Use service method name as tiebreaker
            String svcLower = serviceMethodName.toLowerCase();
            if (svcLower.startsWith("add") || svcLower.startsWith("create") || svcLower.startsWith("insert")) {
                return Action.INSERT;
            }
            if (svcLower.startsWith("update") || svcLower.startsWith("modify") || svcLower.startsWith("edit")) {
                return Action.UPDATE;
            }
            // Default for save: INSERT
            return Action.INSERT;
        }

        return null;
    }

    /**
     * Resolves the entity class name from a repository class.
     * Inspects the generic type parameter of JpaRepository<EntityClass, ID>.
     */
    private String resolveEntityFromRepositoryClass(String repoClassName) {
        try {
            Class<?> repoClass = Class.forName(repoClassName);
            for (java.lang.reflect.Type iface : repoClass.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType pt) {
                    java.lang.reflect.Type rawType = pt.getRawType();
                    if (rawType instanceof Class<?> rawClass
                            && JpaRepository.class.isAssignableFrom(rawClass)) {
                        java.lang.reflect.Type entityType = pt.getActualTypeArguments()[0];
                        if (entityType instanceof Class<?> entityClass) {
                            return entityClass.getSimpleName();
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("Could not resolve entity from repository class {}: {}", repoClassName, e.getMessage());
        }
        return null;
    }

    /**
     * Fallback: detects Action and entity from method name and signature.
     */
    private MethodSecurityMeta detectFromConventions(Method method) {
        String methodName = method.getName().toLowerCase();
        Action action;

        if (methodName.startsWith("delete") || methodName.startsWith("remove")
                || methodName.startsWith("discharge")) {
            action = Action.DELETE;
        } else if (methodName.startsWith("update") || methodName.startsWith("modify")
                || methodName.startsWith("edit")) {
            action = Action.UPDATE;
        } else if (methodName.startsWith("add") || methodName.startsWith("create")
                || methodName.startsWith("insert") || methodName.startsWith("save")) {
            action = Action.INSERT;
        } else {
            action = Action.SELECT;
        }

        String entityName = detectEntityFromSignature(method, action);

        log.debug("Convention detected | Method: {} | Action: {} | Entity: {}",
                method.getName(), action, entityName);
        return new MethodSecurityMeta(action, entityName);
    }

    /**
     * Detects entity name from the method's return type or parameter types.
     */
    private String detectEntityFromSignature(Method method, Action action) {
        // For SELECT: inspect return type
        if (action == Action.SELECT) {
            Class<?> returnType = method.getReturnType();
            if (List.class.isAssignableFrom(returnType) || Collection.class.isAssignableFrom(returnType)) {
                java.lang.reflect.Type genericReturn = method.getGenericReturnType();
                if (genericReturn instanceof ParameterizedType pt) {
                    java.lang.reflect.Type elementType = pt.getActualTypeArguments()[0];
                    if (elementType instanceof Class<?> elementClass
                            && elementClass.isAnnotationPresent(Entity.class)) {
                        return elementClass.getSimpleName();
                    }
                }
            }
            if (returnType.isAnnotationPresent(Entity.class)) {
                return returnType.getSimpleName();
            }
        }

        // For INSERT / UPDATE: inspect first parameter
        if (action == Action.INSERT || action == Action.UPDATE) {
            Class<?>[] paramTypes = method.getParameterTypes();
            for (Class<?> paramType : paramTypes) {
                if (paramType.isAnnotationPresent(Entity.class)) {
                    return paramType.getSimpleName();
                }
            }
        }

        // For DELETE: infer from method name (e.g., deleteDepartment -> Department)
        if (action == Action.DELETE) {
            String name = method.getName();
            for (String prefix : List.of("delete", "remove", "discharge")) {
                if (name.toLowerCase().startsWith(prefix) && name.length() > prefix.length()) {
                    return name.substring(prefix.length());
                }
            }
        }

        // Last resort: check all parameter types and return type for @Entity
        for (Class<?> paramType : method.getParameterTypes()) {
            if (paramType.isAnnotationPresent(Entity.class)) {
                return paramType.getSimpleName();
            }
        }
        Class<?> returnType = method.getReturnType();
        if (returnType.isAnnotationPresent(Entity.class)) {
            return returnType.getSimpleName();
        }

        throw new IllegalStateException(
                "Cannot determine entity for @Secure method: " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName());
    }

    // ---- Helpers ----

    private Long extractEntityId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        Object firstArg = args[0];

        if (firstArg instanceof Long id) {
            return id;
        }

        if (firstArg != null) {
            try {
                java.lang.reflect.Method getIdMethod = firstArg.getClass().getMethod("getId");
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

    /** Immutable pair representing the resolved security metadata. */
    private record MethodSecurityMeta(Action action, String entityName) {
    }

    /** Represents a detected repository method call from ASM analysis. */
    private record RepositoryCall(String ownerClassName, String methodName) {
    }
}
