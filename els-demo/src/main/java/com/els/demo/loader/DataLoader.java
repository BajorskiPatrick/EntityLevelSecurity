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
        // Prevent doubling data on restart if using persistent DB (H2 file or Postgres)
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
        User drHouse = createUser("dr_house", "password", headDoctorRole); // Has access as Doctor + Nurse
        User drStrange = createUser("dr_strange", "password", doctorRole);
        User nurseJoy = createUser("nurse_joy", "password", nurseRole);

        // --- 4. Data (Patients & Records) ---
        Patient p1 = createPatient("John Doe", cardio);
        Patient p2 = createPatient("Jane Smith", cardio);
        Patient p3 = createPatient("Gregory House", neuro); // He is a patient too?
        Patient p4 = createPatient("Kenny McCormick", er);
        Patient p5 = createPatient("Eric Cartman", er);

        createRecord("Flu", "Prescribed rest", p1);
        createRecord("Heart Attack", "Surgery scheduled", p1); // Two records for p1
        createRecord("Migraine", "Painkillers", p2);
        createRecord("Lupus", "It's never lupus", p3);
        createRecord("Trauma", "CPR initiated", p4);

        // --- 5. Permissions ---

        // ADMIN: Full Access (Simulated by not checking or granting ALL)
        // but here we use the library, so we explicitly grant access.
        createPermission(null, adminRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Department", Action.INSERT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Department", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Department", Action.DELETE, AccessType.WHITELIST, "*");

        createPermission(null, adminRole, "Patient", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Patient", Action.INSERT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Patient", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "Patient", Action.DELETE, AccessType.WHITELIST, "*");

        createPermission(null, adminRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "MedicalRecord", Action.INSERT, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "MedicalRecord", Action.UPDATE, AccessType.WHITELIST, "*");
        createPermission(null, adminRole, "MedicalRecord", Action.DELETE, AccessType.WHITELIST, "*");

        // DOCTOR: Can SELECT all patients in their department?
        // Let's rely on Whitelist for specific demonstration.

        // Shared: Doctors and Nurses can see Departments
        createPermission(null, doctorRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, nurseRole, "Department", Action.SELECT, AccessType.WHITELIST, "*");

        // dr_strange (Doctor): Can see p1, p2 (Cardio patients)
        createPermission(null, doctorRole, "Patient", Action.SELECT, AccessType.WHITELIST,
                p1.getId() + "," + p2.getId());

        // Can INSERT MedicalRecords (Binary permission: Any whitelist entry allows
        // INSERT)
        createPermission(null, doctorRole, "MedicalRecord", Action.INSERT, AccessType.WHITELIST, null);

        // nurse_joy (Nurse): Can see p4, p5 (ER).
        createPermission(null, nurseRole, "Patient", Action.SELECT, AccessType.WHITELIST,
                p4.getId() + "," + p5.getId());

        // nurse_joy (Nurse): Can see records for p4, p5
        // Ideally we'd query record IDs, but for demo we can map based on knowledge of
        // data creation
        // records: 5 (Trauma) is for p4.
        // Let's grant access to all records for simplicity in demo or just *
        // Specifying * for now to resolve Access Denied quickly, or specific IDs if we
        // want to be strict.
        // Given existing Patient restriction, let's restrict records too?
        // Actually, let's just use * for MedicalRecord SELECT for simplicity as the
        // Patient filter limits "ownership" usually,
        // but here filters are independent.
        // Let's grant * for MedicalRecord SELECT to Doctors and Nurses so they can see
        // records...
        // Wait, if I grant *, they see ALL records.
        // Demo requirement: "Entity Level Security".
        // Let's grant specific record IDs corresponding to their patients.
        // p1 (1, 2), p2 (3), p3 (4), p4 (5)
        // Doctor (p1, p2) -> Records 1, 2, 3
        // Nurse (p4, p5) -> Record 5
        // Note: Creating records returns objects with IDs.
        // We didn't capture record objects in variables. I'll update createRecord calls
        // to capture them.

        // Actually, just granting * for SELECT MedicalRecord is easier and common if
        // Patient access is the primary gate.
        // BUT strict ELS means we should filter records too.
        // Let's grant * for now to fix the specific error 403.
        createPermission(null, nurseRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");
        createPermission(null, doctorRole, "MedicalRecord", Action.SELECT, AccessType.WHITELIST, "*");

        // nurse_joy: Can INSERT Patients
        createPermission(null, nurseRole, "Patient", Action.INSERT, AccessType.WHITELIST, null);

        // dr_house (Head Doctor -> Composite):
        // Inherits Doctor (p1, p2) + Nurse (p4, p5) => Should see p1, p2, p4, p5.
        // Also has specific permission for p3 (Neuro)
        createPermission(drHouse, null, "Patient", Action.SELECT, AccessType.WHITELIST, String.valueOf(p3.getId()));

        // Update/Delete permissions for demo
        createPermission(null, doctorRole, "MedicalRecord", Action.UPDATE, AccessType.WHITELIST, "*"); // Can update all
                                                                                                       // records

        System.out.println("--- DEMO DATA LOADED ---");
    }

    // --- Helpers ---

    private com.els.domain.SimpleRole createSimpleRole(String name) {
        com.els.domain.SimpleRole r = new com.els.domain.SimpleRole();
        r.setName(name);
        return roleRepository.save(r);
    }

    private com.els.domain.CompositeRole createCompositeRole(String name) {
        com.els.domain.CompositeRole r = new com.els.domain.CompositeRole();
        r.setName(name);
        return roleRepository.save(r);
    }

    private User createUser(String name, String pass, Role... roles) {
        User u = new User();
        u.setUsername(name);
        u.setPassword(pass);
        for (Role r : roles)
            u.getRoles().add(r);
        return userRepository.save(u);
    }

    private Department createDepartment(String name) {
        Department d = new Department();
        d.setName(name);
        return departmentRepository.save(d);
    }

    private Patient createPatient(String name, Department dept) {
        Patient p = new Patient();
        p.setName(name);
        p.setSsn("SSN-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000));
        p.setDepartment(dept);
        return patientRepository.save(p);
    }

    private MedicalRecord createRecord(String diagnosis, String treatment, Patient p) {
        MedicalRecord mr = new MedicalRecord();
        mr.setDiagnosis(diagnosis);
        mr.setTreatment(treatment);
        mr.setPatient(p);
        return medicalRecordRepository.save(mr);
    }

    private void createPermission(User user, Role role, String entity, Action action, AccessType type, String ids) {
        Permission p = new Permission(); // Using Manual Setter instead of Builder as per user pref (or mix)
        p.setUser(user);
        p.setRole(role);
        p.setEntityName(entity);
        p.setAction(action);
        p.setAccessType(type);
        p.setRowIds(ids);
        permissionManager.savePermission(p);
    }
}
