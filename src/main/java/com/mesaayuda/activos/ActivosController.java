package com.mesaayuda.activos;

import com.mesaayuda.dto.AssetResponse;
import com.mesaayuda.entity.Asset;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.AssetMapper;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.service.AssetService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fase 4 de activos: tipos, numeración, fotos y documentos, actas
 * FOR-POL-TIC-01-10, etiquetas con código de barras y búsqueda por código
 * (escáner). Los permisos los deciden los servicios (PermisosActivos).
 */
@RestController
@RequestMapping("/assets")
@Tag(name = "Activos (Fase 4)", description = "Tipos, numeración, archivos, actas y etiquetas")
public class ActivosController {

    private final ActivosCatalogoService catalogo;
    private final AssetArchivoService archivos;
    private final ActaActivoService actas;
    private final EtiquetasPdf etiquetas;
    private final AssetRepository activos;
    private final AssetService assetService;
    private final AssetMapper mapper;
    private final UserService userService;

    public ActivosController(ActivosCatalogoService catalogo, AssetArchivoService archivos, ActaActivoService actas,
                             EtiquetasPdf etiquetas, AssetRepository activos, AssetService assetService,
                             AssetMapper mapper, UserService userService) {
        this.catalogo = catalogo;
        this.archivos = archivos;
        this.actas = actas;
        this.etiquetas = etiquetas;
        this.activos = activos;
        this.assetService = assetService;
        this.mapper = mapper;
        this.userService = userService;
    }

    public record EtiquetasRequest(List<UUID> ids) {
    }

    // ---------------------------------------------------------------- tipos y configuración

    @GetMapping("/tipos")
    public List<ActivosCatalogoService.TipoResponse> tipos() {
        return catalogo.tipos();
    }

    @PostMapping("/tipos")
    public ActivosCatalogoService.TipoResponse crearTipo(@RequestBody ActivosCatalogoService.TipoRequest r) {
        return catalogo.guardarTipo(null, r);
    }

    @PutMapping("/tipos/{id}")
    public ActivosCatalogoService.TipoResponse editarTipo(@PathVariable UUID id, @RequestBody ActivosCatalogoService.TipoRequest r) {
        return catalogo.guardarTipo(id, r);
    }

    @DeleteMapping("/tipos/{id}")
    public ResponseEntity<Void> eliminarTipo(@PathVariable UUID id) {
        catalogo.eliminarTipo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config")
    public ActivosCatalogoService.ConfigResponse config() {
        return catalogo.config();
    }

    @PutMapping("/config")
    public ActivosCatalogoService.ConfigResponse guardarConfig(@RequestBody ActivosCatalogoService.ConfigRequest r) {
        return catalogo.guardarConfig(r);
    }

    // ---------------------------------------------------------------- escáner

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Buscar un activo por su código (lo usa el escáner con la cámara)")
    public AssetResponse porCodigo(@PathVariable String codigo) {
        Asset a = activos.porCodigo(codigo.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Activo", "código", codigo));
        // findById es transaccional: el controlador no tiene sesión abierta y
        // mapear aquí la persona asignada (relación LAZY) daba "could not initialize proxy".
        return assetService.findById(a.getIdAsset());
    }

    // ---------------------------------------------------------------- etiquetas

    @PostMapping("/etiquetas")
    @Operation(summary = "PDF A4 con etiquetas de código de barras de los activos elegidos")
    public ResponseEntity<byte[]> etiquetas(@RequestBody EtiquetasRequest r) {
        var u = userService.getCurrentUser();
        if (r == null || r.ids() == null || r.ids().isEmpty()) {
            throw new BadRequestException("Elija los activos");
        }
        if (r.ids().size() > 240) {
            throw new BadRequestException("Como máximo 240 etiquetas (10 hojas) por vez");
        }
        List<Asset> lista = new ArrayList<>();
        for (UUID id : r.ids()) {
            Asset a = activos.findById(id).filter(x -> !Boolean.TRUE.equals(x.getDeletedAsset()))
                    .orElseThrow(() -> new ResourceNotFoundException("Activo", "id", id));
            if (!assetService.puedeGestionar(u, a)) {
                throw new AccessDeniedException("Las etiquetas las imprimen Tecnología y Desarrollo");
            }
            lista.add(a);
        }
        return pdf("etiquetas-activos.pdf", etiquetas.generar(lista), true);
    }

    // ---------------------------------------------------------------- archivos

    @GetMapping("/{id}/archivos")
    public List<AssetArchivoService.ArchivoVista> archivos(@PathVariable UUID id) {
        return archivos.listar(id);
    }

    @PostMapping(value = "/{id}/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir foto (máx. 6, 5 MB) o PDF de garantía/factura (10 MB)")
    public AssetArchivoService.ArchivoVista subir(@PathVariable UUID id, @RequestParam("tipo") String tipo,
                                                  @RequestParam(value = "vence", required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vence,
                                                  @RequestParam("archivo") MultipartFile archivo) {
        return archivos.subir(id, tipo, vence, archivo);
    }

    @GetMapping("/{id}/archivos/{archivoId}")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID id, @PathVariable UUID archivoId) {
        AssetArchivoService.Descarga d = archivos.descargar(id, archivoId);
        return archivo(d.nombre(), d.mime(), d.datos());
    }

    @DeleteMapping("/{id}/archivos/{archivoId}")
    public ResponseEntity<Void> borrarArchivo(@PathVariable UUID id, @PathVariable UUID archivoId) {
        archivos.eliminar(id, archivoId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- actas

    @GetMapping("/actas")
    @Operation(summary = "Actas: todas (Tecnología/Desarrollo, auditor), las propias o las de un activo")
    public List<ActaActivoService.ActaVista> actas(@RequestParam(required = false) UUID assetId) {
        return actas.listar(assetId);
    }

    @GetMapping("/actas/revisores")
    public List<ActaActivoService.Persona> revisores() {
        return actas.revisores();
    }

    @PostMapping("/actas")
    @Operation(summary = "Entregar, devolver o reasignar equipos generando el acta")
    public ActaActivoService.ActaVista crearActa(@RequestBody ActaActivoService.ActaRequest r, HttpServletRequest req) {
        return actas.crear(r, req.getRemoteAddr());
    }

    @PostMapping("/actas/faltantes")
    @Operation(summary = "Quiénes firmarían el acta sin nombre o cargo (para avisar antes de generarla)")
    public java.util.List<String> faltantesActa(@RequestBody ActaActivoService.ActaRequest r) {
        return actas.faltantesPara(r);
    }

    @GetMapping("/actas/{id}")
    public ActaActivoService.ActaVista acta(@PathVariable UUID id) {
        return actas.detalle(id);
    }

    @PostMapping("/actas/{id}/aceptar")
    @Operation(summary = "El custodio acepta el acta en el sistema")
    public ActaActivoService.ActaVista aceptar(@PathVariable UUID id, HttpServletRequest req) {
        return actas.aceptar(id, req.getRemoteAddr());
    }

    @GetMapping("/actas/{id}/pdf")
    public ResponseEntity<byte[]> actaPdf(@PathVariable UUID id) {
        ActaActivoService.ActaVista a = actas.detalle(id);
        return pdf(a.numero() + ".pdf", actas.pdf(id), false);
    }

    @PostMapping(value = "/actas/{id}/firmada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir el escaneo del acta firmada a mano")
    public ActaActivoService.ActaVista subirFirmada(@PathVariable UUID id, @RequestParam("archivo") MultipartFile archivo) {
        return actas.subirFirmada(id, archivo);
    }

    @GetMapping("/actas/{id}/firmada")
    public ResponseEntity<byte[]> firmada(@PathVariable UUID id) {
        AssetArchivoService.Descarga d = actas.firmada(id);
        return archivo(d.nombre(), d.mime(), d.datos());
    }

    // ---------------------------------------------------------------- apoyo

    private static ResponseEntity<byte[]> pdf(String nombre, byte[] datos, boolean adjunto) {
        return archivo(nombre, "application/pdf", datos, adjunto);
    }

    private static ResponseEntity<byte[]> archivo(String nombre, String mime, byte[] datos) {
        return archivo(nombre, mime, datos, false);
    }

    private static ResponseEntity<byte[]> archivo(String nombre, String mime, byte[] datos, boolean adjunto) {
        ContentDisposition cd = (adjunto ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(nombre, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(mime))
                .body(datos);
    }
}
