package com.els.demo.web;

import com.els.demo.dto.PermissionRequest;
import com.els.domain.Permission;
import com.els.domain.Role;
import com.els.domain.User;
import com.els.manager.PermissionManager;
import com.els.repository.PermissionRepository;
import com.els.repository.RoleRepository;
import com.els.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/permissions")
    public ResponseEntity<Permission> createPermission(@RequestBody PermissionRequest request) {
        Permission.Builder builder = Permission.builder()
                .entity(request.getEntityName())
                .action(request.getAction())
                .accessType(request.getAccessType())
                .rowIds(request.getRowIds());

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            builder.user(user);
        } else if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            builder.role(role);
        } else {
            return ResponseEntity.badRequest().build();
        }

        Permission saved = permissionManager.savePermission(builder.build());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/permissions/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionManager.deletePermission(id);
        return ResponseEntity.ok().build();
    }

    // --- User & Role Management ---

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        // In a real app, password should be encoded
        return userRepository.save(user);
    }

    @PostMapping("/roles/simple")
    public ResponseEntity<Role> createSimpleRole(@RequestParam String name) {
        com.els.domain.SimpleRole role = new com.els.domain.SimpleRole();
        role.setName(name);
        return ResponseEntity.ok(roleRepository.save(role));
    }

    @PostMapping("/roles/composite")
    public ResponseEntity<Role> createCompositeRole(@RequestParam String name) {
        com.els.domain.CompositeRole role = new com.els.domain.CompositeRole();
        role.setName(name);
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

        if (parent instanceof com.els.domain.CompositeRole compositeRole) {
            compositeRole.addChild(child);
            return ResponseEntity.ok(roleRepository.save(compositeRole));
        } else {
            return ResponseEntity.badRequest().build(); // Cannot add child to non-composite
        }
    }
}
