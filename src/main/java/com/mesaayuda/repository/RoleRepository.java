package com.mesaayuda.repository;

import com.mesaayuda.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNameRole(String nameRole);
    boolean existsByNameRole(String nameRole);
    boolean existsByNameRoleAndDeletedRoleFalse(String nameRole);
}