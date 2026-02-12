package com.els.demo.dto;

import com.els.domain.AccessType;
import com.els.domain.Action;

public class PermissionRequest {
    private String username; // optional (if for user)
    private Long roleId; // optional (if for role)
    private String entityName;
    private Action action;
    private AccessType accessType;
    private String rowIds;

    public PermissionRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(AccessType accessType) {
        this.accessType = accessType;
    }

    public String getRowIds() {
        return rowIds;
    }

    public void setRowIds(String rowIds) {
        this.rowIds = rowIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PermissionRequest request;

        public Builder() {
            this.request = new PermissionRequest();
        }

        public Builder username(String username) {
            this.request.setUsername(username);
            return this;
        }

        public Builder roleId(Long roleId) {
            this.request.setRoleId(roleId);
            return this;
        }

        public Builder entityName(String entityName) {
            this.request.setEntityName(entityName);
            return this;
        }

        public Builder action(Action action) {
            this.request.setAction(action);
            return this;
        }

        public Builder accessType(AccessType accessType) {
            this.request.setAccessType(accessType);
            return this;
        }

        public Builder rowIds(String rowIds) {
            this.request.setRowIds(rowIds);
            return this;
        }

        public PermissionRequest build() {
            return this.request;
        }
    }
}
