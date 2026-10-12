package com.mesaayuda.exception;

import com.mesaayuda.dto.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El manejador global es la ultima linea de defensa: si un dia vuelve a
 * concatenar ex.getMessage() en la respuesta, se filtran nombres de tabla,
 * columnas o el JPQL que fallo. Estos tests lo impiden.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tickets");
        request.setAttribute(RequestIdFilter.ATRIBUTO, "req-de-prueba");
        return request;
    }

    @Test
    @DisplayName("Un 500 no devuelve el mensaje interno de la excepción")
    void errorGenericoNoFiltraDetalleInterno() {
        SQLException causa = new SQLException("relation \"usuario_secret\" does not exist");

        ResponseEntity<ApiErrorResponse> r = handler.handleGeneric(causa, request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody())
                .isNotNull()
                .satisfies(body -> {
                    assertThat(body.message())
                            .doesNotContain("usuario_secret")
                            .doesNotContain("does not exist")
                            .doesNotContain("relation");
                });
    }

    @Test
    @DisplayName("El 500 incluye requestId para poder correlacionar con el log")
    void errorGenericoIncluyeRequestId() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleGeneric(new IllegalStateException("fallo"), request());

        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().requestId()).isEqualTo("req-de-prueba");
    }

    @Test
    @DisplayName("Si la petición no trae requestId se genera uno, nunca null")
    void sinRequestIdSeGenera() {
        MockHttpServletRequest sinId = new MockHttpServletRequest("GET", "/api/tickets");

        ResponseEntity<ApiErrorResponse> r =
                handler.handleGeneric(new IllegalStateException("fallo"), sinId);

        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().requestId()).isNotBlank();
    }

    @Test
    @DisplayName("Un parámetro con tipo inválido responde 400, no 500")
    void tipoInvalidoResponde400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "userId", null, null);

        ResponseEntity<ApiErrorResponse> r = handler.handleTypeMismatch(ex, request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().message())
                .doesNotContain("MethodArgumentTypeMismatchException")
                .doesNotContain("Long");
    }

    @Test
    @DisplayName("Una ruta inexistente responde 404, no 500")
    void rutaInexistenteResponde404() {
        ResponseEntity<ApiErrorResponse> r = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/no-existe"), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Los errores de negocio siguen siendo legibles")
    void errorDeNegocioSeMuestra() {
        ResponseEntity<ApiErrorResponse> r = handler.handleBadRequest(
                new BadRequestException("La contraseña actual es incorrecta"), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().message()).isEqualTo("La contraseña actual es incorrecta");
    }
}
