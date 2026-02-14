package com.els.config;

import com.els.domain.AccessType;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global configuration properties for Entity Level Security.
 * The access type (WHITELIST or BLACKLIST) is configured globally
 * for the entire application rather than per-permission.
 *
 * Usage in application.properties:
 * els.access-type=WHITELIST
 * els.access-type=BLACKLIST
 */
@ConfigurationProperties(prefix = "els")
public class ElsProperties {

    private AccessType accessType = AccessType.WHITELIST;

    public AccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(AccessType accessType) {
        this.accessType = accessType;
    }
}
