package com.els.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "els_permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String entityName;

    @Column(columnDefinition = "bigint")
    private Long rowId;

    public boolean isUserPermission() {
        return user != null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
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
        private final Permission permission;

        public Builder() {
            this.permission = new Permission();
        }

        public Builder user(User user) {
            this.permission.setUser(user);
            return this;
        }

        public Builder role(Role role) {
            this.permission.setRole(role);
            return this;
        }

        public Builder entity(String entityName) {
            this.permission.setEntityName(entityName);
            return this;
        }

        public Builder rowId(Long rowId) {
            this.permission.setRowId(rowId);
            return this;
        }

        public Permission build() {
            return this.permission;
        }
    }
}
