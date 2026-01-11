package com.els.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "els_permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Direct User assignment
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Role assignment
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String entityName; // e.g., "Product" or table name

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action; // SELECT, UPDATE...

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType; // WHITELIST, BLACKLIST

    @Column(columnDefinition = "TEXT")
    private String rowIds; // JSON or CSV string of IDs: "1,2,3"

    // Helper to check if it applies to user or role
    public boolean isUserPermission() {
        return user != null;
    }

    // Manual Getters and Setters
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

    // --- Builder Pattern ---
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

        public Builder action(Action action) {
            this.permission.setAction(action);
            return this;
        }

        public Builder accessType(AccessType accessType) {
            this.permission.setAccessType(accessType);
            return this;
        }

        public Builder rowIds(String rowIds) {
            this.permission.setRowIds(rowIds);
            return this;
        }

        public Permission build() {
            return this.permission;
        }
    }
}
