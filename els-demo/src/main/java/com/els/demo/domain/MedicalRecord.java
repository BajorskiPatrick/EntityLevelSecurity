package com.els.demo.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "medical_records")
@Filter(name = "elsFilter", condition = "id IN (:ids)")
@Filter(name = "elsBlacklistFilter", condition = "id NOT IN (:ids)")
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(nullable = false)
    private String diagnosis;

    @Column(length = 1000)
    private String treatment;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }
}
