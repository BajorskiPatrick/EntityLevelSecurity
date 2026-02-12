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

        String username = (saved.getUser() != null) ? saved.getUser().getUsername() : null;

        PermissionUpdateEvent event = PermissionUpdateEvent.builder(this)
                .username(username)
                .entityName(saved.getEntityName())
                .action(saved.getAction())
                .build();

        eventPublisher.publishEvent(event);
        return saved;
    }

    @Transactional
    public void deletePermission(Long permissionId) {
        permissionRepository.findById(permissionId).ifPresent(p -> {
            String username = (p.getUser() != null) ? p.getUser().getUsername() : null;
            permissionRepository.delete(p);

            eventPublisher.publishEvent(PermissionUpdateEvent.builder(this)
                    .username(username)
                    .entityName(p.getEntityName())
                    .action(p.getAction())
                    .build());
        });
    }
}
