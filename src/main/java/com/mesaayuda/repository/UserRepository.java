package com.mesaayuda.repository;

import com.mesaayuda.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByCorporateEmailUser(String corporateEmailUser);
    Optional<User> findByIdentificationUser(String identificationUser);
    boolean existsByCorporateEmailUser(String corporateEmailUser);
    boolean existsByIdentificationUser(String identificationUser);

    List<User> findByStatusUserAndDeletedUserFalse(String statusUser);

    @Query("SELECT u FROM User u WHERE u.rolUser.nameRole = :roleName AND u.deletedUser = false AND u.statusUser = 'ACTIVE'")
    List<User> findByRoleName(@Param("roleName") String roleName);

    long countByRolUserNameRoleAndDeletedUserFalse(String roleName);
}