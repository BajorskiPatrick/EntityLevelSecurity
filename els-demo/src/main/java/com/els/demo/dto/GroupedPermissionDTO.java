package com.els.demo.dto;

import java.util.List;

public class GroupedPermissionDTO {
    private String who;
    private String entityName;
    private String action;
    private List<Long> ids;
    private List<Long> permissionIds;

    public GroupedPermissionDTO() {
    }

    public GroupedPermissionDTO(String who, String entityName, String action,
            List<Long> ids, List<Long> permissionIds) {
        this.who = who;
        this.entityName = entityName;
        this.action = action;
        this.ids = ids;
        this.permissionIds = permissionIds;
    }

    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public List<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final GroupedPermissionDTO dto;

        public Builder() {
            this.dto = new GroupedPermissionDTO();
        }

        public Builder who(String who) {
            this.dto.setWho(who);
            return this;
        }

        public Builder entityName(String entityName) {
            this.dto.setEntityName(entityName);
            return this;
        }

        public Builder action(String action) {
            this.dto.setAction(action);
            return this;
        }

        public Builder ids(List<Long> ids) {
            this.dto.setIds(ids);
            return this;
        }

        public Builder permissionIds(List<Long> permissionIds) {
            this.dto.setPermissionIds(permissionIds);
            return this;
        }

        public GroupedPermissionDTO build() {
            return this.dto;
        }
    }
}
