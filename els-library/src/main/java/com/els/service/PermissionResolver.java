package com.els.service;

import com.els.cache.PermissionCache;
import com.els.cache.PermissionKey;
import com.els.domain.*;
import com.els.repository.PermissionRepository;
import com.els.strategies.AccessStrategy.FilterCondition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolver {

    private final PermissionRepository permissionRepository;
    private final PermissionCache permissionCache;

    public PermissionResolver(PermissionRepository permissionRepository, PermissionCache permissionCache) {
        this.permissionRepository = permissionRepository;
        this.permissionCache = permissionCache;
    }

    public FilterCondition resolve(User user, String entityName, Action action) {
        PermissionKey key = new PermissionKey(user.getUsername(), entityName, action);
        if (permissionCache.contains(key)) {
            return permissionCache.get(key);
        }

        // 1. Gather all roles (traverse Composite)
        Set<Role> effectiveRoles = new HashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                collectRoles(role, effectiveRoles);
            }
        }

        // 2. Fetch Permissions
        List<Permission> permissions = new ArrayList<>();
        // User specific
        permissions.addAll(permissionRepository.findByUserAndEntityNameAndAction(user, entityName, action));
        // Role specific
        if (!effectiveRoles.isEmpty()) {
            permissions.addAll(permissionRepository.findByRoles(effectiveRoles, entityName, action));
        }

        // 3. Coordinate Strategies (Aggregation)
        FilterCondition condition = aggregatePermissions(permissions);

        // 4. Cache
        permissionCache.put(key, condition);

        return condition;
    }

    private void collectRoles(Role root, Set<Role> accumulator) {
        accumulator.add(root);
        if (root instanceof CompositeRole composite) {
            for (Role child : composite.getChildren()) {
                collectRoles(child, accumulator);
            }
        }
    }

    private FilterCondition aggregatePermissions(List<Permission> permissions) {
        Set<Object> allowedIds = new HashSet<>();
        Set<Object> deniedIds = new HashSet<>();
        boolean hasWhitelist = false;

        for (Permission p : permissions) {
            List<String> ids = parseIds(p.getRowIds());
            if (p.getAccessType() == AccessType.WHITELIST) {
                allowedIds.addAll(ids);
                hasWhitelist = true;
            } else {
                deniedIds.addAll(ids);
            }
        }

        // Simplification for the "Single Query Strategy" result
        // If we have mixed, we might return a complex condition or just one.
        // For this demo/standard RLS, we want the list of final IDs to inject into "IN
        // (...)".
        // Calculate Effective Allow Set:

        // If NO Whitelists exist, is it "Allow All" or "Deny All"?
        // Security default: Deny All.
        if (!hasWhitelist) {
            // If only blacklist exists? Then it implies "All except X".
            // That requires "NOT IN" strategy across the whole table.
            if (!deniedIds.isEmpty()) {
                return new FilterCondition("NOT IN", new ArrayList<>(deniedIds));
            }
            // Nothing defined: Deny All (empty IN list)
            return new FilterCondition("IN", List.of()); // ID IN () -> False
        }

        // Whitelist exists: Union of Allowed minus Denied
        allowedIds.removeAll(deniedIds);
        return new FilterCondition("IN", new ArrayList<>(allowedIds));
    }

    private List<String> parseIds(String rowIds) {
        if (rowIds == null || rowIds.isBlank())
            return Collections.emptyList();
        // Simple distinct parse
        return Arrays.stream(rowIds.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
