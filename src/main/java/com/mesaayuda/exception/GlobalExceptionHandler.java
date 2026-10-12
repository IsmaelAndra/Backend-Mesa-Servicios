package com.mesaayuda.exception;

import com.mesaayuda.dto.ApiErrorResponse;
import com.mesaayuda.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Opcional para que las pruebas puedan crear el manejador sin contexto. En la
     * aplicación siempre está; @Lazy evita que el manejador de errores arrastre la
     * capa de persistencia al arrancar.
     */
    @Autowired(required = false)
    @Lazy
    private SystemAuditService systemAuditService;

    /**
     * Lo unico que el cliente recibe de un fallo no previsto. Antes se devolvia
     * {@code ex.getMessage()}, que en un 500 llega a contener nombres de tabla,
     * de columna, el JPQL que fallo, rutas del servidor o fragmentos de
     * conexion: informacion que solo deberia ver quien opera el sistema.
     */
    private static final String MENSAJE_INTERNO =
            "Ocurrió un error interno. El equipo técnico ya fue notificado; "
                    + "si el problema continua, indica este codigo de seguimiento.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", null, request);
    }

    /**
     * Acceso denegado.
     *
     * <p>Aquí es donde se registra, y no en el {@code accessDeniedHandler} de
     * SecurityConfig. La razón es que este manejador se ejecuta antes: cuando el
     * permiso se comprueba con {@code @PreAuthorize} o con un {@code if} dentro
     * del controlador, la excepción salta desde el DispatcherServlet y la resuelve
     * esta clase, de modo que el filtro de Spring Security nunca llega a verla.
     *
     * <p>Los 403 anteriores a esta modificación no dejaban rastro en ninguna parte.
     * Un ADMIN que iba probando endpoints de super administrador no generaba ni
     * una línea de log, que es justo lo contrario de lo que hace falta para
     * detectar un barrido de permisos.
     *
     * <p>Se registra a WARN, no a ERROR: no es un fallo de la aplicación, es una
     * petición que legítimamente se ha rechazado. Uno suelto es ruido; repetidos
     * desde la misma cuenta, no.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            String quien = auth == null || !auth.isAuthenticated()
                    ? "anonimo"
                    : (auth.getName() == null ? "desconocido" : auth.getName());
            String rol = auth == null || auth.getAuthorities() == null ? ""
                    : auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            Object ip = request.getAttribute(com.mesaayuda.security.ClientIpFilter.ATRIBUTO_IP);

            // Solo metodo y ruta: nunca el cuerpo ni las cabeceras, que aquí
            // podrían contener una contraseña o un token de sesión.
            String motivo = ex.getMessage() == null ? "sin detalle" : ex.getMessage();
            log.warn("Acceso denegado: {} {} desde {} usuario={} rol={} motivo={}",
                    request.getMethod(), request.getRequestURI(), ip, quien, rol, motivo);
            // Además del log, a audit_log: es de donde lee la detección de
            // actividad sospechosa, y un barrido de ids ajenos se ve aquí.
            if (systemAuditService != null) {
                systemAuditService.record("ACCESS_DENIED", "Endpoint", null, quien,
                        motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
            }
        } catch (Exception e) {
            // Un fallo al registrar no puede convertirse en un 500: el usuario
            // tiene que seguir recibiendo el 403 que le corresponde.
            log.debug("No se pudo registrar el acceso denegado: {}", e.getMessage());
        }
        return build(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción", null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Errores de validación", errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "La petición no cumple las restricciones requeridas", null, request);
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiErrorResponse> handleAi(AiException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null, request);
    }

    /**
     * Petición incompleta: falta un parámetro o una parte del multipart. Antes no
     * había manejador y caía en el genérico, que responde 500: un error del
     * cliente aparecía en el log como fallo del servidor.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(Exception ex, HttpServletRequest request) {
        String nombre = ex instanceof MissingServletRequestParameterException p ? p.getParameterName()
                : ((MissingServletRequestPartException) ex).getRequestPartName();
        return build(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + nombre + "'", null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido", null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo excede el tamaño máximo permitido", null, request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null, request);
    }

    /**
     * El manejador generico de abajo se come cualquier excepcion, incluidas las
     * que Spring ya sabia clasificar. Sin estos casos, un parametro con tipo
     * equivocado o una URL inexistente responderian 500 en lugar de 400/404, que
     * es peor diagnostico para el cliente y para el monitor de la aplicacion.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Algún parámetro de la petición tiene un formato inválido", null, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para esta ruta", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "El recurso solicitado no existe", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        // El detalle completo se queda en el log del servidor. El cliente solo
        // recibe el requestId para poder correlacionar. Reutilizo el mismo id
        // que puso RequestIdFilter, para que coincida con la cabecera
        // X-Request-Id y con la linea del log.
        String requestId = requestIdDe(request);
        log.error("[{}] {} {} fallo no previsto: {}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex);

        ApiErrorResponse response = new ApiErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                MENSAJE_INTERNO,
                null,
                requestId);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   String message,
                                                   Map<String, String> details,
                                                   HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details,
                requestIdDe(request));
        return new ResponseEntity<>(response, status);
    }

    /**
     * Reutiliza el identificador que el filtro de peticiones ya dejo en la
     * request, para que un mismo error tenga el mismo codigo en toda la traza.
     */
    private String requestIdDe(HttpServletRequest request) {
        Object existing = request == null ? null : request.getAttribute(RequestIdFilter.ATRIBUTO);
        return existing != null ? existing.toString() : UUID.randomUUID().toString();
    }
}
