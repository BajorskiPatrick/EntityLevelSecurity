package com.els.demo.loader;

import com.els.demo.domain.*;
import com.els.demo.repository.*;
import com.els.domain.*;
import com.els.manager.PermissionManager;
import com.els.repository.RoleRepository;
import com.els.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionManager permissionManager;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public DataLoader(UserRepository userRepository, RoleRepository roleRepository, PermissionManager permissionManager,
            DepartmentRepository departmentRepository, PatientRepository patientRepository,
            MedicalRecordRepository medicalRecordRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionManager = permissionManager;
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }

        // --- 1. Roles ---
        com.els.domain.SimpleRole adminRole = createSimpleRole("ADMIN");
        com.els.domain.SimpleRole doctorRole = createSimpleRole("DOCTOR");
        com.els.domain.SimpleRole nurseRole = createSimpleRole("NURSE");

        com.els.domain.CompositeRole headDoctorRole = createCompositeRole("HEAD_DOCTOR");
        headDoctorRole.addChild(doctorRole);
        headDoctorRole.addChild(nurseRole);
        roleRepository.save(headDoctorRole);

        // --- 2. Departments ---
        Department cardio = createDepartment("Cardiology");
        Department neuro = createDepartment("Neurology");
        Department er = createDepartment("ER");

        // --- 3. Users ---
        User admin = createUser("admin", "admin", adminRole);
        User drHouse = createUser("dr_house", "password", headDoctorRole);
        User drStrange = createUser("dr_strange", "password", doctorRole);
        User nurseJoy = createUser("nurse_joy", "password", nurseRole);

        // --- 4. Data (Patients & Records) ---
        Patient p1 = createPatient("John Doe", cardio);
        Patient p2 = createPatient("Jane Smith", cardio);
        Patient p3 = createPatient("Gregory House", neuro);
        Patient p4 = createPatient("Kenny McCormick", er);
        Patient p5 = createPatient("Eric Cartman", er);

        MedicalRecord r1 = createRecord("Flu", "Prescribed rest", p1);
        MedicalRecord r2 = createRecord("Heart Attack", "Surgery scheduled", p1);
        MedicalRecord r3 = createRecord("Migraine", "Painkillers", p2);
        MedicalRecord r4 = createRecord("Lupus", "It's never lupus", p3);
        MedicalRecord r5 = createRecord("Trauma", "CPR initiated", p4);

        // --- 5. Permissions (one record per row ID, no wildcards) ---

        // ADMIN: full access to all departments
        createPermission(null, adminRole, "Department", Action.SELECT, cardio.getId());
        createPermission(null, adminRole, "Department", Action.SELECT, neuro.getId());
        createPermission(null, adminRole, "Department", Action.SELECT, er.getId());
        createPermission(null, adminRole, "Department", Action.INSERT, null);
        createPermission(null, adminRole, "Department", Action.UPDATE, cardio.getId());
        createPermission(null, adminRole, "Department", Action.UPDATE, neuro.getId());
        createPermission(null, adminRole, "Department", Action.UPDATE, er.getId());
        createPermission(null, adminRole, "Department", Action.DELETE, cardio.getId());
        createPermission(null, adminRole, "Department", Action.DELETE, neuro.getId());
        createPermission(null, adminRole, "Department", Action.DELETE, er.getId());

        // ADMIN: full access to all patients
        createPermission(null, adminRole, "Patient", Action.SELECT, p1.getId());
        createPermission(null, adminRole, "Patient", Action.SELECT, p2.getId());
        createPermission(null, adminRole, "Patient", Action.SELECT, p3.getId());
        createPermission(null, adminRole, "Patient", Action.SELECT, p4.getId());
        createPermission(null, adminRole, "Patient", Action.SELECT, p5.getId());
        createPermission(null, adminRole, "Patient", Action.INSERT, null);
        createPermission(null, adminRole, "Patient", Action.UPDATE, p1.getId());
        createPermission(null, adminRole, "Patient", Action.UPDATE, p2.getId());
        createPermission(null, adminRole, "Patient", Action.UPDATE, p3.getId());
        createPermission(null, adminRole, "Patient", Action.UPDATE, p4.getId());
        createPermission(null, adminRole, "Patient", Action.UPDATE, p5.getId());
        createPermission(null, adminRole, "Patient", Action.DELETE, p1.getId());
        createPermission(null, adminRole, "Patient", Action.DELETE, p2.getId());
        createPermission(null, adminRole, "Patient", Action.DELETE, p3.getId());
        createPermission(null, adminRole, "Patient", Action.DELETE, p4.getId());
        createPermission(null, adminRole, "Patient", Action.DELETE, p5.getId());

        // ADMIN: full access to all medical records
        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, r1.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, r2.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, r3.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, r4.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, r5.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.INSERT, null);
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, r1.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, r2.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, r3.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, r4.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, r5.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, r1.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, r2.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, r3.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, r4.getId());
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, r5.getId());

        // DOCTOR: view all departments
        createPermission(null, doctorRole, "Department", Action.SELECT, cardio.getId());
        createPermission(null, doctorRole, "Department", Action.SELECT, neuro.getId());
        createPermission(null, doctorRole, "Department", Action.SELECT, er.getId());

        // NURSE: view all departments
        createPermission(null, nurseRole, "Department", Action.SELECT, cardio.getId());
        createPermission(null, nurseRole, "Department", Action.SELECT, neuro.getId());
        createPermission(null, nurseRole, "Department", Action.SELECT, er.getId());

        // DOCTOR: specific patients (p1, p2) + can INSERT
        createPermission(null, doctorRole, "Patient", Action.SELECT, p1.getId());
        createPermission(null, doctorRole, "Patient", Action.SELECT, p2.getId());
        createPermission(null, doctorRole, "Patient", Action.INSERT, null);

        // DOCTOR: full access to all medical records
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, r1.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, r2.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, r3.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, r4.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, r5.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.INSERT, null);
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, r1.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, r2.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, r3.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, r4.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, r5.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, r1.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, r2.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, r3.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, r4.getId());
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, r5.getId());

        // NURSE: specific patients (p4, p5) + can INSERT
        createPermission(null, nurseRole, "Patient", Action.SELECT, p4.getId());
        createPermission(null, nurseRole, "Patient", Action.SELECT, p5.getId());
        createPermission(null, nurseRole, "Patient", Action.INSERT, null);

        // NURSE: access to medical records + INSERT + UPDATE (no DELETE)
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, r1.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, r2.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, r3.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, r4.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, r5.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.INSERT, null);
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, r1.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, r2.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, r3.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, r4.getId());
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, r5.getId());

        // dr_house (user-level): additional patient p3
        createPermission(drHouse, null, "Patient", Action.SELECT, p3.getId());

        System.out.println("--- DEMO DATA LOADED ---");
    }

    private com.els.domain.SimpleRole createSimpleRole(String name) {
        com.els.domain.SimpleRole r = com.els.domain.SimpleRole.builder()
                .name(name)
                .build();
        return roleRepository.save(r);
    }

    private com.els.domain.CompositeRole createCompositeRole(String name) {
        com.els.domain.CompositeRole r = com.els.domain.CompositeRole.builder()
                .name(name)
                .build();
        return roleRepository.save(r);
    }

    private User createUser(String name, String pass, Role... roles) {
        User.Builder builder = User.builder()
                .username(name)
                .password(pass);
        for (Role r : roles)
            builder.role(r);
        return userRepository.save(builder.build());
    }

    private Department createDepartment(String name) {
        Department d = Department.builder()
                .name(name)
                .build();
        return departmentRepository.save(d);
    }

    private Patient createPatient(String name, Department dept) {
        Patient p = Patient.builder()
                .name(name)
                .ssn("SSN-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000))
                .department(dept)
                .build();
        return patientRepository.save(p);
    }

    private MedicalRecord createRecord(String diagnosis, String treatment, Patient p) {
        MedicalRecord mr = MedicalRecord.builder()
                .diagnosis(diagnosis)
                .treatment(treatment)
                .patient(p)
                .build();
        return medicalRecordRepository.save(mr);
    }

    private void createPermission(User user, Role role, String entity, Action action, Long rowId) {
        Permission p = Permission.builder()
                .user(user)
                .role(role)
                .entity(entity)
                .action(action)
                .rowId(rowId)
                .build();
        permissionManager.savePermission(p);
    }
}
