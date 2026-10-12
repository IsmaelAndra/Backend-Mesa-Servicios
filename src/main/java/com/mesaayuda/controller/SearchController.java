package com.mesaayuda.controller;

import com.mesaayuda.dto.GlobalSearchResponse;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Búsqueda global.
 *
 * <p>Ruta fuera de {@code /admin/**}: en {@code SecurityConfig} solo compite con
 * {@code anyRequest().authenticated()}, así que cualquier rol con sesión puede
 * llamarla y el propio {@link SearchService} decide qué grupos sirve a cada rol.
 */
@RestController
@RequestMapping("/search")
@Tag(name = "Búsqueda global", description = "Búsqueda unificada de tickets, personas, equipos, áreas y workflows")
public class SearchController {

    /** Menos de dos caracteres convierte la búsqueda en un barrido inútil del índice. */
    private static final int MINIMO_CARACTERES = 2;

    @Autowired
    private SearchService searchService;

    @GetMapping
    @Operation(summary = "Buscar en la plataforma con un término libre",
            description = "Devuelve hasta 5 coincidencias por grupo. Los grupos se filtran por rol: "
                    + "tickets propios para USER, personas solo para el personal y workflows solo para SUPER_ADMIN.")
    public GlobalSearchResponse buscar(
            @RequestParam("q") String q) {
        if (q == null || q.trim().length() < MINIMO_CARACTERES) {
            throw new BadRequestException("El término de búsqueda debe tener al menos "
                    + MINIMO_CARACTERES + " caracteres");
        }
        return searchService.buscar(q);
    }
}