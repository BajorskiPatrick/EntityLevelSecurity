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

        // Composite Role: HEAD_DOCTOR (Contains Doctor + Nurse permissions)
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

        createRecord("Flu", "Prescribed rest", p1);
        createRecord("Heart Attack", "Surgery scheduled", p1);
        createRecord("Migraine", "Painkillers", p2);
        createRecord("Lupus", "It's never lupus", p3);
        createRecord("Trauma", "CPR initiated", p4);

        // --- 5. Permissions ---
        // ADMIN: full CRUD on all entities (wildcard)
        createPermission(null, adminRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Department", Action.INSERT, AccessType.WHITELIST, null);
        createPermission(null, adminRole, "Department", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Department", Action.DELETE, AccessType.WHITELIST, "*");

        createPermission(null, adminRole, "Patient", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Patient", Action.INSERT, AccessType.WHITELIST, null);
        createPermission(null, adminRole, "Patient", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Patient", Action.DELETE, AccessType.WHITELIST, "*");

        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "MedicalRecord", Action.INSERT, AccessType.WHITELIST, null);
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, AccessType.WHITELIST, "*");

        // DOCTOR & NURSE: can view all departments (read-only)
        createPermission(null, doctorRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, nurseRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");

        // DOCTOR: can SELECT specific patients (p1, p2), INSERT patients, full CRUD on
        // records
        createPermission(null, doctorRole, "Patient", Action.SELECT, AccessType.WHITELIST,
                p1.getId() + "," + p2.getId());
        createPermission(null, doctorRole, "Patient", Action.INSERT, AccessType.WHITELIST, null);

        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, doctorRole, "MedicalRecord", Action.INSERT, AccessType.WHITELIST, null);
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, doctorRole, "MedicalRecord", Action.DELETE, AccessType.WHITELIST, "*");

        // NURSE: can SELECT specific patients (p4, p5), INSERT patients, SELECT &
        // INSERT & UPDATE records (no DELETE)
        createPermission(null, nurseRole, "Patient", Action.SELECT, AccessType.WHITELIST,
                p4.getId() + "," + p5.getId());
        createPermission(null, nurseRole, "Patient", Action.INSERT, AccessType.WHITELIST, null);

        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, nurseRole, "MedicalRecord", Action.INSERT, AccessType.WHITELIST, null);
        createPermission(null, nurseRole, "MedicalRecord", Action.UPDATE, AccessType.WHITELIST, "*");

        // dr_house (user-specific): additional patient p3 visible
        // (as HEAD_DOCTOR he inherits DOCTOR+NURSE, seeing p1,p2,p4,p5; this adds p3)
        createPermission(drHouse, null, "Patient", Action.SELECT, AccessType.WHITELIST, String.valueOf(p3.getId()));

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

    private void createPermission(User user, Role role, String entity, Action action, AccessType type, String ids) {
        Permission p = Permission.builder()
                .user(user)
                .role(role)
                .entity(entity)
                .action(action)
                .accessType(type)
                .rowIds(ids)
                .build();
        permissionManager.savePermission(p);
    }
}
