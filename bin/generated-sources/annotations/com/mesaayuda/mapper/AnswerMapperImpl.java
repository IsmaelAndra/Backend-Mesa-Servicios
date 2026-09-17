package com.mesaayuda.mapper;

import com.mesaayuda.dto.AnswerResponse;
import com.mesaayuda.entity.Answer;
import com.mesaayuda.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-17T12:15:31-0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260826-1225, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class AnswerMapperImpl implements AnswerMapper {

    @Override
    public AnswerResponse toResponse(Answer answer) {
        if ( answer == null ) {
            return null;
        }

        String user = null;
        Long idAnswer = null;
        String messageAnswer = null;
        Boolean internalAnswer = null;
        OffsetDateTime createdAtAnswer = null;

        user = answerUserAnswerCorporateEmailUser( answer );
        idAnswer = answer.getIdAnswer();
        messageAnswer = answer.getMessageAnswer();
        internalAnswer = answer.getInternalAnswer();
        createdAtAnswer = answer.getCreatedAtAnswer();

        AnswerResponse answerResponse = new AnswerResponse( idAnswer, messageAnswer, internalAnswer, user, createdAtAnswer );

        return answerResponse;
    }

    @Override
    public List<AnswerResponse> toResponse(List<Answer> answers) {
        if ( answers == null ) {
            return null;
        }

        List<AnswerResponse> list = new ArrayList<AnswerResponse>( answers.size() );
        for ( Answer answer : answers ) {
            list.add( toResponse( answer ) );
        }

        return list;
    }

    private String answerUserAnswerCorporateEmailUser(Answer answer) {
        if ( answer == null ) {
            return null;
        }
        User userAnswer = answer.getUserAnswer();
        if ( userAnswer == null ) {
            return null;
        }
        String corporateEmailUser = userAnswer.getCorporateEmailUser();
        if ( corporateEmailUser == null ) {
            return null;
        }
        return corporateEmailUser;
    }
}
