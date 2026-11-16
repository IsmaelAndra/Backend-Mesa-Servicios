package com.mesaayuda.reportes;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El constructor de reportes solo arma SQL con el catálogo cerrado y acota al jefe a sus áreas. */
class ConstructorConsultaTest {

    private static ConstructorConsulta.Definicion def(List<String> columnas, Map<String, String> filtros) {
        return new ConstructorConsulta.Definicion("TICKETS", columnas, filtros);
    }

    @Test
    @DisplayName("las columnas salen en el orden elegido y con su etiqueta como alias")
    void columnasEnOrden() {
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(
                def(List.of("estado", "numero"), Map.of()), null, null, null, null);
        assertEquals(List.of("Estado", "Ticket"), c.encabezados());
        assertTrue(c.sql().startsWith("SELECT s.name_status AS \"Estado\", t.number_ticket AS \"Ticket\""));
    }

    @Test
    @DisplayName("una columna o tabla fuera del catálogo se rechaza: no hay forma de colar SQL")
    void nadaFueraDelCatalogo() {
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                def(List.of("numero", "1; DROP TABLE ticket"), Map.of()), null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                new ConstructorConsulta.Definicion("users", List.of("numero"), Map.of()), null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                def(List.of("numero"), Map.of("area", "' OR 1=1 --")), null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                def(List.of("numero"), Map.of("contraseña", id(1).toString())), null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                def(List.of(), Map.of()), null, null, null, null));
    }

    @Test
    @DisplayName("los valores de los filtros y las fechas van como parámetros, nunca dentro del SQL")
    void valoresComoParametros() {
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(
                def(List.of("numero"), Map.of("estado", id(7).toString())),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null);
        assertFalse(c.sql().contains(id(7).toString()));
        assertTrue(c.sql().contains("t.status_ticket = :f0"));
        assertEquals(id(7), c.parametros().getValue("f0"));
        assertTrue(c.parametros().hasValue("desde") && c.parametros().hasValue("hasta"));
    }

    @Test
    @DisplayName("el jefe solo obtiene filas de sus áreas; sin áreas, ninguna")
    void elJefeQuedaEnSusAreas() {
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(
                def(List.of("numero"), Map.of()), null, null, Set.of(id(50)), null);
        assertTrue(c.sql().contains("t.area_destino_ticket IN (:ambito)"));

        ConstructorConsulta.Consulta sinAreas = ConstructorConsulta.construir(
                def(List.of("numero"), Map.of()), null, null, Set.of(), null);
        assertTrue(sinAreas.sql().contains(" AND FALSE"));

        ConstructorConsulta.Consulta superAdmin = ConstructorConsulta.construir(
                def(List.of("numero"), Map.of()), null, null, null, null);
        assertFalse(superAdmin.sql().contains(":ambito"));
    }

    @Test
    @DisplayName("el título de un confidencial nunca sale en un reporte")
    void confidencialOculto() {
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(
                def(List.of("titulo", "solicitante"), Map.of()), null, null, null, 10);
        assertTrue(c.sql().contains("WHEN t.confidencial_ticket THEN 'Ticket confidencial'"));
        assertTrue(c.sql().contains("WHEN t.confidencial_ticket THEN 'Confidencial'"));
        assertTrue(c.sql().endsWith("LIMIT 10"));
    }

    @Test
    @DisplayName("un resumen agrupa por las columnas elegidas y añade sus totales")
    void resumenAgrupado() {
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(
                new ConstructorConsulta.Definicion("TICKETS", List.of("area_atiende", "agente"), Map.of(),
                        List.of("total", "sla_cumplido")), null, null, Set.of(id(3)), null);
        assertEquals(List.of("Área que atiende", "Agente", "Total de tickets", "% SLA cumplido"), c.encabezados());
        assertTrue(c.sql().contains("COUNT(*) AS \"Total de tickets\""));
        assertTrue(c.sql().contains(" GROUP BY 1, 2 ORDER BY 1, 2"));
        // El jefe sigue acotado a sus áreas aunque sea un resumen.
        assertTrue(c.sql().contains("t.area_destino_ticket IN (:ambito)"));
    }

    @Test
    @DisplayName("en un resumen solo se agrupa por columnas agrupables y con totales del catálogo")
    void resumenSoloConCatalogo() {
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                new ConstructorConsulta.Definicion("TICKETS", List.of("titulo"), Map.of(), List.of("total")),
                null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                new ConstructorConsulta.Definicion("TICKETS", List.of("estado"), Map.of(), List.of("SUM(1)")),
                null, null, null, null));
        assertThrows(BadRequestException.class, () -> ConstructorConsulta.construir(
                new ConstructorConsulta.Definicion("TICKETS", List.of("estado", "agente", "area_atiende", "mes"),
                        Map.of(), List.of("total")), null, null, null, null));
    }
}
