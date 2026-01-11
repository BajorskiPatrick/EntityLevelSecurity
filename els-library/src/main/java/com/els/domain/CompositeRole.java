package com.els.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("COMPOSITE")
@Getter
@Setter
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
}
