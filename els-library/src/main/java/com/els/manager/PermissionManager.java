package com.els.manager;

import com.els.domain.Permission;
import com.els.repository.PermissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionManager {

    private final PermissionRepository permissionRepository;

    public PermissionManager(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public Permission savePermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(Long permissionId) {
        permissionRepository.findById(permissionId).ifPresent(p -> {
            permissionRepository.delete(p);
        });
    }
}
