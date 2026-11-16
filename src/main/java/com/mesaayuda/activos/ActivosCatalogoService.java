package com.mesaayuda.activos;

import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Maestro de tipos de activo y configuración de numeración (Fase 4). */
@Service
@Transactional
public class ActivosCatalogoService {

    private final AssetTypeRepository tipos;
    private final AssetConfigRepository configuraciones;
    private final NumeracionActivos numeracion;
    private final PermisosActivos permisos;
    private final UserService userService;
    private final SystemAuditService auditoria;

    public ActivosCatalogoService(AssetTypeRepository tipos, AssetConfigRepository configuraciones,
                                  NumeracionActivos numeracion, PermisosActivos permisos, UserService userService,
                                  SystemAuditService auditoria) {
        this.tipos = tipos;
        this.configuraciones = configuraciones;
        this.numeracion = numeracion;
        this.permisos = permisos;
        this.userService = userService;
        this.auditoria = auditoria;
    }

    public record TipoRequest(String nombre, String prefijo, String icono, String color, Boolean activo) {
    }

    public record TipoResponse(UUID id, String nombre, String prefijo, String icono, String color, boolean activo) {
    }

    public record ConfigRequest(String formatoCodigo, Boolean contadorPorTipo, String formatoActa, String ciudad,
                                String codigoFormulario, String versionFormulario, Integer diasAvisoGarantia) {
    }

    public record ConfigResponse(String formatoCodigo, boolean contadorPorTipo, String formatoActa, String ciudad,
                                 String codigoFormulario, String versionFormulario, int diasAvisoGarantia,
                                 String ejemploCodigo, String ejemploActa) {
    }

    // ---------------------------------------------------------------- tipos

    /** Cualquiera con sesión lee los tipos (los muestran "Mis equipos" y la búsqueda). */
    @Transactional(readOnly = true)
    public List<TipoResponse> tipos() {
        return tipos.findByDeletedFalseOrderByNombreAsc().stream().map(ActivosCatalogoService::vista).toList();
    }

    public TipoResponse guardarTipo(UUID id, TipoRequest r) {
        permisos.exigirGestion(userService.getCurrentUser());
        if (r == null || r.nombre() == null || r.nombre().isBlank()) {
            throw new BadRequestException("Ponga un nombre al tipo");
        }
        String prefijo = r.prefijo() == null ? "" : r.prefijo().trim().toUpperCase();
        if (!prefijo.matches("[A-Z0-9]{2,10}")) {
            throw new BadRequestException("El prefijo lleva de 2 a 10 letras o números (ej.: LAP)");
        }
        AssetType t = id == null ? new AssetType()
                : tipos.findById(id).filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de activo", "id", id));
        String nombre = r.nombre().trim();
        tipos.porNombre(nombre).filter(o -> !o.getIdAssetType().equals(t.getIdAssetType())).ifPresent(o -> {
            throw new BadRequestException("Ya existe el tipo " + nombre);
        });
        tipos.porPrefijo(prefijo).filter(o -> !o.getIdAssetType().equals(t.getIdAssetType())).ifPresent(o -> {
            throw new BadRequestException("El prefijo " + prefijo + " ya lo usa " + o.getNombre());
        });
        t.setNombre(nombre.length() > 80 ? nombre.substring(0, 80) : nombre);
        t.setPrefijo(prefijo);
        t.setIcono(r.icono() != null && r.icono().matches("^[a-z0-9 -]{3,100}$") ? r.icono() : "fa-solid fa-box");
        t.setColor(r.color() != null && r.color().matches("^#[0-9A-Fa-f]{6}$") ? r.color() : "#475569");
        if (r.activo() != null) {
            t.setActivo(r.activo());
        }
        return vista(tipos.save(t));
    }

    public void eliminarTipo(UUID id) {
        permisos.exigirGestion(userService.getCurrentUser());
        AssetType t = tipos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tipo de activo", "id", id));
        t.setDeleted(true);
        tipos.save(t);
    }

    private static TipoResponse vista(AssetType t) {
        return new TipoResponse(t.getIdAssetType(), t.getNombre(), t.getPrefijo(), t.getIcono(), t.getColor(),
                Boolean.TRUE.equals(t.getActivo()));
    }

    // ---------------------------------------------------------------- configuración

    @Transactional(readOnly = true)
    public ConfigResponse config() {
        return vista(numeracion.config());
    }

    public ConfigResponse guardarConfig(ConfigRequest r) {
        var u = userService.getCurrentUser();
        permisos.exigirGestion(u);
        AssetConfig c = numeracion.config();
        if (r.formatoCodigo() != null) {
            NumeracionActivos.validarFormato(r.formatoCodigo().trim(), "código");
            c.setFormatoCodigo(r.formatoCodigo().trim());
        }
        if (r.formatoActa() != null) {
            NumeracionActivos.validarFormato(r.formatoActa().trim(), "acta");
            c.setFormatoActa(r.formatoActa().trim());
        }
        if (r.contadorPorTipo() != null) c.setContadorPorTipo(r.contadorPorTipo());
        if (r.ciudad() != null && !r.ciudad().isBlank()) c.setCiudad(r.ciudad().trim());
        if (r.codigoFormulario() != null && !r.codigoFormulario().isBlank()) c.setCodigoFormulario(r.codigoFormulario().trim());
        if (r.versionFormulario() != null && !r.versionFormulario().isBlank()) c.setVersionFormulario(r.versionFormulario().trim());
        if (r.diasAvisoGarantia() != null) {
            if (r.diasAvisoGarantia() < 1 || r.diasAvisoGarantia() > 365) {
                throw new BadRequestException("Los días de aviso de garantía van de 1 a 365");
            }
            c.setDiasAvisoGarantia(r.diasAvisoGarantia());
        }
        c = configuraciones.save(c);
        auditoria.record("ACTIVOS_CONFIG", "AssetConfig", c.getIdAssetConfig(), u.getCorporateEmailUser(),
                c.getFormatoCodigo() + " / " + c.getFormatoActa());
        return vista(c);
    }

    private static ConfigResponse vista(AssetConfig c) {
        return new ConfigResponse(c.getFormatoCodigo(), Boolean.TRUE.equals(c.getContadorPorTipo()), c.getFormatoActa(),
                c.getCiudad(), c.getCodigoFormulario(), c.getVersionFormulario(),
                c.getDiasAvisoGarantia() == null ? 30 : c.getDiasAvisoGarantia(),
                NumeracionActivos.aplicar(c.getFormatoCodigo(), "LAP", 123),
                NumeracionActivos.aplicar(c.getFormatoActa(), "", 1));
    }
}
