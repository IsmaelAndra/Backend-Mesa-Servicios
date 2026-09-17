package com.mesaayuda.mapper;

import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import java.time.OffsetDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-17T12:15:31-0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260826-1225, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        String role = null;
        String area = null;
        Long idUser = null;
        String identificationUser = null;
        String corporateEmailUser = null;
        String phoneUser = null;
        String statusUser = null;
        OffsetDateTime createdAtUser = null;

        role = userRolUserNameRole( user );
        area = userAreaNameArea( user );
        idUser = user.getIdUser();
        identificationUser = user.getIdentificationUser();
        corporateEmailUser = user.getCorporateEmailUser();
        phoneUser = user.getPhoneUser();
        statusUser = user.getStatusUser();
        createdAtUser = user.getCreatedAtUser();

        UserResponse userResponse = new UserResponse( idUser, identificationUser, corporateEmailUser, phoneUser, statusUser, role, area, createdAtUser );

        return userResponse;
    }

    private String userRolUserNameRole(User user) {
        if ( user == null ) {
            return null;
        }
        Role rolUser = user.getRolUser();
        if ( rolUser == null ) {
            return null;
        }
        String nameRole = rolUser.getNameRole();
        if ( nameRole == null ) {
            return null;
        }
        return nameRole;
    }

    private String userAreaNameArea(User user) {
        if ( user == null ) {
            return null;
        }
        Area area = user.getArea();
        if ( area == null ) {
            return null;
        }
        String nameArea = area.getNameArea();
        if ( nameArea == null ) {
            return null;
        }
        return nameArea;
    }
}
