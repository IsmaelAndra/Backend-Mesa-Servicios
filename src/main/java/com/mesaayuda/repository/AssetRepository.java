package com.mesaayuda.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mesaayuda.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}