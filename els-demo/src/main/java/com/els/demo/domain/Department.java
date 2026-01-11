package com.els.demo.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "departments")
@FilterDef(name = "elsFilter", parameters = @ParamDef(name = "idList", type = Long.class))
@Filter(name = "elsFilter", condition = "id IN (:idList)")
@FilterDef(name = "elsBlacklistFilter", parameters = @ParamDef(name = "idList", type = Long.class))
@Filter(name = "elsBlacklistFilter", condition = "id NOT IN (:idList)")
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
