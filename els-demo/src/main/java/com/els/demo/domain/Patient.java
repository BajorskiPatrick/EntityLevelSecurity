package com.els.demo.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

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
    private String ssn;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<MedicalRecord> medicalRecords = new ArrayList<>();

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Patient patient;

        public Builder() {
            this.patient = new Patient();
        }

        public Builder id(Long id) {
            this.patient.setId(id);
            return this;
        }

        public Builder name(String name) {
            this.patient.setName(name);
            return this;
        }

        public Builder ssn(String ssn) {
            this.patient.setSsn(ssn);
            return this;
        }

        public Builder department(Department department) {
            this.patient.setDepartment(department);
            return this;
        }

        public Patient build() {
            return this.patient;
        }
    }
}
