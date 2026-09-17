package com.mesaayuda.mapper;

import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "role", source = "rolUser.nameRole")
    @Mapping(target = "area", source = "area.nameArea")
    UserResponse toResponse(User user);
}