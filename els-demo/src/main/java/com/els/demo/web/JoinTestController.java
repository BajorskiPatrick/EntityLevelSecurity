package com.els.demo.web;

import com.els.demo.domain.MedicalRecord;
import com.els.demo.domain.Patient;
import com.els.demo.repository.MedicalRecordRepository;
import com.els.demo.repository.PatientRepository;

import com.els.annotation.Secure;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JoinTestController {

    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    @Secure
    @GetMapping("/patients-join-test")
    public List<Patient> getJoinedRecords() {
        return patientRepository.findPatientsWithDepartment();
    }

    @Secure
    @GetMapping("/records-join-test")
    public List<MedicalRecord> getJoinedRecordsWithPatient() {
        return medicalRecordRepository.findAllWithPatientsWithDepartments();
    }
}
