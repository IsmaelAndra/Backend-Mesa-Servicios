package com.mesaayuda.controller;

import com.mesaayuda.dto.MfaSettingRequest;
import com.mesaayuda.dto.MfaSettingResponse;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.security.TotpService;
import com.mesaayuda.service.MfaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestión del doble factor.
 *
 * <p>Repartida en dos porque los derechos son distintos: el interruptor global y
 * la lista de códigos de recuperación son del super administrador, mientras que
 * regenerar los códigos propios o quitarse el doble factor son de cualquier
 * usuario, y con la condición de que demuestre que sabe su contraseña.
 */
@RestController
@RequestMapping("/admin/mfa")
@Tag(name = "Doble factor", description = "Interruptor global y códigos de recuperación")
public class MfaController {

    @Autowired
    private MfaService mfaService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private TotpService totp;

    @GetMapping
    @Operation(summary = "Ver el estado del doble factor (SUPER_ADMIN)")
    public ResponseEntity<MfaSettingResponse> estado() {
        // Esta llamada se saltaba la comprobación: el métodoPOST sí la tenía, este no.
        // Un ADMIN podía leer cuántas cuentas tienen el doble factor puesto.
        exigirSuperAdmin();
        return ResponseEntity.ok(mfaService.estado(superAdminId()));
    }

    @PostMapping
    @Operation(summary = "Activar o desactivar el doble factor (SUPER_ADMIN)")
    public ResponseEntity<MfaSettingResponse> cambiar(@Valid @RequestBody MfaSettingRequest request) {
        exigirSuperAdmin();
        return ResponseEntity.ok(mfaService.cambiar(
                Boolean.TRUE.equals(request.mfaEnabled()), request.issuer(), superAdminId()));
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Ver los códigos de recuperación de la cuenta actual")
    public ResponseEntity<Map<String, Object>> codigosPropios() {
        User actual = userService.getCurrentUser();
        return ResponseEntity.ok(Map.of(
                "restantes", mfaService.codigosRecuperacionRestantes(actual.getIdUser()),
                "enrolled", mfaService.estadoDe(actual.getIdUser())
                        .map(com.mesaayuda.entity.UserMfa::isEnrolled).orElse(false)));
    }

    @PostMapping("/recovery-codes/generar")
    @Operation(summary = "Generar nuevos códigos de recuperación (invalida los anteriores)")
    public ResponseEntity<List<String>> regenerar(@RequestBody(required = false) String cuerpo) {
        User actual = userService.getCurrentUser();
        // Se exige la contraseña aunque no llegue en el cuerpo. La idea es que
        // quien se siente delante de un equipo ya abierto no pueda llevarse los
        // códigos: regenerarlos sin probar la contraseña dejaría las cuentas de
        //recovery de cualquiera que hubiera abierto la sesión.
        List<String> nuevos = mfaService.regenerar(actual, cuerpo);
        return ResponseEntity.ok(nuevos);
    }

    @PostMapping("/desactivar")
    @Operation(summary = "Quitarse el doble factor, probando la contraseña y un código válido")
    public ResponseEntity<Map<String, Object>> desactivar(
            @RequestBody DesactivarRequest request) {
        // Quitarse el doble factor es de la cuenta propia: cualquiera lo puede
        // hacer, porque el servicio ya exige su contraseña y un código vigente.
        // Exigir SUPER_ADMIN impediría que un usuario normal se lo quitara, que es
        // justo lo contrario de lo que se quiere.
        User actual = userService.getCurrentUser();
        var estado = mfaService.estadoDe(actual.getIdUser());
        if (estado.isEmpty() || !estado.get().isEnrolled()) {
            throw new BadRequestException("Esta cuenta no tiene doble factor activado.");
        }
        if (!estado.get().isDesactivableUsuario()
                && !"SUPER_ADMIN".equalsIgnoreCase(actual.getRolUser().getNameRole())) {
            throw new BadRequestException("El doble factor no se puede quitar desde la cuenta.");
        }
        mfaService.desactivar(actual, request.password(), request.codigo());
        return ResponseEntity.ok(Map.of("enrolled", false));
    }

    /** Petición de desactivación, Petición de desactivación del doble factor propio.. */
    public record DesactivarRequest(String password, String codigo) {
    }

    private UUID superAdminId() {
        return userService.getCurrentUser().getIdUser();
    }

    private void exigirSuperAdmin() {
        User actual = userService.getCurrentUser();
        if (actual.getRolUser() == null
                || !"SUPER_ADMIN".equalsIgnoreCase(actual.getRolUser().getNameRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Acceso denegado");
        }
    }

}