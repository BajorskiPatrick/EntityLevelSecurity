package com.els.event;

import com.els.cache.PermissionCache;
import com.els.cache.PermissionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);
    private final PermissionCache permissionCache;

    public CacheInvalidator(PermissionCache permissionCache) {
        this.permissionCache = permissionCache;
    }

    @EventListener
    public void handlePermissionUpdate(PermissionUpdateEvent event) {
        log.info("Received Permission Update Event for user: {}, entity: {}", event.getUsername(),
                event.getEntityName());

        if (event.getUsername() != null) {
            PermissionKey key = new PermissionKey(event.getUsername(), event.getEntityName(), event.getAction());
            permissionCache.invalidate(key);
            log.info("Invalidated cache for key: {}", key);
        } else {
            permissionCache.clear();
            log.warn("Role permission changed. perform full cache clear for safety.");
        }
    }
}
