package com.mesaayuda.mapper;

import com.mesaayuda.dto.AnswerResponse;
import com.mesaayuda.entity.Answer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AnswerMapper {

    AnswerMapper INSTANCE = Mappers.getMapper(AnswerMapper.class);

    @Mapping(target = "user", source = "userAnswer.corporateEmailUser")
    @Mapping(target = "userDisplayName", source = "userAnswer.displayName")
    @Mapping(target = "userPhotoUrl", source = "userAnswer.photoUrl")
    @Mapping(target = "unreadOnOpen", ignore = true)
    AnswerResponse toResponse(Answer answer);

    List<AnswerResponse> toResponse(List<Answer> answers);
}