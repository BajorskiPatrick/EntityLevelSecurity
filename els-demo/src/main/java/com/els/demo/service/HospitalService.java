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

    @Secure(entity = Department.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Secure(entity = Department.class, action = Action.INSERT)
    @Transactional
    public Department addDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Secure(entity = Department.class, action = Action.UPDATE)
    @Transactional
    public Department updateDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Secure(entity = Department.class, action = Action.DELETE)
    @Transactional
    public void deleteDepartment(Long departmentId) {
        departmentRepository.deleteById(departmentId);
    }

    @Secure(entity = Patient.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Secure(entity = Patient.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Patient> getPatientsByDepartment(Long deptId) {
        return patientRepository.findByDepartmentId(deptId);
    }

    @Secure(entity = MedicalRecord.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<MedicalRecord> getAllMedicalRecords() {
        return medicalRecordRepository.findAll();
    }

    @Secure(entity = MedicalRecord.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<MedicalRecord> getMedicalRecordsByPatient(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId);
    }

    @Secure(entity = Patient.class, action = Action.INSERT)
    @Transactional
    public Patient addPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    @Secure(entity = MedicalRecord.class, action = Action.INSERT)
    @Transactional
    public MedicalRecord addMedicalRecord(MedicalRecord record) {
        return medicalRecordRepository.save(record);
    }

    @Secure(entity = Patient.class, action = Action.UPDATE)
    @Transactional
    public Patient updatePatient(Patient patient) {
        return patientRepository.save(patient);
    }

    @Secure(entity = MedicalRecord.class, action = Action.UPDATE)
    @Transactional
    public MedicalRecord updateMedicalRecord(MedicalRecord record) {
        return medicalRecordRepository.save(record);
    }

    @Secure(entity = Patient.class, action = Action.DELETE)
    @Transactional
    public void dischargePatient(Long patientId) {
        patientRepository.deleteById(patientId);
    }

    @Secure(entity = MedicalRecord.class, action = Action.DELETE)
    @Transactional
    public void deleteMedicalRecord(Long recordId) {
        medicalRecordRepository.deleteById(recordId);
    }
}
