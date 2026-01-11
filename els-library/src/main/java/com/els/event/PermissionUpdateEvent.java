package com.els.event;

import com.els.domain.Action;
import org.springframework.context.ApplicationEvent;

public class PermissionUpdateEvent extends ApplicationEvent {

    private final String username;
    private final String entityName;
    private final Action action;

    public PermissionUpdateEvent(Object source, String username, String entityName, Action action) {
        super(source);
        this.username = username;
        this.entityName = entityName;
        this.action = action;
    }

    public String getUsername() {
        return username;
    }

    public String getEntityName() {
        return entityName;
    }

    public Action getAction() {
        return action;
    }
}
