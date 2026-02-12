package com.els.demo.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "departments")
@Filter(name = "elsFilter", condition = "id IN (:ids)")
@Filter(name = "elsBlacklistFilter", condition = "id NOT IN (:ids)")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Department department;

        public Builder() {
            this.department = new Department();
        }

        public Builder id(Long id) {
            this.department.setId(id);
            return this;
        }

        public Builder name(String name) {
            this.department.setName(name);
            return this;
        }

        public Department build() {
            return this.department;
        }
    }
}
