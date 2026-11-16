package com.mesaayuda.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mesaayuda.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {
    Optional<Asset> findByCodeAsset(String codeAsset);
    boolean existsByCodeAsset(String codeAsset);
    List<Asset> findByDeletedAssetFalseOrderByCreatedAtAssetDesc();
    List<Asset> findByAssignedUser_IdUserAndDeletedAssetFalse(UUID userId);
    List<Asset> findByArea_IdAreaAndDeletedAssetFalse(UUID areaId);
    List<Asset> findBySucursal_IdSucursalAndDeletedAssetFalse(UUID sucursalId);
    List<Asset> findByStatusAssetAndDeletedAssetFalse(String status);
    List<Asset> findByTypeAssetAndDeletedAssetFalse(String type);

    /** Para el escáner: el código tal cual lo lee la cámara, sin distinguir mayúsculas. */
    @Query("SELECT a FROM Asset a WHERE a.deletedAsset = false AND lower(a.codeAsset) = lower(:codigo)")
    Optional<Asset> porCodigo(@Param("codigo") String codigo);

    /** Garantías que vencen antes de {@code hasta} (y no han vencido) y aún no se avisaron. */
    @Query("SELECT a FROM Asset a WHERE a.deletedAsset = false AND a.statusAsset <> 'BAJA' "
            + "AND a.warrantyUntilAsset IS NOT NULL AND a.garantiaAvisadaAsset IS NULL "
            + "AND a.warrantyUntilAsset <= :hasta AND a.warrantyUntilAsset >= CURRENT_TIMESTAMP")
    List<Asset> garantiasPorAvisar(@Param("hasta") java.time.OffsetDateTime hasta);

    /** Otro activo vivo con el mismo número de serie (sin distinguir mayúsculas). */
    @org.springframework.data.jpa.repository.Query("SELECT count(a) > 0 FROM Asset a WHERE a.deletedAsset = false "
            + "AND lower(trim(a.serialNumberAsset)) = lower(:serie) AND a.idAsset <> :propio")
    boolean serieRepetida(@org.springframework.data.repository.query.Param("serie") String serie,
                          @org.springframework.data.repository.query.Param("propio") java.util.UUID propio);
}
