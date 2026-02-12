package com.els.domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("COMPOSITE")
public class CompositeRole extends Role {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_hierarchy", joinColumns = @JoinColumn(name = "parent_role_id"), inverseJoinColumns = @JoinColumn(name = "child_role_id"))
    private Set<Role> children = new HashSet<>();

    public void addChild(Role role) {
        this.children.add(role);
    }

    public void removeChild(Role role) {
        this.children.remove(role);
    }

    public Set<Role> getChildren() {
        return children;
    }

    public void setChildren(Set<Role> children) {
        this.children = children;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CompositeRole role;

        public Builder() {
            this.role = new CompositeRole();
        }

        public Builder id(Long id) {
            this.role.setId(id);
            return this;
        }

        public Builder name(String name) {
            this.role.setName(name);
            return this;
        }

        public Builder children(Set<Role> children) {
            this.role.setChildren(children);
            return this;
        }

        public Builder child(Role child) {
            this.role.addChild(child);
            return this;
        }

        public CompositeRole build() {
            return this.role;
        }
    }
}
