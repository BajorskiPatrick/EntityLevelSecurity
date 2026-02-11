package com.els.repository;

import com.els.domain.Permission;
import com.els.domain.Role;
import com.els.domain.User;
import com.els.domain.Action;
import com.els.domain.AccessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    // Find direct user permissions
    List<Permission> findByUserAndEntityNameAndAction(User user, String entityName, Action action);

    // Find role permissions (flat list of roles)
    @Query("SELECT p FROM Permission p WHERE p.role IN :roles AND p.entityName = :entityName AND p.action = :action")
    List<Permission> findByRoles(@Param("roles") Set<Role> roles,
            @Param("entityName") String entityName,
            @Param("action") Action action);

    // Upsert lookup: find existing permission with same key for merging
    Optional<Permission> findByUserAndEntityNameAndActionAndAccessType(
            User user, String entityName, Action action, AccessType accessType);

    Optional<Permission> findByRoleAndEntityNameAndActionAndAccessType(
            Role role, String entityName, Action action, AccessType accessType);
}
