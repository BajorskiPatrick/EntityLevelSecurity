package com.els.service;

import com.els.cache.PermissionCache;
import com.els.cache.PermissionKey;
import com.els.config.AccessTypeConfig;
import com.els.domain.*;
import com.els.repository.PermissionRepository;
import com.els.strategies.AccessStrategy;
import com.els.strategies.AccessStrategy.FilterCondition;
import org.springframework.stereotype.Service;
import com.els.config.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolver {

    private final PermissionRepository permissionRepository;
    private final PermissionCache permissionCache;
    private final Map<AccessType, AccessStrategy> strategies;
    private final AccessStrategy defaultStrategy;
    private final AccessType defaultStrategyType;

    public PermissionResolver(PermissionRepository permissionRepository,
            PermissionCache permissionCache,
            Map<String, AccessStrategy> strategiesByName) {
        this.permissionRepository = permissionRepository;
        this.permissionCache = permissionCache;
        // Map Spring bean names to AccessType enum (Strategy Pattern injection)
        this.strategies = new EnumMap<>(AccessType.class);
        strategiesByName.forEach((name, strategy) -> {
            if ("whitelistStrategy".equalsIgnoreCase(name)) {
                this.strategies.put(AccessType.WHITELIST, strategy);
            } else if ("blacklistStrategy".equalsIgnoreCase(name)) {
                this.strategies.put(AccessType.BLACKLIST, strategy);
            }
        });
        // ! TYMCZASOWE - pobierz oba (typ i instancję strategii)
        this.defaultStrategyType = AccessTypeConfig.DEFAULT_STRATEGY;
        this.defaultStrategy = this.strategies.get(this.defaultStrategyType);
    }

    public FilterCondition resolve(User user, String entityName, Action action) {
        PermissionKey key = new PermissionKey(user.getUsername(), entityName, action);
        if (permissionCache.contains(key)) {
            return permissionCache.get(key);
        }

        // 1. Gather all roles (traverse Composite pattern hierarchy)
        Set<Role> effectiveRoles = new HashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                collectRoles(role, effectiveRoles);
            }
        }

        // 2. Fetch Permissions
        List<Permission> permissions = new ArrayList<>();
        permissions.addAll(permissionRepository.findByUserAndEntityNameAndAction(user, entityName, action));
        if (!effectiveRoles.isEmpty()) {
            permissions.addAll(permissionRepository.findByRoles(effectiveRoles, entityName, action));
        }

        // 3. Aggregate using Strategy pattern
        FilterCondition condition = aggregatePermissions(permissions, action);

        // 4. Cache
        permissionCache.put(key, condition);

        return condition;
    }

    /**
     * Recursively collects all effective roles (Composite pattern traversal).
     */
    private void collectRoles(Role root, Set<Role> accumulator) {
        accumulator.add(root);
        if (root instanceof CompositeRole composite) {
            for (Role child : composite.getChildren()) {
                collectRoles(child, accumulator);
            }
        }
    }

    private FilterCondition aggregatePermissions(List<Permission> permissions, Action action) {
        // Używaj globalnie ustawionej strategii
        AccessType configuredStrategy = defaultStrategyType;

        if (action == Action.INSERT) {
            // Dla WHITELIST: pozwól jeśli są uprawnienia
            if (configuredStrategy == AccessType.WHITELIST) {
                boolean hasPermission = permissions.stream()
                        .anyMatch(p -> p.getAccessType() == AccessType.WHITELIST);
                return hasPermission
                        ? new FilterCondition("ALL", Collections.emptyList())
                        : new FilterCondition("NONE", Collections.emptyList());
            } else {
                // Dla BLACKLIST: odmów jeśli są zakazy
                boolean hasBlacklist = permissions.stream()
                        .anyMatch(p -> p.getAccessType() == AccessType.BLACKLIST);
                return hasBlacklist
                        ? new FilterCondition("NONE", Collections.emptyList())
                        : new FilterCondition("ALL", Collections.emptyList());
            }
        }

        // Zbierz IDs tylko dla skonfigurowanej strategii
        List<Object> ids = permissions.stream()
                .filter(p -> p.getAccessType() == configuredStrategy)
                .flatMap(p -> parseIds(p.getRowIds()).stream())
                .collect(Collectors.toList());

        // Jeśli brak uprawnień dla skonfigurowanej strategii
        if (ids.isEmpty()) {
            if (configuredStrategy == AccessType.WHITELIST) {
                return new FilterCondition("NONE", Collections.emptyList()); // WHITELIST: brak = odmów
            } else {
                return new FilterCondition("ALL", Collections.emptyList()); // BLACKLIST: brak = pozwól
            }
        }

        // Użyj defaultStrategy do generacji warunku
        return defaultStrategy.generateCondition(ids);
    }

    private List<Object> parseIds(String rowIds) {
        if (rowIds == null || rowIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rowIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
