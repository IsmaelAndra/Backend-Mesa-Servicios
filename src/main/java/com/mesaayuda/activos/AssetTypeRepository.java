package com.mesaayuda.activos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, UUID> {

    List<AssetType> findByDeletedFalseOrderByNombreAsc();

    @Query("SELECT t FROM AssetType t WHERE t.deleted = false AND lower(t.nombre) = lower(:nombre)")
    Optional<AssetType> porNombre(@Param("nombre") String nombre);

    @Query("SELECT t FROM AssetType t WHERE t.deleted = false AND upper(t.prefijo) = upper(:prefijo)")
    Optional<AssetType> porPrefijo(@Param("prefijo") String prefijo);
}
