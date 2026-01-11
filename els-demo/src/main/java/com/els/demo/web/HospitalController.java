package com.els.demo.web;

import com.els.demo.domain.Department;
import com.els.demo.domain.MedicalRecord;
import com.els.demo.domain.Patient;
import com.els.demo.service.HospitalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hospital")
public class HospitalController {

    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = hospitalService;
    }

    @GetMapping("/departments")
    public List<Department> getAllDepartments() {
        return hospitalService.getAllDepartments();
    }

    @GetMapping("/patients")
    public List<Patient> getPatients(@RequestParam(required = false) Long deptId) {
        if (deptId != null) {
            return hospitalService.getPatientsByDepartment(deptId);
        }
        return hospitalService.getAllPatients();
    }

    @GetMapping("/records")
    public List<MedicalRecord> getRecords(@RequestParam(required = false) Long patientId) {
        if (patientId != null) {
            return hospitalService.getMedicalRecordsByPatient(patientId);
        }
        return hospitalService.getAllMedicalRecords();
    }
}
