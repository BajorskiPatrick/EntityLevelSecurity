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
    private final ProductRepository productRepository;

    public DataLoader(UserRepository userRepository, RoleRepository roleRepository, PermissionManager permissionManager,
            DepartmentRepository departmentRepository, PatientRepository patientRepository,
            MedicalRecordRepository medicalRecordRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionManager = permissionManager;
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        createPermission(null, employeeRole, "Product", Action.SELECT, AccessType.WHITELIST, "3, 4");

        // Manager -> Whitelist "1" (Laptop)
        createPermission(null, managerRole, "Product", Action.SELECT, AccessType.WHITELIST, "1");

        // Result:
        // Bob (Employee) should see: 3, 4.
        // Alice (Manager) should see: 1 + 3, 4 = 1, 3, 4.
    }

    private Product createProduct(String name, String cat, Double price) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(cat);
        p.setPrice(price);
        return productService.save(p);
    }

    private User createUser(String name, String pass, Role... roles) {
        User u = new User();
        u.setUsername(name);
        u.setPassword(pass);
        for (Role r : roles)
            u.getRoles().add(r);
        return userRepository.save(u);
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
