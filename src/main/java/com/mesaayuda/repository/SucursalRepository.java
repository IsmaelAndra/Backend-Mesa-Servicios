package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {

    Optional<Sucursal> findByNameSucursal(String nameSucursal);

    Optional<Sucursal> findByCodeSucursal(String codeSucursal);

    boolean existsByNameSucursal(String nameSucursal);

    boolean existsByCodeSucursal(String codeSucursal);

    List<Sucursal> findByDeletedSucursalFalseOrderByNameSucursalAsc();

    /** Solo las activas: es lo que se ofrece en los selectores de la interfaz. */
    List<Sucursal> findByDeletedSucursalFalseAndStatusSucursalTrueOrderByNameSucursalAsc();
}