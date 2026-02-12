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

        List<Permission> findByUserAndEntityNameAndAction(User user, String entityName, Action action);

        @Query("SELECT p FROM Permission p WHERE p.role IN :roles AND p.entityName = :entityName AND p.action = :action")
        List<Permission> findByRoles(@Param("roles") Set<Role> roles,
                        @Param("entityName") String entityName,
                        @Param("action") Action action);

        Optional<Permission> findByUserAndEntityNameAndActionAndAccessType(
                        User user, String entityName, Action action, AccessType accessType);

        Optional<Permission> findByRoleAndEntityNameAndActionAndAccessType(
                        Role role, String entityName, Action action, AccessType accessType);
}
