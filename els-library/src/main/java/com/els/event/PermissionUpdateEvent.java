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

    public static Builder builder(Object source) {
        return new Builder(source);
    }

    public static class Builder {
        private final Object source;
        private String username;
        private String entityName;
        private Action action;

        public Builder(Object source) {
            this.source = source;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public PermissionUpdateEvent build() {
            return new PermissionUpdateEvent(source, username, entityName, action);
        }
    }
}
