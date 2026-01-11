package com.els.demo.service;

import com.els.annotation.Secure;
import com.els.demo.domain.Department;
import com.els.demo.domain.MedicalRecord;
import com.els.demo.domain.Patient;
import com.els.demo.repository.DepartmentRepository;
import com.els.demo.repository.MedicalRecordRepository;
import com.els.demo.repository.PatientRepository;
import com.els.domain.Action;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HospitalService {

    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public HospitalService(DepartmentRepository departmentRepository, PatientRepository patientRepository,
            MedicalRecordRepository medicalRecordRepository) {
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    @Secure(entity = "Department", action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Secure(entity = "Patient", action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Secure(entity = "Patient", action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Patient> getPatientsByDepartment(Long deptId) {
        return patientRepository.findByDepartmentId(deptId);
    }

    @Secure(entity = "MedicalRecord", action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<MedicalRecord> getAllMedicalRecords() {
        return medicalRecordRepository.findAll();
    }

    @Secure(entity = "MedicalRecord", action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<MedicalRecord> getMedicalRecordsByPatient(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId);
    }
}
