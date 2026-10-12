package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameRole(String nameRole);
    boolean existsByNameRole(String nameRole);
    boolean existsByNameRoleAndDeletedRoleFalse(String nameRole);
}