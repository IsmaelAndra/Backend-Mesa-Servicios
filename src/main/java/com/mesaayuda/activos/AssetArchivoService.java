package com.mesaayuda.activos;

import com.mesaayuda.config.UploadZones;
import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.service.AssetService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fotos y documentos de un activo. Se guardan en adjuntos/activos (nunca se
 * sirven como estático: solo por este servicio, que comprueba el permiso).
 * Límites: hasta {@value #MAX_FOTOS} fotos de {@value #MAX_FOTO_MB} MB (JPG, PNG,
 * WEBP) y PDF de hasta {@value #MAX_PDF_MB} MB. El tipo se comprueba por el
 * contenido, no por lo que diga el navegador.
 */
@Service
@Transactional
public class AssetArchivoService {

    public static final int MAX_FOTOS = 2;
    public static final int MAX_FOTO_MB = 5;
    public static final int MAX_PDF_MB = 10;
    private static final List<String> TIPOS = List.of("FOTO", "GARANTIA", "FACTURA", "OTRO");
    private static final Map<String, String> MIMES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp", "pdf", "application/pdf");

    private final AssetArchivoRepository archivos;
    private final AssetRepository activos;
    private final AssetService assetService;
    private final PermisosActivos permisos;
    private final UserService userService;
    private final SystemAuditService auditoria;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    public AssetArchivoService(AssetArchivoRepository archivos, AssetRepository activos, AssetService assetService,
                               PermisosActivos permisos, UserService userService, SystemAuditService auditoria) {
        this.archivos = archivos;
        this.activos = activos;
        this.assetService = assetService;
        this.permisos = permisos;
        this.userService = userService;
        this.auditoria = auditoria;
    }

    public record ArchivoVista(UUID id, String tipo, String nombre, String mime, long tamano, LocalDate vence,
                               OffsetDateTime subido) {
    }

    public record Descarga(String nombre, String mime, byte[] datos) {
    }

    @Transactional(readOnly = true)
    public List<ArchivoVista> listar(UUID assetId) {
        legible(assetId);
        return archivos.findByAssetIdAndDeletedFalseOrderByCreatedAtAsc(assetId).stream().map(AssetArchivoService::vista).toList();
    }

    /** Sube una foto (desde la cámara del teléfono también) o un PDF de garantía/factura. */
    public ArchivoVista subir(UUID assetId, String tipo, LocalDate vence, MultipartFile file) {
        User u = userService.getCurrentUser();
        permisos.exigirGestion(u);
        Asset asset = activo(assetId);
        String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(t)) {
            throw new BadRequestException("Tipo de archivo no válido: use FOTO, GARANTIA, FACTURA u OTRO");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Elija un archivo");
        }
        String nombre = file.getOriginalFilename() == null ? "archivo" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String ext = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        boolean foto = "FOTO".equals(t);
        if (foto) {
            if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                throw new BadRequestException("Las fotos van en JPG, PNG o WEBP");
            }
            if (file.getSize() > MAX_FOTO_MB * 1024L * 1024L) {
                throw new BadRequestException("Cada foto puede pesar hasta " + MAX_FOTO_MB + " MB");
            }
            if (archivos.countByAssetIdAndTipoAndDeletedFalse(assetId, "FOTO") >= MAX_FOTOS) {
                throw new BadRequestException("Un activo tiene como máximo " + MAX_FOTOS + " fotos: borre una antes");
            }
        } else {
            if (!"pdf".equals(ext)) {
                throw new BadRequestException("La garantía, la factura y los documentos van en PDF");
            }
            if (file.getSize() > MAX_PDF_MB * 1024L * 1024L) {
                throw new BadRequestException("El PDF puede pesar hasta " + MAX_PDF_MB + " MB");
            }
        }
        byte[] cabecera = cabecera(file);
        if (!coincide(ext, cabecera)) {
            throw new BadRequestException("El contenido del archivo no corresponde a su extensión (." + ext + ")");
        }
        try {
            Path dir = carpeta();
            String unico = UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), dir.resolve(unico), StandardCopyOption.REPLACE_EXISTING);
            AssetArchivo a = new AssetArchivo();
            a.setAssetId(assetId);
            a.setTipo(t);
            a.setNombre(nombre.length() > 255 ? nombre.substring(0, 255) : nombre);
            a.setRuta(unico);
            a.setMime(MIMES.get(ext));
            a.setTamano(file.getSize());
            a.setVence("GARANTIA".equals(t) ? vence : null);
            a.setSubidoPor(u.getIdUser());
            a = archivos.save(a);
            // La garantía subida con fecha de vencimiento la toma el activo (y su aviso).
            if ("GARANTIA".equals(t) && vence != null) {
                asset.setWarrantyUntilAsset(vence.atStartOfDay(ZoneId.of("America/Guayaquil")).toOffsetDateTime());
                asset.setGarantiaAvisadaAsset(null);
                activos.save(asset);
            }
            auditoria.record("ACTIVO_ARCHIVO", "Asset", assetId, u.getCorporateEmailUser(), t + ": " + a.getNombre());
            return vista(a);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar el archivo");
        }
    }

    @Transactional(readOnly = true)
    public Descarga descargar(UUID assetId, UUID archivoId) {
        legible(assetId);
        AssetArchivo a = archivos.findById(archivoId)
                .filter(x -> assetId.equals(x.getAssetId()) && !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", archivoId));
        try {
            return new Descarga(a.getNombre(), a.getMime(), Files.readAllBytes(carpeta().resolve(a.getRuta()).normalize()));
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo", "id", archivoId);
        }
    }

    public void eliminar(UUID assetId, UUID archivoId) {
        User u = userService.getCurrentUser();
        permisos.exigirGestion(u);
        AssetArchivo a = archivos.findById(archivoId).filter(x -> assetId.equals(x.getAssetId()))
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", archivoId));
        a.setDeleted(true);
        archivos.save(a);
        auditoria.record("ACTIVO_ARCHIVO_BORRADO", "Asset", assetId, u.getCorporateEmailUser(), a.getNombre());
    }

    /** Guarda el escaneo firmado de un acta en la misma zona protegida; devuelve el nombre único. */
    public String guardarActaFirmada(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Elija el escaneo del acta firmada");
        }
        String nombre = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1) : "";
        if (!List.of("pdf", "jpg", "jpeg", "png").contains(ext)) {
            throw new BadRequestException("El acta firmada va en PDF o como foto (JPG/PNG)");
        }
        if (file.getSize() > MAX_PDF_MB * 1024L * 1024L) {
            throw new BadRequestException("El archivo puede pesar hasta " + MAX_PDF_MB + " MB");
        }
        if (!coincide(ext, cabecera(file))) {
            throw new BadRequestException("El contenido del archivo no corresponde a su extensión (." + ext + ")");
        }
        try {
            String unico = "acta-" + UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), carpeta().resolve(unico), StandardCopyOption.REPLACE_EXISTING);
            return unico;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar el acta firmada");
        }
    }

    /**
     * Bytes de las fotos de un activo (como mucho {@value #MAX_FOTOS}), para el acta de
     * entrega. Sin control de permiso: lo hace quien llama (el acta ya es legible).
     * Una foto que no se pueda leer se omite.
     */
    @Transactional(readOnly = true)
    public List<byte[]> fotosDe(UUID assetId) {
        List<byte[]> fotos = new java.util.ArrayList<>();
        for (AssetArchivo a : archivos.findByAssetIdAndDeletedFalseOrderByCreatedAtAsc(assetId)) {
            if (!"FOTO".equals(a.getTipo()) || fotos.size() >= MAX_FOTOS) {
                continue;
            }
            try {
                fotos.add(Files.readAllBytes(carpeta().resolve(a.getRuta()).normalize()));
            } catch (IOException e) {
                // Sin la foto el acta igual se genera.
            }
        }
        return fotos;
    }

    public byte[] leer(String ruta) {
        try {
            return Files.readAllBytes(carpeta().resolve(ruta).normalize());
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo", "ruta", ruta);
        }
    }

    public static String mimeDe(String ruta) {
        String ext = ruta.substring(ruta.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return MIMES.getOrDefault(ext, "application/octet-stream");
    }

    // ---------------------------------------------------------------- apoyo

    private Asset activo(UUID id) {
        return activos.findById(id).filter(a -> !Boolean.TRUE.equals(a.getDeletedAsset()))
                .orElseThrow(() -> new ResourceNotFoundException("Activo", "id", id));
    }

    private void legible(UUID assetId) {
        if (!assetService.puedeVer(userService.getCurrentUser(), activo(assetId))) {
            throw new AccessDeniedException("No tiene permisos sobre este activo");
        }
    }

    private Path carpeta() throws IOException {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(UploadZones.ADJUNTOS).resolve("activos");
        Files.createDirectories(dir);
        return dir;
    }

    private static byte[] cabecera(MultipartFile f) {
        try (InputStream in = f.getInputStream()) {
            return in.readNBytes(16);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo");
        }
    }

    /** Firma de bytes de cada formato admitido. */
    static boolean coincide(String ext, byte[] b) {
        if (b == null || b.length < 4) return false;
        return switch (ext) {
            case "pdf" -> b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            case "webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private static ArchivoVista vista(AssetArchivo a) {
        return new ArchivoVista(a.getIdAssetArchivo(), a.getTipo(), a.getNombre(), a.getMime(), a.getTamano(),
                a.getVence(), a.getCreatedAt());
    }
}
