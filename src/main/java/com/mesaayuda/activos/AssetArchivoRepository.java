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
public interface AssetArchivoRepository extends JpaRepository<AssetArchivo, UUID> {

    List<AssetArchivo> findByAssetIdAndDeletedFalseOrderByCreatedAtAsc(UUID assetId);

    long countByAssetIdAndTipoAndDeletedFalse(UUID assetId, String tipo);

    List<AssetArchivo> findByAssetIdInAndTipoAndDeletedFalse(Collection<UUID> assetIds, String tipo);
}
