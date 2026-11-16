package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByCorporateEmailUser(String corporateEmailUser);
    Optional<User> findByIdentificationUser(String identificationUser);
    boolean existsByCorporateEmailUser(String corporateEmailUser);
    boolean existsByIdentificationUser(String identificationUser);

    List<User> findByStatusUserAndDeletedUserFalse(String statusUser);

    /**
     * Agentes activos, para el desplegable de asignacion.
     *
     * <p>El nombre sigue la forma de Spring Data: RolUserNameRole resuelve
     * user.rolUser.nameRole y los siguientes tramos son condiciones sobre la
     * entidad. El orden por id es el que ya usaba el listado general, para que el
     * desplegable no cambie de posicion entre recargas.
     */
    List<User> findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc(
            String nameRole, String statusUser);

    @Query("SELECT u FROM User u WHERE u.rolUser.nameRole = :roleName AND u.deletedUser = false AND u.statusUser = 'ACTIVE'")
    List<User> findByRoleName(@Param("roleName") String roleName);

    /** Jefes activos de un área (un jefe responde por la de su cuenta, V33). */
    @Query("SELECT u FROM User u WHERE u.rolUser.nameRole = 'AREA_MANAGER' AND u.area.idArea = :areaId "
            + "AND u.deletedUser = false AND u.statusUser = 'ACTIVE'")
    List<User> jefesActivosDe(@Param("areaId") UUID areaId);

    /** Personal activo de unos roles (candidatos a un equipo), ordenado por nombre. */
    @Query("SELECT u FROM User u WHERE u.rolUser.nameRole IN :roles AND u.deletedUser = false "
            + "AND u.statusUser = 'ACTIVE' ORDER BY lower(coalesce(u.displayName, u.corporateEmailUser))")
    List<User> personalActivo(@Param("roles") java.util.Collection<String> roles);

    long countByRolUserNameRoleAndDeletedUserFalse(String roleName);

    /** Personas vivas de una sucursal: impide borrar una sucursal que aun se usa. */
    /**
 * Personas no eliminadas de una sucursal.
 *
 * <p>El nombre lleva el guion bajo porque {@code countBySucursalId...} no
 * funciona: Spring Data interpretaría {@code sucursalId} como la propiedad
 * {@code id} de la entidad Sucursal, y ahí no existe, se llama
 * {@code idSucursal}. Con la forma {@code Sucursal_IdSucursal} la propiedad se
 * indica sin ambigüedad y el método se crea al arrancar. El fallo solo aparece
 * al levantar la aplicación, no en los tests unitarios, porque es el repositorio
 * el que construye la consulta.
 */
long countBySucursal_IdSucursalAndDeletedUserFalse(UUID sucursalId);

    /**
     * Invalida los tokens de acceso del usuario emitidos hasta ahora. Necesita
     * transacción en el servicio que lo llama.
     */
    @Modifying
    @Query("UPDATE User u SET u.tokensValidAfter = :instante WHERE u.idUser = :userId")
    int invalidarTokensDeAcceso(@Param("userId") UUID userId, @Param("instante") OffsetDateTime instante);
}
