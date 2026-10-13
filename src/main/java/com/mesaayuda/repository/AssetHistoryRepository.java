package com.mesaayuda.repository;

import java.util.List;
import java.util.UUID;

import com.mesaayuda.entity.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, UUID> {
    List<AssetHistory> findByAsset_IdAssetOrderByCreatedAtAssetHistoryDesc(UUID assetId);
    List<AssetHistory> findByUser_IdUserOrderByCreatedAtAssetHistoryDesc(UUID userId);
}