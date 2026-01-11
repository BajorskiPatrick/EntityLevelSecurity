package com.els.demo.repository;

import com.els.demo.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findByDepartmentId(Long departmentId);
}
