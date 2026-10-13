package com.mesaayuda.mapper;

import com.mesaayuda.dto.AssetHistoryResponse;
import com.mesaayuda.entity.AssetHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetHistoryMapper {

    AssetHistoryMapper INSTANCE = Mappers.getMapper(AssetHistoryMapper.class);

    @Mapping(target = "assetId", source = "asset.idAsset")
    @Mapping(target = "assetCode", source = "asset.codeAsset")
    @Mapping(target = "assetName", source = "asset.nameAsset")
    @Mapping(target = "userId", source = "user.idUser")
    @Mapping(target = "user", source = "user.corporateEmailUser")
    @Mapping(target = "userDisplayName", expression = "java(history.getUser() != null ? history.getUser().getFullName() : null)")
    @Mapping(target = "userPhotoUrl", source = "user.photoUrl")
    @Mapping(target = "previousUserId", source = "previousUser.idUser")
    @Mapping(target = "previousUser", source = "previousUser.corporateEmailUser")
    @Mapping(target = "previousUserDisplayName", expression = "java(history.getPreviousUser() != null ? history.getPreviousUser().getFullName() : null)")
    @Mapping(target = "performedById", source = "performedBy.idUser")
    @Mapping(target = "performedBy", source = "performedBy.corporateEmailUser")
    @Mapping(target = "performedByDisplayName", expression = "java(history.getPerformedBy() != null ? history.getPerformedBy().getFullName() : null)")
    AssetHistoryResponse toResponse(AssetHistory history);

    List<AssetHistoryResponse> toResponse(List<AssetHistory> histories);
}
