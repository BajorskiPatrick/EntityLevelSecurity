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
    private String name; // e.g., "Cardiology", "Neurology"

    // Getters and Setters
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
}
