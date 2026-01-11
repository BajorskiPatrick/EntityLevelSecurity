package com.els.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("SIMPLE")
public class SimpleRole extends Role {
    // Leaf node: No children, just represents a basic role.
}
