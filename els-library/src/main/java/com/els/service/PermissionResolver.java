package com.els.service;

import com.els.cache.PermissionCache;
import com.els.cache.PermissionKey;
import com.els.domain.*;
import com.els.repository.PermissionRepository;
import com.els.strategies.AccessStrategy;
import com.els.strategies.AccessStrategy.FilterCondition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolver {

    private final PermissionRepository permissionRepository;
    private final PermissionCache permissionCache;
    private final AccessStrategy activeStrategy;

    public PermissionResolver(PermissionRepository permissionRepository,
            PermissionCache permissionCache,
            @Qualifier("activeAccessStrategy") AccessStrategy activeStrategy) {
        this.permissionRepository = permissionRepository;
        this.permissionCache = permissionCache;
        this.activeStrategy = activeStrategy;
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

        // 3. Aggregate using global strategy
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
        if (permissions.isEmpty()) {
            return new FilterCondition("NONE", Collections.emptyList());
        }

        // INSERT: table-level — if any permission exists, allow; otherwise deny
        if (action == Action.INSERT) {
            return new FilterCondition("ALL", Collections.emptyList());
        }

        // SELECT / UPDATE / DELETE: collect all row IDs from individual permission
        // records
        List<Long> rowIds = permissions.stream()
                .map(Permission::getRowId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Delegate to the global strategy (Whitelist or Blacklist)
        return activeStrategy.generateCondition(rowIds);
    }
}
