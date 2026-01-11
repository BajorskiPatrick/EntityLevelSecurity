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
}
