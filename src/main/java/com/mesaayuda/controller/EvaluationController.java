package com.mesaayuda.controller;

import com.mesaayuda.dto.MonthlyEvaluationResponse;
import com.mesaayuda.service.EvaluationService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/evaluations")
@Tag(name = "Evaluación mensual", description = "Evaluaciones de desempeño del agente")
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private UserService userService;

    @GetMapping("/mine")
    @Operation(summary = "Mis evaluaciones mensuales (AGENT)")
    public List<MonthlyEvaluationResponse> mine() {
        return evaluationService.findByAgent(userService.getCurrentUser().getIdUser());
    }

    @GetMapping("/month/{month}")
    @Operation(summary = "Evaluaciones de un mes")
    public List<MonthlyEvaluationResponse> byMonth(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return evaluationService.findByMonth(month);
    }
}