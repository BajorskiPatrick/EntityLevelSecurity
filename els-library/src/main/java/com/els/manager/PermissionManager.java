package com.els.manager;

import com.els.domain.Permission;
import com.els.event.PermissionUpdateEvent;
import com.els.repository.PermissionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionManager {

    private final PermissionRepository permissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PermissionManager(PermissionRepository permissionRepository, ApplicationEventPublisher eventPublisher) {
        this.permissionRepository = permissionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Permission savePermission(Permission permission) {
        Permission saved = permissionRepository.save(permission);

        // Notify observers
        String username = (saved.getUser() != null) ? saved.getUser().getUsername() : null;

        PermissionUpdateEvent event = new PermissionUpdateEvent(
                this,
                username,
                saved.getEntityName(),
                saved.getAction());

        eventPublisher.publishEvent(event);
        return saved;
    }

    @Transactional
    public void deletePermission(Long permissionId) {
        // Fetch before delete to get info for event
        permissionRepository.findById(permissionId).ifPresent(p -> {
            String username = (p.getUser() != null) ? p.getUser().getUsername() : null;
            permissionRepository.delete(p);

            eventPublisher.publishEvent(new PermissionUpdateEvent(
                    this, username, p.getEntityName(), p.getAction()));
        });
    }
}
