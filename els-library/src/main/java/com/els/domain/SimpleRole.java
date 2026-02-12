package com.els.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("SIMPLE")
public class SimpleRole extends Role {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SimpleRole role;

        public Builder() {
            this.role = new SimpleRole();
        }

        public Builder id(Long id) {
            this.role.setId(id);
            return this;
        }

        public Builder name(String name) {
            this.role.setName(name);
            return this;
        }

        public SimpleRole build() {
            return this.role;
        }
    }
}
