package com.mesaayuda.mapper;

import com.mesaayuda.dto.AssetResponse;
import com.mesaayuda.entity.Asset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    AssetMapper INSTANCE = Mappers.getMapper(AssetMapper.class);

    @Mapping(target = "assignedUserId", source = "assignedUser.idUser")
    @Mapping(target = "assignedUser", source = "assignedUser.corporateEmailUser")
    @Mapping(target = "assignedUserDisplayName", expression = "java(asset.getAssignedUser() != null ? asset.getAssignedUser().getFullName() : null)")
    @Mapping(target = "assignedUserPhotoUrl", source = "assignedUser.photoUrl")
    @Mapping(target = "assignedUserArea", source = "assignedUser.area.nameArea")
    @Mapping(target = "areaId", source = "area.idArea")
    @Mapping(target = "area", source = "area.nameArea")
    @Mapping(target = "sucursalId", source = "sucursal.idSucursal")
    @Mapping(target = "sucursal", source = "sucursal.nameSucursal")
    AssetResponse toResponse(Asset asset);

    List<AssetResponse> toResponse(List<Asset> assets);
}