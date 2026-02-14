package com.els.demo.service;

import com.els.annotation.Secure;
import com.els.demo.domain.Department;
import com.els.demo.domain.MedicalRecord;
import com.els.demo.domain.Patient;
import com.els.demo.repository.DepartmentRepository;
import com.els.demo.repository.MedicalRecordRepository;
import com.els.demo.repository.PatientRepository;
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

    @Secure
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Secure
    @Transactional
    public Department addDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Secure
    @Transactional
    public Department updateDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Secure
    @Transactional
    public void deleteDepartment(Long departmentId) {
        departmentRepository.deleteById(departmentId);
    }

    @Secure
    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Secure
    @Transactional(readOnly = true)
    public List<Patient> getPatientsByDepartment(Long deptId) {
        return patientRepository.findByDepartmentId(deptId);
    }

    @Secure
    @Transactional(readOnly = true)
    public List<MedicalRecord> getAllMedicalRecords() {
        return medicalRecordRepository.findAll();
    }

    @Secure
    @Transactional(readOnly = true)
    public List<MedicalRecord> getMedicalRecordsByPatient(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId);
    }

    @Secure
    @Transactional
    public Patient addPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    @Secure
    @Transactional
    public MedicalRecord addMedicalRecord(MedicalRecord record) {
        return medicalRecordRepository.save(record);
    }

    @Secure
    @Transactional
    public Patient updatePatient(Patient patient) {
        return patientRepository.save(patient);
    }

    @Secure
    @Transactional
    public MedicalRecord updateMedicalRecord(MedicalRecord record) {
        return medicalRecordRepository.save(record);
    }

    @Secure
    @Transactional
    public void dischargePatient(Long patientId) {
        patientRepository.deleteById(patientId);
    }

    @Secure
    @Transactional
    public void deleteMedicalRecord(Long recordId) {
        medicalRecordRepository.deleteById(recordId);
    }
}
