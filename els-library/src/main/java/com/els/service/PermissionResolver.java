package com.els.service;

import com.els.cache.PermissionCache;
import com.els.cache.PermissionKey;
import com.els.domain.*;
import com.els.repository.PermissionRepository;
import com.els.strategies.AccessStrategy;
import com.els.strategies.AccessStrategy.FilterCondition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolver {

    private final PermissionRepository permissionRepository;
    private final PermissionCache permissionCache;
    private final Map<AccessType, AccessStrategy> strategies;

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

        // 4. Cache (Flyweight pattern)
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
        // Special Handling for INSERT
        // Project Spec: "Access type (WHITELIST/BLACKLIST) in case of an INSERT action
        // determines whether the user has access or no".
        // And "row ids ... in case of INSERT action equals to null".
        if (action == Action.INSERT) {
            boolean canInsert = permissions.stream()
                    .anyMatch(p -> p.getAccessType() == AccessType.WHITELIST);
            return canInsert
                    ? new FilterCondition("ALL", Collections.emptyList())
                    : new FilterCondition("NONE", Collections.emptyList());
        }

        // Group permissions by access type and collect ids as Objects
        Map<AccessType, List<Object>> idsByType = permissions.stream()
                .collect(Collectors.groupingBy(Permission::getAccessType,
                        Collectors.mapping(p -> parseIds(p.getRowIds()), Collectors.toList())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())));

        // Default deny when no permissions
        if (idsByType.isEmpty()) {
            return new FilterCondition("NONE", Collections.emptyList());
        }

        // Delegate to Strategy pattern for each access type
        FilterCondition whitelistCondition = applyStrategy(idsByType, AccessType.WHITELIST);
        FilterCondition blacklistCondition = applyStrategy(idsByType, AccessType.BLACKLIST);

        // Handle deny-all from blacklist first (highest priority)
        if (blacklistCondition != null && "NONE".equals(blacklistCondition.operator())) {
            return blacklistCondition;
        }

        // If no whitelist, fall back to blacklist (NOT IN) or deny all
        if (whitelistCondition == null) {
            if (blacklistCondition != null) {
                return blacklistCondition;
            }
            return new FilterCondition("NONE", Collections.emptyList());
        }

        // Whitelist exists
        if ("ALL".equals(whitelistCondition.operator())) {
            // Allow all, but exclude blacklisted specific IDs if any
            if (blacklistCondition != null && !blacklistCondition.ids().isEmpty()) {
                return blacklistCondition; // NOT IN list acts on full set
            }
            return whitelistCondition; // ALL
        }

        // Specific allowed IDs minus blacklisted ones
        List<Object> allowed = new ArrayList<>(whitelistCondition.ids());
        if (blacklistCondition != null) {
            allowed.removeAll(blacklistCondition.ids());
        }

        if (allowed.isEmpty()) {
            return new FilterCondition("NONE", Collections.emptyList());
        }

        return new FilterCondition("IN", allowed);
    }

    /**
     * Delegates to the appropriate AccessStrategy (Strategy Pattern).
     * Returns null if no permissions exist for the given access type.
     */
    private FilterCondition applyStrategy(Map<AccessType, List<Object>> idsByType, AccessType type) {
        List<Object> ids = idsByType.get(type);
        if (ids == null) {
            return null;
        }
        AccessStrategy strategy = strategies.get(type);
        if (strategy == null) {
            return null;
        }
        return strategy.generateCondition(ids);
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
