package com.els.demo.web;

import com.els.demo.dto.GroupedPermissionDTO;
import com.els.demo.dto.PermissionRequest;
import com.els.domain.*;
import com.els.manager.PermissionManager;
import com.els.repository.PermissionRepository;
import com.els.repository.RoleRepository;
import com.els.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionManager permissionManager;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository,
            PermissionRepository permissionRepository, PermissionManager permissionManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionManager = permissionManager;
    }

    @GetMapping("/users")
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/roles")
    public List<Role> getRoles() {
        return roleRepository.findAll();
    }

    @GetMapping("/permissions")
    public List<Permission> getPermissions() {
        return permissionRepository.findAll();
    }

    @GetMapping("/permissions/grouped")
    public List<GroupedPermissionDTO> getGroupedPermissions() {
        List<Permission> all = permissionRepository.findAll();

        // Group by composite key: who + entity + action + accessType
        Map<String, List<Permission>> groups = all.stream()
                .collect(Collectors.groupingBy(this::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<GroupedPermissionDTO> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<Permission> perms = entry.getValue();
            Permission first = perms.get(0);

            String who = first.getUser() != null
                    ? "U: " + first.getUser().getUsername()
                    : "R: " + (first.getRole() != null ? first.getRole().getName() : "?");

            // Merge all rowIds
            Set<String> mergedIds = new LinkedHashSet<>();
            for (Permission p : perms) {
                mergedIds.addAll(parseIds(p.getRowIds()));
            }

            List<Long> permIds = perms.stream().map(Permission::getId).collect(Collectors.toList());

            result.add(GroupedPermissionDTO.builder()
                    .who(who)
                    .entityName(first.getEntityName())
                    .action(first.getAction().name())
                    .accessType(first.getAccessType().name())
                    .ids(new ArrayList<>(mergedIds))
                    .permissionIds(permIds)
                    .build());
        }
        return result;
    }

    @PostMapping("/permissions")
    public ResponseEntity<?> createPermission(@RequestBody PermissionRequest request) {
        // Validate rowIds format
        String validationError = validateRowIds(request.getRowIds());
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_FORMAT", "message", validationError));
        }

        // Expand ranges: "1-5,8" → "1,2,3,4,5,8"
        String expandedIds = expandRanges(request.getRowIds());

        User user = null;
        Role role = null;

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } else if (request.getRoleId() != null) {
            role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));
        } else {
            return ResponseEntity.badRequest().build();
        }

        // Try to find existing permission with same key for merge
        Optional<Permission> existing;
        if (user != null) {
            existing = permissionRepository.findByUserAndEntityNameAndActionAndAccessType(
                    user, request.getEntityName(), request.getAction(), request.getAccessType());
        } else {
            existing = permissionRepository.findByRoleAndEntityNameAndActionAndAccessType(
                    role, request.getEntityName(), request.getAction(), request.getAccessType());
        }

        if (existing.isPresent()) {
            // MERGE: add new IDs to existing permission
            Permission perm = existing.get();
            String merged = mergeIds(perm.getRowIds(), expandedIds);
            perm.setRowIds(merged);
            Permission saved = permissionManager.savePermission(perm);
            return ResponseEntity.ok(saved);
        } else {
            // CREATE new permission
            Permission.Builder builder = Permission.builder()
                    .entity(request.getEntityName())
                    .action(request.getAction())
                    .accessType(request.getAccessType())
                    .rowIds(expandedIds);

            if (user != null)
                builder.user(user);
            else
                builder.role(role);

            Permission saved = permissionManager.savePermission(builder.build());
            return ResponseEntity.ok(saved);
        }
    }

    @DeleteMapping("/permissions/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionManager.deletePermission(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/permissions/{id}/ids")
    public ResponseEntity<Void> removeIdsFromPermission(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String idsToRemove = body.get("idsToRemove");
        if (idsToRemove == null || idsToRemove.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Permission perm = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        Set<String> current = new LinkedHashSet<>(parseIds(perm.getRowIds()));
        Set<String> toRemove = new HashSet<>(parseIds(idsToRemove));
        current.removeAll(toRemove);

        if (current.isEmpty()) {
            // No IDs left → delete entire permission
            permissionManager.deletePermission(id);
        } else {
            perm.setRowIds(String.join(",", current));
            permissionManager.savePermission(perm);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }

    @PostMapping("/roles/simple")
    public ResponseEntity<Role> createSimpleRole(@RequestParam String name) {
        SimpleRole role = SimpleRole.builder()
                .name(name)
                .build();
        return ResponseEntity.ok(roleRepository.save(role));
    }

    @PostMapping("/roles/composite")
    public ResponseEntity<Role> createCompositeRole(@RequestParam String name) {
        CompositeRole role = CompositeRole.builder()
                .name(name)
                .build();
        return ResponseEntity.ok(roleRepository.save(role));
    }

    @PostMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<User> assignRoleToUser(@PathVariable Long userId, @PathVariable Long roleId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        user.getRoles().add(role);
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PostMapping("/roles/{parentId}/children/{childId}")
    public ResponseEntity<Role> addChildToRole(@PathVariable Long parentId, @PathVariable Long childId) {
        Role parent = roleRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Parent Role not found"));
        Role child = roleRepository.findById(childId).orElseThrow(() -> new RuntimeException("Child Role not found"));

        if (parent instanceof CompositeRole compositeRole) {
            if (createsCycle(parent, child)) {
                return ResponseEntity.badRequest().body(null);
            }
            compositeRole.addChild(child);
            return ResponseEntity.ok(roleRepository.save(compositeRole));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean createsCycle(Role parent, Role child) {
        if (Objects.equals(parent.getId(), child.getId())) {
            return true;
        }
        if (!(child instanceof CompositeRole compositeChild)) {
            return false;
        }
        // DFS over children to see if parent is reachable from child
        Deque<Role> stack = new ArrayDeque<>(compositeChild.getChildren());
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Role current = stack.pop();
            if (current.getId() != null && !visited.add(current.getId())) {
                continue;
            }
            if (Objects.equals(current.getId(), parent.getId())) {
                return true;
            }
            if (current instanceof CompositeRole currentComposite) {
                stack.addAll(currentComposite.getChildren());
            }
        }
        return false;
    }

    static String validateRowIds(String input) {
        if (input == null || input.isBlank())
            return null; // empty is valid (INSERT)
        input = input.trim();
        if ("*".equals(input))
            return null; // wildcard is valid

        for (String part : input.split(",")) {
            part = part.trim();
            if (part.isEmpty())
                continue;
            if ("*".equals(part))
                continue;

            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                if (bounds.length != 2 || bounds[0].trim().isEmpty() || bounds[1].trim().isEmpty()) {
                    return "Invalid range format: '" + part + "'. Use format: start-end (e.g. 1-10)";
                }
                try {
                    Long.parseLong(bounds[0].trim());
                    Long.parseLong(bounds[1].trim());
                } catch (NumberFormatException e) {
                    return "Range contains non-numeric values: '" + part + "'";
                }
            } else {
                try {
                    Long.parseLong(part);
                } catch (NumberFormatException e) {
                    return "Invalid ID: '" + part + "'. IDs must be numbers, ranges (1-10), or wildcard (*)";
                }
            }
        }
        return null;
    }

    static String expandRanges(String input) {
        if (input == null || input.isBlank())
            return null;
        input = input.trim();
        if ("*".equals(input))
            return "*";

        Set<String> result = new LinkedHashSet<>();
        for (String part : input.split(",")) {
            part = part.trim();
            if (part.isEmpty())
                continue;
            if ("*".equals(part))
                return "*";

            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                long start = Long.parseLong(bounds[0].trim());
                long end = Long.parseLong(bounds[1].trim());
                for (long i = Math.min(start, end); i <= Math.max(start, end); i++) {
                    result.add(String.valueOf(i));
                }
            } else {
                result.add(part);
            }
        }

        return result.isEmpty() ? null : String.join(",", result);
    }

    static String mergeIds(String existing, String incoming) {
        if (existing == null && incoming == null)
            return null;
        if ("*".equals(existing) || "*".equals(incoming))
            return "*";

        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(parseIds(existing));
        merged.addAll(parseIds(incoming));

        return merged.isEmpty() ? null : String.join(",", merged);
    }

    private String groupKey(Permission p) {
        String who = p.getUser() != null
                ? "user:" + p.getUser().getUsername()
                : "role:" + (p.getRole() != null ? p.getRole().getId() : "null");
        return who + "|" + p.getEntityName() + "|" + p.getAction() + "|" + p.getAccessType();
    }

    private static List<String> parseIds(String rowIds) {
        if (rowIds == null || rowIds.isBlank())
            return Collections.emptyList();
        return Arrays.stream(rowIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
