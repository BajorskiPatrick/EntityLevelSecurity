package com.els.demo.loader;

import com.els.demo.domain.Product;
import com.els.demo.service.ProductService;
import com.els.domain.*;
import com.els.manager.PermissionManager;
import com.els.repository.RoleRepository;
import com.els.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionManager permissionManager;
    private final ProductService productService;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 1. Create Data (Products)
        // IDs will likely be 1, 2, 3, 4
        createProduct("Laptop", "Electronics", 1200.0); // ID 1
        createProduct("Mouse", "Electronics", 20.0); // ID 2
        createProduct("Chair", "Furniture", 150.0); // ID 3
        createProduct("Desk", "Furniture", 300.0); // ID 4

        // 2. Create Roles (Composite Pattern)
        // Employee (Leaf)
        SimpleRole employeeRole = new SimpleRole();
        employeeRole.setName("Employee");
        roleRepository.save(employeeRole);

        // Manager (Composite -> includes Employee)
        CompositeRole managerRole = new CompositeRole();
        managerRole.setName("Manager");
        managerRole.addChild(employeeRole);
        roleRepository.save(managerRole);

        // 3. Create Users
        User alice = createUser("alice", "pass", managerRole); // Manager
        User bob = createUser("bob", "pass", employeeRole); // Employee
        User charlie = createUser("charlie", "pass"); // No Role

        // 4. Create Permissions
        // Manager: Whitelist IDs 1, 2, 3, 4 (All)
        createPermission(null, managerRole, "Product", Action.SELECT, AccessType.WHITELIST, "1,2,3,4");

        // Employee: Whitelist IDs 2, 3 (Mouse, Chair)
        // But let's verify Composite: If Manager inherits Employee, and Employee has
        // Permission X, Manager gets it too.
        // Let's set Employee to access ID 3 (Chair).
        // Let's set Manager to access ID 1 (Laptop).
        // Manager should see 1 (direct) + 3 (inherited).

        // REVISE:
        // Clear previous perms.
        // Employee -> Whitelist "3, 4" (Chair, Desk)
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
