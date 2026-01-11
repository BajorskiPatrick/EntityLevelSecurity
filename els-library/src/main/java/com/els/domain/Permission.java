package com.els.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "els_permissions")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Direct User assignment
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Role assignment
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String entityName; // e.g., "Product" or table name

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action; // SELECT, UPDATE...

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType; // WHITELIST, BLACKLIST

    @Column(columnDefinition = "TEXT")
    private String rowIds; // JSON or CSV string of IDs: "1,2,3"

    // Helper to check if it applies to user or role
    public boolean isUserPermission() {
        return user != null;
    }
}
