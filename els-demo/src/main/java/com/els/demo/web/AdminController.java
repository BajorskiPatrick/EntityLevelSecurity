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
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionManager permissionManager;

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
        Permission p = new Permission();
        p.setEntityName(request.getEntityName());
        p.setAction(request.getAction());
        p.setAccessType(request.getAccessType());
        p.setRowIds(request.getRowIds());

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            p.setUser(user);
        } else if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            p.setRole(role);
        } else {
            return ResponseEntity.badRequest().build();
        }

        Permission saved = permissionManager.savePermission(p);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/permissions/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionManager.deletePermission(id);
        return ResponseEntity.ok().build();
    }
}
