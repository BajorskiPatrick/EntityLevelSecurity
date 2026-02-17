package com.els.demo.repository;

import com.els.demo.domain.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Query;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findByPatientId(Long patientId);

    @Query("SELECT mr FROM MedicalRecord mr JOIN FETCH mr.patient JOIN FETCH mr.patient.department")
    List<MedicalRecord> findAllWithPatientsWithDepartments();
}
