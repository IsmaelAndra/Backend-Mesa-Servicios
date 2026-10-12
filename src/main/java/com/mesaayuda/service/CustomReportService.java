package com.mesaayuda.service;

import com.mesaayuda.entity.BrandingConfig;
import com.mesaayuda.entity.ReportConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.repository.ReportConfigRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mesaayuda.config.UploadZones;
import java.nio.file.Paths;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomReportService {

    private static final Logger log = LoggerFactory.getLogger(CustomReportService.class);

    /**
     * Un unico .jrxml para todos los reportes: el admin edita aqui la cabecera,
     * el icono y el estilo de las celdas, y las columnas se generan solas a
     * partir de la consulta SQL que definio en Reportes Personalizados.
     */
    private static final String PLANTILLA = "reports/reporte.jrxml";

    /** Keys de las celdas de ejemplo que se clonan una vez por cada columna. */
    private static final String KEY_HEADER = "TPL_HEADER";
    private static final String KEY_DETALLE = "TPL_DETAIL";

    private static final int ANCHO_PAGINA = 802;
    /** A4 vertical: 595 de ancho menos 20 de margen a cada lado. */
    private static final int ANCHO_PAGINA_VERTICAL = 555;
    private static final int MAX_COLS = 40;
    private static final int ANCHO_MIN_COL = 60;
    /** Ancho reservado para la columna de identificador, que va centrada. */
    private static final int ANCHO_COL_ID = 56;
    private static final Color DEFAULT_PRIMARY = new Color(0x06, 0x2E, 0x86);

    /** Formatos que ImageIO (el rasterizador de Jasper) si sabe leer. */
    private static final List<String> FORMATOS_RASTER = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    @Autowired
    private ReportConfigRepository reportConfigRepository;

    @Autowired
    private BrandingConfigRepository brandingConfigRepository;

    @Autowired
    private com.mesaayuda.repository.UserRepository userRepository;

    @Autowired
    private ReportSqlPolicy reportSqlPolicy;

    @Autowired
    private com.mesaayuda.security.UrlSafetyValidator urlSafetyValidator;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Plantilla propia para los reportes, con límites de tiempo y de filas.
     * No se reutiliza la general porque estos límites solo tienen sentido aquí:
     * una consulta de reporte puede ser muy lenta sobre tablas grandes y no
     * debe quedarse con una conexión del pool bloqueada, ni traer medio millón
     * de filas a memoria para renderizar un PDF.
     */
    private NamedParameterJdbcTemplate jdbcReportes;

    @Autowired
    public void initReportJdbc(DataSource dataSource,
                                @Value("${app.reports.query-timeout-seconds:15}") int timeoutSegundos,
                                @Value("${app.reports.max-rows:5000}") int maxFilas) {
        JdbcTemplate plantilla = new JdbcTemplate(dataSource);
        plantilla.setQueryTimeout(timeoutSegundos);
        plantilla.setMaxRows(maxFilas);
        this.jdbcReportes = new NamedParameterJdbcTemplate(plantilla);
    }

    @Value("${app.uploads.dir}")
    private String uploadDir;

    // ============================ CRUD ============================

    @Transactional(readOnly = true)
    public List<ReportConfig> list(boolean soloActivos) {
        return soloActivos
                ? reportConfigRepository.findByDeletedReportFalseAndActiveReportTrueOrderByTitleReportAsc()
                : reportConfigRepository.findByDeletedReportFalseOrderByTitleReportAsc();
    }

    @Transactional
    public ReportConfig save(UUID id, String name, String title, String description, String querySql, Boolean active) {
        return save(id, name, title, description, querySql, active, null, null);
    }

    @Transactional
    public ReportConfig save(UUID id, String name, String title, String description, String querySql,
                             Boolean active, String icon, String color) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("El nombre del reporte es obligatorio");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("El título del reporte es obligatorio");
        }
        if (querySql == null || querySql.isBlank()) {
            throw new BadRequestException("La consulta SQL es obligatoria");
        }
        validateSql(querySql);

        ReportConfig cfg = (id != null)
                ? reportConfigRepository.findById(id)
                        .filter(r -> !r.getDeletedReport())
                        .orElseThrow(() -> new ResourceNotFoundException("Reporte", "id", id))
                : new ReportConfig();

        boolean nuevo = cfg.getIdReport() == null;
        if (nuevo && reportConfigRepository.existsByNameReport(name.trim())) {
            throw new BadRequestException("Ya existe un reporte con ese nombre: " + name);
        }
        if (!nuevo && reportConfigRepository.existsByNameReportAndIdReportNot(name.trim(), id)) {
            throw new BadRequestException("Ya existe un reporte con ese nombre: " + name);
        }

        cfg.setNameReport(name.trim());
        cfg.setTitleReport(title.trim());
        cfg.setDescriptionReport(description == null || description.isBlank() ? null : description.trim());
        cfg.setQuerySql(querySql.trim());
        if (active != null) cfg.setActiveReport(active);
        if (icon != null) cfg.setIconReport(icon.isBlank() ? null : icon.trim());
        if (color != null) cfg.setColorReport(color.isBlank() ? null : color.trim());
        cfg.setDeletedReport(false);
        return reportConfigRepository.save(cfg);
    }

    @Transactional
    public void delete(UUID id) {
        ReportConfig cfg = reportConfigRepository.findById(id)
                .filter(r -> !r.getDeletedReport())
                .orElseThrow(() -> new ResourceNotFoundException("Reporte", "id", id));
        cfg.setDeletedReport(true);
        reportConfigRepository.save(cfg);
    }

    // ============================ GENERACIÓN ============================
    // Las tres variantes van en transacción de solo lectura. El SQL del reporte lo
    // escribe un administrador y ReportSqlPolicy ya rechaza todo lo que no sea una
    // lectura de las tablas permitidas; esta es la segunda barrera: aunque algo se
    // le escapara a la política, PostgreSQL rechaza cualquier escritura dentro de
    // una transacción READ ONLY.

    @Transactional(readOnly = true)
    public byte[] generate(UUID id, String format, LocalDate from, LocalDate to, UUID typeId, UUID currentUserId) {
        return generate(id, format, from, to, typeId, currentUserId, "PDF");
    }

    /**
     * @param orientacion "H" para hoja apaisada (más columnas) o "V" para vertical.
     */
@Transactional(readOnly = true)
public byte[] generate(UUID id, String format, LocalDate from, LocalDate to, UUID typeId, UUID currentUserId,
              String orientacion) {
          return generate(id, format, from, to, typeId, currentUserId, orientacion, null);
      }

      /**
       * @param orientacion "H" para hoja apaisada (más columnas) o "V" para vertical.
       * @param sucursalId  sede por la que se acota el reporte; null son todas.
       */
      @Transactional(readOnly = true)
      public byte[] generate(UUID id, String format, LocalDate from, LocalDate to, UUID typeId, UUID currentUserId,
              String orientacion, UUID sucursalId) {
        ReportConfig cfg = reportConfigRepository.findById(id)
                .filter(r -> !r.getDeletedReport())
                .filter(r -> Boolean.TRUE.equals(r.getActiveReport()))
                .orElseThrow(() -> new ResourceNotFoundException("Reporte", "id", id));

        String nombreUsuario = null;
        if (currentUserId != null) {
            nombreUsuario = userRepository.findById(currentUserId)
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName()
                            : u.getCorporateEmailUser())
                    .orElse(null);
        }

// Los cinco filtros se envían siempre, nulos si no se elegieron, porque las
          // consultas los usan como "(:desde IS NULL OR ...)". Si se omitieran, Spring
          // fallaría al no encontrar el valor en el mapa.
          MapSqlParameterSource sqlParams = new MapSqlParameterSource()
                  .addValue("desde", from == null ? null : from.atStartOfDay().atOffset(ZoneOffset.UTC),
                          Types.TIMESTAMP_WITH_TIMEZONE)
                  .addValue("hasta", to == null ? null : to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                          Types.TIMESTAMP_WITH_TIMEZONE)
                  .addValue("tipoId", typeId, Types.OTHER)
                  .addValue("usuarioId", currentUserId, Types.OTHER)
                  .addValue("sucursalId", sucursalId, Types.OTHER);

        List<Map<String, Object>> raw;
        try {
            // Se revalida al ejecutar, no solo al guardar: el SQL está en la base
            // y pudo escribirse antes de que existiera esta política.
            reportSqlPolicy.validar(cfg.getQuerySql());
            // Con cast explícito en los parámetros: sin él, un reporte sin
            // rango de fechas falla porque PostgreSQL no logra deducir el tipo
            // de un parámetro nulo en (:desde IS NULL OR col >= :desde).
            raw = jdbcReportes.queryForList(
                    reportSqlPolicy.conCastosExplicitos(cfg.getQuerySql()), sqlParams);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error ejecutando consulta del reporte {}", cfg.getNameReport(), e);
            // El motivo de PostgreSQL (nombres de tabla o de columna, sintaxis)
            // no se le devuelve al cliente: solo se registra aquí.
            throw new BadRequestException("No se pudo ejecutar la consulta del reporte. "
                    + "Revisa la consulta o contacta al administrador.");
        }

        return render(cfg, normalize(raw), format, nombreUsuario, esVertical(orientacion));
    }

    /** Pasa los resultados a mapas de cadenas con claves de campo seguras (fc0, fc1, ...). */
    private Map<String, Object> normalize(List<Map<String, Object>> raw) {
        List<String> headers = new ArrayList<>();
        List<Map<String, ?>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String h = e.getKey();
                if (!headers.contains(h)) headers.add(h);
                int idx = headers.indexOf(h);
                Object v = e.getValue();
                row.put("fc" + idx, v == null ? "" : v.toString());
            }
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("headers", headers);
        out.put("rows", rows);
        return out;
    }

    private byte[] render(ReportConfig cfg, Map<String, Object> normalized, String format, String usuario,
            boolean vertical) {
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) normalized.get("headers");
        @SuppressWarnings("unchecked")
        List<Map<String, ?>> rows = (List<Map<String, ?>>) normalized.get("rows");

        if (headers.isEmpty()) {
            headers = List.of("Sin datos");
        }

        InputStream logo = loadLogoInputStream();
        try {
            JasperReport report = JasperCompileManager.compileReport(construirDiseno(headers, vertical));

            Map<String, Object> params = new HashMap<>();
            params.put("TITULO", cfg.getTitleReport());
            params.put("FECHA", LocalDate.now().toString());
            params.put("TOTAL", rows.size());
            params.put("LOGO", logo);
            params.put("USUARIO", usuario == null || usuario.isBlank() ? "Sistema" : usuario);

            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(rows);
            JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);

            if ("EXCEL".equalsIgnoreCase(format) || "XLSX".equalsIgnoreCase(format)) {
                return exportXlsx(print);
            }
            if ("HTML".equalsIgnoreCase(format)) {
                ByteArrayOutputStream htmlOut = new ByteArrayOutputStream();
                net.sf.jasperreports.engine.export.HtmlExporter exporter =
                        new net.sf.jasperreports.engine.export.HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new net.sf.jasperreports.export.SimpleHtmlExporterOutput(htmlOut));
                exporter.exportReport();
                return htmlOut.toByteArray();
            }
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            log.error("Error generando reporte personalizado {}", cfg.getNameReport(), e);
            throw new BadRequestException("No se pudo generar el reporte: " + e.getMessage());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado en reporte personalizado {}", cfg.getNameReport(), e);
            throw new BadRequestException("No se pudo generar el reporte");
        } finally {
            if (logo != null) {
                try {
                    logo.close();
                } catch (Exception ignored) {
                    // el stream ya se liberó con el cierre del reporte
                }
            }
        }
    }

    /**
     * Reparte todo el ancho útil entre las columnas para que la tabla llegue de
     * margen a margen. La primera, si es un identificador, se deja angosta para que
     * quepan más columnas; el sobrante se reparte entre las demás.
     */
    private int[] anchoColumna(List<String> headers, int n, int anchoUtil) {
        boolean primeraEsId = esColumnaId(headers.get(0));
        int anchoId = Math.min(ANCHO_COL_ID, anchoUtil / 3);
        int primeraNormal = primeraEsId ? 1 : 0;
        int restantes = n - primeraNormal;
        if (restantes <= 0) {
            return new int[] { anchoUtil };
        }

        // El ancho de la columna de ID se reserva antes de repartir, o si no la
        // tabla se pasa del margen derecho.
        int disponible = anchoUtil - (primeraEsId ? anchoId : 0);

        int[] anchos = new int[n];
        int usado = primeraEsId ? anchoId : 0;
        for (int i = primeraNormal; i < n; i++) {
            anchos[i] = disponible / restantes;
            usado += anchos[i];
        }
        int sobrante = anchoUtil - usado;
        for (int i = primeraNormal; i < n && sobrante > 0; i++) {
            anchos[i] += 1;
            sobrante--;
        }
        if (primeraEsId) {
            anchos[0] = anchoId;
        }
        return anchos;
    }

    /** "V" o "VERTICAL" pide la hoja en vertical; cualquier otro valor la deja apaisada. */
    private boolean esVertical(String orientacion) {
        if (orientacion == null) {
            return false;
        }
        String o = orientacion.trim().toUpperCase(Locale.ROOT);
        return o.equals("V") || o.equals("VERTICAL") || o.equals("PORTRAIT");
    }

    /** Reacomoda los textos de la cabecera y el resumen al cambiar el ancho de hoja. */
    private void ajustarAnchoTitulo(JasperDesign design, int anchoUtil) {
        int inicioTexto = Math.min(290, Math.max(0, anchoUtil - 312));
        int anchoTexto = Math.max(80, anchoUtil - inicioTexto);
        for (JRElement e : elementsOf(design.getTitle())) {
            var el = (JRDesignElement) e;
            if (el.getX() >= 250) {
                el.setX(inicioTexto);
                el.setWidth(anchoTexto);
            }
        }
        for (JRElement e : elementsOf(design.getSummary())) {
            var el = (JRDesignElement) e;
            el.setWidth(anchoUtil);
        }
    }

    /** Cabeceras que contienen un identificador numérico y no necesitan mucho ancho. */
    private boolean esColumnaId(String header) {
        if (header == null) {
            return false;
        }
        String h = header.trim().toLowerCase(Locale.ROOT);
        return h.equals("id") || h.equals("#") || h.equals("ticket") || h.equals("codigo")
                || h.equals("código") || h.equals("evaluacion") || h.equals("evaluación")
                || h.equals("no") || h.equals("nro") || h.equals("numero") || h.equals("número");
    }

    private JasperDesign construirDiseno(List<String> headers, boolean vertical) throws Exception {
        int n = headers.size();
        if (n > MAX_COLS) {
            throw new BadRequestException(
                    "El reporte tiene " + n + " columnas; el máximo permitido es " + MAX_COLS);
        }
        // La hoja puede ir apaisada (muchas columnas) o vertical (pocas y anchas).
        int anchoUtil = vertical ? ANCHO_PAGINA_VERTICAL : ANCHO_PAGINA;
        int[] anchos = anchoColumna(headers, n, anchoUtil);

        JasperDesign design;
        var resource = new ClassPathResource(PLANTILLA);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Plantilla", "reporte", "reporte.jrxml");
        }
        try (InputStream in = resource.getInputStream()) {
            design = JRXmlLoader.load(in);
        }

        if (vertical) {
            design.setOrientation(OrientationEnum.PORTRAIT);
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setColumnWidth(anchoUtil);
            ajustarAnchoTitulo(design, anchoUtil);
        }

        // Un campo por columna; el contenido de la tabla viaja en el mapa de cada fila.
        for (int i = 0; i < n; i++) {
            JRDesignField campo = new JRDesignField();
            campo.setName("fc" + i);
            campo.setValueClassName("java.lang.String");
            design.addField(campo);
        }

        JRDesignBand bandaHeader = null;
        JRDesignStaticText celdaHeader = null;
        JRDesignBand bandaDetalle = null;
        JRDesignTextField celdaDetalle = null;
        for (JRBand banda : design.getAllBands()) {
            if (!(banda instanceof JRDesignBand grupo)) {
                continue;
            }
            for (JRElement elemento : grupo.getElements()) {
                String key = ((JRDesignElement) elemento).getKey();
                if (KEY_HEADER.equals(key) && celdaHeader == null) {
                    bandaHeader = grupo;
                    celdaHeader = (JRDesignStaticText) elemento;
                } else if (KEY_DETALLE.equals(key) && celdaDetalle == null) {
                    bandaDetalle = grupo;
                    celdaDetalle = (JRDesignTextField) elemento;
                }
            }
        }
        if (celdaHeader == null || celdaDetalle == null) {
            throw new BadRequestException(
                    "La plantilla " + PLANTILLA + " debe tener una celda con key=\"" + KEY_HEADER
                            + "\" y otra con key=\"" + KEY_DETALLE + "\"");
        }

        boolean centrarPrimera = esColumnaId(headers.get(0));
        int x = 0;
        for (int i = 0; i < n; i++) {
            int ancho = anchos[i];

            var cabecera = (JRDesignStaticText) celdaHeader.clone();
            cabecera.setKey(null);
            cabecera.setX(x);
            cabecera.setWidth(ancho);
            cabecera.setText(headers.get(i));
            if (i == 0 && centrarPrimera) {
                cabecera.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            }
            bandaHeader.addElement(cabecera);

            var valor = (JRDesignTextField) celdaDetalle.clone();
            valor.setKey(null);
            valor.setX(x);
            valor.setWidth(ancho);
            valor.setExpression(new JRDesignExpression("$F{fc" + i + "}"));
            if (i == 0 && centrarPrimera) {
                valor.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            }
            bandaDetalle.addElement(valor);

            x += ancho;
        }

        // Las celdas de ejemplo se quitan: solo existen para clonar su estilo.
        bandaHeader.removeElement(celdaHeader);
        bandaDetalle.removeElement(celdaDetalle);

        aplicarColorMarca(design, loadPrimaryColor());
        return design;
    }

    /**
     * Pinta con el color primario de la marca el título, el resumen y el fondo de
     * las cabeceras. El .jrxml trae un color literal porque JasperReports trata
     * backcolor/forecolor como valores fijos, no como expresiones.
     */
    private void aplicarColorMarca(JasperDesign design, Color color) {
        if (design == null || color == null) {
            return;
        }
        for (JRElement e : elementsOf(design.getTitle())) {
            ((JRDesignElement) e).setForecolor(color);
        }
        for (JRElement e : elementsOf(design.getSummary())) {
            ((JRDesignElement) e).setForecolor(color);
        }
        for (JRElement e : elementsOf(design.getColumnHeader())) {
            var elemento = (JRDesignElement) e;
            elemento.setMode(ModeEnum.OPAQUE);
            elemento.setBackcolor(color);
            elemento.setForecolor(Color.WHITE);
        }
    }

    private JRElement[] elementsOf(JRBand band) {
        if (band == null || band.getElements() == null) {
            return new JRElement[0];
        }
        return band.getElements();
    }

    private byte[] exportXlsx(JasperPrint print) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    /**
     * Imagen de la cabecera: el icono de reportes de la marca y, si no hay, el logo.
     * JasperReports no dibuja SVG, asi que un formato no raster se descarta en vez
     * de romper la generación del PDF completo.
     */
    private InputStream loadLogoInputStream() {
        try {
            BrandingConfig cfg = brandingConfigRepository.findTopByOrderByIdBrandingAsc().orElse(null);
            if (cfg == null) {
                return null;
            }
            InputStream logo = abrirImagen(cfg.getReportLogoUrl());
            return logo != null ? logo : abrirImagen(cfg.getLogoUrl());
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo para el reporte: {}", e.getMessage());
            return null;
        }
    }

    private InputStream abrirImagen(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            byte[] bytes;
            if (path.startsWith("http")) {
                // Esta URL la escribe un administrador y la descarga el servidor.
                // Sin comprobarla, apuntarla a la red interna (por ejemplo los
                // metadatos de la nube) convertiría cada reporte en una vía de
                // salida, porque la respuesta acabaría dentro del PDF.
                if (!urlSafetyValidator.esUrlPublica(path)) {
                    log.warn("Se omite la imagen del reporte: la URL no apunta a internet.");
                    return null;
                }
                try (InputStream in = new java.net.URL(path).openStream()) {
                    bytes = in.readAllBytes();
                }
            } else {
                int query = path.indexOf('?');
                String clean = query >= 0 ? path.substring(0, query) : path;
                if (!FORMATOS_RASTER.contains(extensionOf(clean))) {
                    log.warn("Formato no soportado por JasperReports para la imagen del reporte: {}", clean);
                    return null;
                }
                // La URL guardada apunta a la zona publica, que es donde AdminService deja
                // los logos de informe: /uploads/publico/logo.png sobre app.uploads.dir.
                // Las marcas ya guardadas antes del reparto de zonas tienen "/uploads/logo.png",
                // sin el subdirectorio, y se resuelven en el mismo sitio.
                Path base = Paths.get(uploadDir).toAbsolutePath().normalize()
                        .resolve(UploadZones.PUBLICO);
                Path file;
                if (clean.startsWith("/uploads/" + UploadZones.PUBLICO + "/")) {
                    file = base.resolve(clean.substring(("/uploads/" + UploadZones.PUBLICO + "/").length()));
                } else if (clean.startsWith("/uploads/")) {
                    file = base.resolve(clean.substring("/uploads/".length()));
                } else {
                    file = Paths.get(clean).toAbsolutePath().normalize();
                }
                bytes = Files.readAllBytes(file);
            }
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            log.warn("No se pudo cargar la imagen del reporte {}: {}", path, e.getMessage());
            return null;
        }
    }

    private String extensionOf(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    /** Color primario de la marca, usado en el título y en las cabeceras de la tabla. */
    private Color loadPrimaryColor() {
        try {
            BrandingConfig cfg = brandingConfigRepository.findTopByOrderByIdBrandingAsc().orElse(null);
            return toColor(cfg == null ? null : cfg.getPrimaryColor());
        } catch (Exception e) {
            log.warn("No se pudo leer el color primario para el reporte: {}", e.getMessage());
            return DEFAULT_PRIMARY;
        }
    }

    /** Normaliza a #RRGGBB y lo devuelve como Color; cualquier valor inválido cae en el azul corporativo. */
    private Color toColor(String value) {
        if (value == null) return DEFAULT_PRIMARY;
        String hex = value.trim();
        if (hex.isEmpty()) return DEFAULT_PRIMARY;
        if (!hex.startsWith("#")) hex = "#" + hex;
        if (hex.length() == 4) {
            StringBuilder sb = new StringBuilder("#");
            for (int i = 1; i < 4; i++) {
                sb.append(hex.charAt(i)).append(hex.charAt(i));
            }
            hex = sb.toString();
        }
        if (!hex.matches("#[0-9a-fA-F]{6}")) return DEFAULT_PRIMARY;
        return Color.decode(hex);
    }

    /**
     * Valida que la consulta solo pueda leer las tablas y columnas autorizadas.
     * La política vive en ReportSqlPolicy; aquí solo se delega.
     */
    private void validateSql(String sql) {
        reportSqlPolicy.validar(sql);
    }
}

