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

        // Group by: who + entity + action
        Map<String, List<Permission>> groups = all.stream()
                .collect(Collectors.groupingBy(this::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<GroupedPermissionDTO> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<Permission> perms = entry.getValue();
            Permission first = perms.get(0);

            String who = first.getUser() != null
                    ? "U: " + first.getUser().getUsername()
                    : "R: " + (first.getRole() != null ? first.getRole().getName() : "?");

            // Collect individual row IDs
            List<Long> rowIds = perms.stream()
                    .map(Permission::getRowId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            List<Long> permIds = perms.stream().map(Permission::getId).collect(Collectors.toList());

            result.add(GroupedPermissionDTO.builder()
                    .who(who)
                    .entityName(first.getEntityName())
                    .action(first.getAction().name())
                    .ids(rowIds)
                    .permissionIds(permIds)
                    .build());
        }
        return result;
    }

    @PostMapping("/permissions")
    public ResponseEntity<?> createPermission(@RequestBody PermissionRequest request) {
        // Validate: rowId must be a positive number or null (for INSERT)
        if (request.getRowId() != null && request.getRowId() <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_FORMAT", "message", "Row ID must be a positive number."));
        }

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

        // Create a new permission record (one per row ID)
        Permission.Builder builder = Permission.builder()
                .entity(request.getEntityName())
                .action(request.getAction())
                .rowId(request.getRowId());

        if (user != null)
            builder.user(user);
        else
            builder.role(role);

        Permission saved = permissionManager.savePermission(builder.build());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/permissions/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionManager.deletePermission(id);
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

    private String groupKey(Permission p) {
        String who = p.getUser() != null
                ? "user:" + p.getUser().getUsername()
                : "role:" + (p.getRole() != null ? p.getRole().getId() : "null");
        return who + "|" + p.getEntityName() + "|" + p.getAction();
    }
}
