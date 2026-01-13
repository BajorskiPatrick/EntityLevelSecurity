package com.els.demo.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "patients")
@Filter(name = "elsFilter", condition = "id IN (:ids)")
@Filter(name = "elsBlacklistFilter", condition = "id NOT IN (:ids)")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String ssn; // In real app, highly sensitive

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

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

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}
