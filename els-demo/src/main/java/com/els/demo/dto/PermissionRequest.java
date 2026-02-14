package com.els.demo.dto;

import com.els.domain.Action;

public class PermissionRequest {
    private String username;
    private Long roleId;
    private String entityName;
    private Action action;
    private Long rowId;

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

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
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

        public Builder rowId(Long rowId) {
            this.request.setRowId(rowId);
            return this;
        }

        public PermissionRequest build() {
            return this.request;
        }
    }
}
