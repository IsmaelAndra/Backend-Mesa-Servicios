package com.mesaayuda.service;

import com.mesaayuda.config.UploadZones;
import com.mesaayuda.entity.BrandingConfig;
import com.mesaayuda.repository.BrandingConfigRepository;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.engine.export.HtmlExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reporte se arma desde reports/reporte.jrxml: la cabecera y las celdas son
 * plantillas que el servicio clona segun las columnas de la consulta. Estos tests
 * cubren ese camino y la eleccion de la imagen de la cabecera.
 */
class CustomReportServiceTest {

    private static final String PLANTILLA = "reports/reporte.jrxml";

    @TempDir
    Path uploadDir;

    private CustomReportService service;
    private BrandingConfigRepository brandingConfigRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Los logos de marca viven en la zona publica de uploads, separada de los
        // adjuntos privados de ticket. Por eso el subdirectorio se crea aqui:
        // antes los archivos estaban planos en la raiz de uploads.
        Files.createDirectories(uploadDir.resolve(UploadZones.PUBLICO));
        service = new CustomReportService();
        brandingConfigRepository = Mockito.mock(BrandingConfigRepository.class);
        setField("brandingConfigRepository", brandingConfigRepository);
        setField("uploadDir", uploadDir.toString());
    }

    /** Escribe un archivo de marca en la zona publica y devuelve su URL. */
    private String brandingFile(String nombre, byte[] contenido) throws Exception {
        Files.write(uploadDir.resolve(UploadZones.PUBLICO).resolve(nombre), contenido);
        return "/uploads/" + UploadZones.PUBLICO + "/" + nombre;
    }

    private void setField(String name, Object value) {
        try {
            var field = CustomReportService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== COLOR DE LA MARCA ====================

    @Test
    void loadPrimaryColor_usaElColorDeMarca() throws Exception {
        assertEquals(new Color(0x0D, 0x94, 0x88), loadPrimaryColorFrom("#0d9488"));
    }

    @Test
    void loadPrimaryColor_normalizaValoresNoValidos() throws Exception {
        Color azul = new Color(0x06, 0x2E, 0x86);
        assertEquals(azul, loadPrimaryColorFrom("no-es-un-color"));
        assertEquals(azul, loadPrimaryColorFrom(""));
        assertEquals(new Color(0xAA, 0xBB, 0xCC), loadPrimaryColorFrom("aabbcc"));
    }

    private Color loadPrimaryColorFrom(String value) throws Exception {
        Method m = CustomReportService.class.getDeclaredMethod("loadPrimaryColor");
        m.setAccessible(true);
        BrandingConfig branding = new BrandingConfig();
        branding.setPrimaryColor(value);
        Mockito.when(brandingConfigRepository.findTopByOrderByIdBrandingAsc())
                .thenReturn(Optional.of(branding));
        return (Color) m.invoke(service);
    }

    // ==================== PLANTILLA ====================

    /** Corre el mismo camino que el servicio: plantilla editable + marca + compilación. */
    private JasperDesign diseno(List<String> headers) throws Exception {
        return diseno(headers, false);
    }

    private JasperDesign diseno(List<String> headers, boolean vertical) throws Exception {
        Method m = CustomReportService.class.getDeclaredMethod("construirDiseno", List.class, boolean.class);
        m.setAccessible(true);
        return (JasperDesign) m.invoke(service, headers, vertical);
    }

    private JasperReport compilar(List<String> headers) throws Exception {
        return JasperCompileManager.compileReport(diseno(headers));
    }

    private List<Map<String, ?>> filas(int columnas, int registros) {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (int r = 0; r < registros; r++) {
            Map<String, Object> fila = new HashMap<>();
            for (int c = 0; c < columnas; c++) {
                fila.put("fc" + c, "v" + c + "-" + r);
            }
            filas.add(fila);
        }
        return filas;
    }

    private JasperPrint llenar(JasperReport report, int columnas, int registros, InputStream logo)
            throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("TITULO", "Detalle de tickets");
        params.put("FECHA", "2026-09-25");
        params.put("TOTAL", registros);
        params.put("USUARIO", "Ana Pérez");
        params.put("LOGO", logo);
        return JasperFillManager.fillReport(report, params,
                new JRMapCollectionDataSource(filas(columnas, registros)));
    }

    @Test
    void plantilla_generaUnaCeldaPorCadaColumnaDeLaConsulta() throws Exception {
        JasperDesign design = diseno(List.of("Ticket", "Área", "Prioridad"));
        JasperReport report = JasperCompileManager.compileReport(design);
        assertEquals("reporte", report.getName());

        JasperPrint print = llenar(report, 3, 1, null);
        String texto = print.getPages().get(0).getElements().stream()
                .filter(e -> e instanceof net.sf.jasperreports.engine.JRPrintText)
                .map(e -> ((net.sf.jasperreports.engine.JRPrintText) e).getFullText())
                .reduce("", (a, b) -> a + " | " + b);

        assertTrue(texto.contains("Ticket"), "debe imprimir la cabecera de la primera columna: " + texto);
        assertTrue(texto.contains("Prioridad"), "debe imprimir la última cabecera: " + texto);
        assertTrue(texto.contains("v0-0"), "debe imprimir el valor de la primera columna: " + texto);
        assertTrue(texto.contains("v2-0"), "debe imprimir el valor de la última columna: " + texto);
    }

    @Test
    void plantilla_exportaAPdfValido() throws Exception {
        JasperReport report = compilar(List.of("Ticket", "Área"));
        JasperPrint print = llenar(report, 2, 2, null);

        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertTrue(pdf.length > 1000, "el PDF debe tener contenido");
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }

    @Test
    void plantilla_lasCabecerasSalenConElColorDeLaMarcaYCentradas() throws Exception {
        BrandingConfig branding = new BrandingConfig();
        branding.setPrimaryColor("#0d9488");
        Mockito.when(brandingConfigRepository.findTopByOrderByIdBrandingAsc())
                .thenReturn(Optional.of(branding));

        JasperDesign design = diseno(List.of("Ticket", "Área"));
        JasperReport report = JasperCompileManager.compileReport(design);
        JasperPrint print = llenar(report, 2, 1, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HtmlExporter exporter = new HtmlExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new net.sf.jasperreports.export.SimpleHtmlExporterOutput(out));
        exporter.exportReport();
        String html = out.toString("UTF-8").toLowerCase(Locale.ROOT);

        assertTrue(html.contains("0d9488"), "el fondo de las cabeceras debe ser el color de la marca");
        assertTrue(html.contains("center"), "las cabeceras deben quedar centradas");
    }

    @Test
    void plantilla_rechazaConsultasConDemasiadasColumnas() {
        List<String> muchas = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            muchas.add("Columna " + i);
        }
        Exception e = assertThrowsEjecutando(() -> diseno(muchas));
        assertTrue(String.valueOf(e.getMessage()).contains("columnas"),
                "debe avisar que hay demasiadas columnas: " + e.getMessage());
    }

    /** La invocacion reflexiva envuelve la excepcion original en InvocationTargetException. */
    private Exception assertThrowsEjecutando(ThrowingRunnable r) {
        try {
            r.run();
        } catch (java.lang.reflect.InvocationTargetException e) {
            return (Exception) e.getCause();
        } catch (Exception e) {
            return e;
        }
        throw new AssertionError("se esperaba una excepcion");
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void plantilla_elTituloYElSubtituloVienenDeLosParametros() throws Exception {
        JasperReport report = compilar(List.of("Ticket"));
        JasperPrint print = llenar(report, 1, 5, null);

        String texto = print.getPages().get(0).getElements().stream()
                .filter(e -> e instanceof net.sf.jasperreports.engine.JRPrintText)
                .map(e -> ((net.sf.jasperreports.engine.JRPrintText) e).getFullText())
                .reduce("", (a, b) -> a + " | " + b);

        assertTrue(texto.contains("Detalle de tickets"), "el titulo debe venir del parametro: " + texto);
        assertTrue(texto.contains("Generado: 2026-09-25"), "el subtitulo debe traer la fecha: " + texto);
        assertTrue(texto.contains("Descargado por: Ana Pérez"), "debe decir quien lo descargo: " + texto);
        assertFalse(texto.contains("Registros:"),
                "el total ya va en el resumen, no debe repetirse arriba: " + texto);
    }

    @Test
    void plantilla_laOrientacionVerticalUsaLaHojaA4Vertical() throws Exception {
        JasperDesign vertical = diseno(List.of("Agente", "Tickets", "Pendientes"), true);
        assertEquals(Integer.valueOf(595), vertical.getPageWidth(), "debe quedar en vertical");
        assertEquals(Integer.valueOf(842), vertical.getPageHeight());
        assertEquals(555, vertical.getColumnWidth(),
                "el ancho util descuenta los margenes: 595 - 20 - 20");

        JasperReport report = JasperCompileManager.compileReport(vertical);
        JasperPrint print = llenar(report, 3, 2, null);
        assertTrue(JasperExportManager.exportReportToPdf(print).length > 1000);
    }

    @Test
    void plantilla_lasColumnasLlenanLaHojaSinHuecos() throws Exception {
        JasperDesign design = diseno(List.of("Ticket", "Titulo", "Area", "Prioridad", "Estado"), false);
        JasperReport report = JasperCompileManager.compileReport(design);

        var banda = (net.sf.jasperreports.engine.design.JRDesignBand) design.getColumnHeader();
        int suma = 0;
        int esperadoX = 0;
        for (net.sf.jasperreports.engine.JRElement e : banda.getElements()) {
            var el = (net.sf.jasperreports.engine.design.JRDesignElement) e;
            assertEquals(esperadoX, el.getX(), "las columnas deben ser contiguas, sin huecos");
            suma += el.getWidth();
            esperadoX += el.getWidth();
        }
        assertEquals(802, suma, "la tabla debe ocupar toda la hoja de margen a margen");
        assertEquals("reporte", report.getName());
    }

    @Test
    void plantilla_elTotalYaNoSeRepiteArriba() throws Exception {
        JasperReport report = compilar(List.of("Ticket"));
        JasperPrint print = llenar(report, 1, 7, null);

        String texto = print.getPages().get(0).getElements().stream()
                .filter(e -> e instanceof net.sf.jasperreports.engine.JRPrintText)
                .map(e -> ((net.sf.jasperreports.engine.JRPrintText) e).getFullText())
                .reduce("", (a, b) -> a + " | " + b);

        assertFalse(texto.contains("Registros:"), "el total ya va en el resumen: " + texto);
        assertTrue(texto.contains("Descargado por:"), "debe indicar quien lo descargo: " + texto);
    }

    // ==================== IMAGEN DE LA CABECERA ====================

    private InputStream loadLogoInputStream() throws Exception {
        Method m = CustomReportService.class.getDeclaredMethod("loadLogoInputStream");
        m.setAccessible(true);
        return (InputStream) m.invoke(service);
    }

    private void branding(String reportLogoUrl, String logoUrl) {
        BrandingConfig branding = new BrandingConfig();
        branding.setReportLogoUrl(reportLogoUrl);
        branding.setLogoUrl(logoUrl);
        branding.setPrimaryColor("#062e86");
        Mockito.when(brandingConfigRepository.findTopByOrderByIdBrandingAsc())
                .thenReturn(Optional.of(branding));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void logo_usaElIconoDeReportesDeLaMarca() throws Exception {
        String url = brandingFile("report_logo_1.png", pngBytes());
        branding(url, brandingFile("logo_1.png", pngBytes()));

        try (InputStream in = loadLogoInputStream()) {
            assertNotNull(in, "debe encontrar el icono de reportes");
            assertEquals(pngBytes().length, in.readAllBytes().length);
        }
    }

    @Test
    void logo_caeAlLogoDeLaMarcaSiNoHayIconoDeReportes() throws Exception {
        branding(null, brandingFile("logo_1.png", pngBytes()));

        try (InputStream in = loadLogoInputStream()) {
            assertNotNull(in, "sin icono de reportes debe usarse el logo");
            assertTrue(in.readAllBytes().length > 0);
        }
    }

    @Test
    void logo_descartaSvgPorqueJasperNoLoDibuja() throws Exception {
        String svg = brandingFile("system_icon_1.svg", "<svg/>".getBytes());
        branding(svg, brandingFile("logo_1.png", pngBytes()));

        try (InputStream in = loadLogoInputStream()) {
            assertNotNull(in, "un SVG no debe usarse: debe caer al logo PNG");
            assertEquals(pngBytes().length, in.readAllBytes().length);
        }
    }

    @Test
    void logo_aceptaLaUrlViejaSinLaZonaPublica() throws Exception {
        // Marcas ya guardadas antes del reparto de zonas apuntan a /uploads/x.png.
        // Esos registros no se pueden reescribir a mano uno por uno, asi que la
        // URL antigua debe seguir resolviendose, ahora contra la zona publica.
        Files.write(uploadDir.resolve(UploadZones.PUBLICO).resolve("logo_viejo.png"), pngBytes());
        branding("/uploads/logo_viejo.png", null);

        try (InputStream in = loadLogoInputStream()) {
            assertNotNull(in, "la URL anterior sin /publico debe seguir funcionando");
        }
    }

    @Test
    void logo_devuelveNullSiNoHayNingunaImagen() throws Exception {
        branding(null, null);
        assertNull(loadLogoInputStream());
    }

    @Test
    void plantilla_elIconoSeOmiteCuandoNoHayLogo() throws Exception {
        JasperReport report = compilar(List.of("Ticket"));
        assertDoesNotThrow(() -> llenar(report, 1, 1, null),
                "sin logo la banda del icono debe omitirse sin romper el PDF");
        assertDoesNotThrow(() -> llenar(report, 1, 1, new ByteArrayInputStream(pngBytes())),
                "con logo la cabecera debe imprimirse sin problema");
    }

    @Test
    void celdas_lasFechasYLosBooleanosSeLeenComoPersonas() {
        java.sql.Timestamp t = java.sql.Timestamp.from(java.time.Instant.parse("2026-09-30T14:05:00Z"));
        assertEquals("30/09/2026 09:05", CustomReportService.formatear(t), "en hora de Ecuador, sin milisegundos");
        assertEquals("Sí", CustomReportService.formatear(Boolean.TRUE));
        assertEquals("12.5", CustomReportService.formatear(new java.math.BigDecimal("12.500")));
        assertEquals("", CustomReportService.formatear(null));
    }

    @Test
    void columnas_elAnchoSigueAlContenido() {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            Map<String, Object> f = new HashMap<>();
            f.put("fc0", String.valueOf(r));
            f.put("fc1", "Descripcion bastante larga del problema numero " + r);
            f.put("fc2", "BAJA");
            filas.add(f);
        }
        int[] anchos = service.anchoColumna(List.of("Ticket", "Descripción", "Prioridad"), filas, 3, 802);
        assertEquals(802, anchos[0] + anchos[1] + anchos[2], "de margen a margen");
        assertTrue(anchos[1] > anchos[2] && anchos[2] > anchos[0], "la descripción, la más ancha; el número, la más angosta");
    }
}
