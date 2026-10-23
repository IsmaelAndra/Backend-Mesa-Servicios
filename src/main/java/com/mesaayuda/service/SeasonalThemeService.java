package com.mesaayuda.service;

import com.mesaayuda.dto.SeasonalThemeDtos.ConfigRequest;
import com.mesaayuda.dto.SeasonalThemeDtos.Tema;
import com.mesaayuda.dto.SeasonalThemeDtos.TemaActual;
import com.mesaayuda.dto.SeasonalThemeDtos.TemaRequest;
import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.SeasonalTheme;
import com.mesaayuda.entity.SeasonalThemeConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.SeasonalThemeConfigRepository;
import com.mesaayuda.repository.SeasonalThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Temas de temporada: cuál toca hoy (público, lo pide el login antes de la
 * sesión) y su configuración (solo SUPER_ADMIN).
 */
@Service
public class SeasonalThemeService {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    private final SeasonalThemeRepository repository;
    private final SeasonalThemeConfigRepository configRepository;
    private final AdminService adminService;
    private final UserService userService;

    public SeasonalThemeService(SeasonalThemeRepository repository,
                                SeasonalThemeConfigRepository configRepository,
                                AdminService adminService, UserService userService) {
        this.repository = repository;
        this.configRepository = configRepository;
        this.adminService = adminService;
        this.userService = userService;
    }

    /** Tema de hoy: el forzado si lo hay; si no, el activo cuyas fechas incluyen hoy. */
    @Transactional(readOnly = true)
    public Optional<SeasonalTheme> temaActual() {
        return temaActual(LocalDate.now(ZONA));
    }

    @Transactional(readOnly = true)
    public Optional<SeasonalTheme> temaActual(LocalDate hoy) {
        SeasonalThemeConfig config = config();
        if (!Boolean.TRUE.equals(config.getEnabledThemeConfig())) {
            return Optional.empty();
        }
        if (config.getForcedCodeTheme() != null && !config.getForcedCodeTheme().isBlank()) {
            Optional<SeasonalTheme> forzado = repository.findByCodeTheme(config.getForcedCodeTheme());
            if (forzado.isPresent()) {
                return forzado;
            }
        }
        // Si coinciden dos (Navidad y Fiestas de Quito a inicios de diciembre)
        // gana el de menos días: el más concreto.
        return repository.findAllByOrderByStartMonthThemeAscStartDayThemeAsc().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActiveTheme()))
                .filter(t -> vigente(t, hoy))
                .min(java.util.Comparator.comparingLong(t -> duracionDias(t, hoy)));
    }

    /** Si el tema está vigente ese día, teniendo en cuenta las fechas móviles. */
    static boolean vigente(SeasonalTheme t, LocalDate hoy) {
        if (REGLA_CARNAVAL.equals(t.getRuleTheme())) {
            LocalDate[] c = carnaval(hoy.getYear());
            return !hoy.isBefore(c[0]) && !hoy.isAfter(c[1]);
        }
        return vigente(t, MonthDay.from(hoy));
    }

    private static long duracionDias(SeasonalTheme t, LocalDate hoy) {
        if (REGLA_CARNAVAL.equals(t.getRuleTheme())) {
            return 4;
        }
        LocalDate inicio = LocalDate.of(2001, t.getStartMonthTheme(), Math.min(t.getStartDayTheme(), 28));
        LocalDate fin = LocalDate.of(2001, t.getEndMonthTheme(), Math.min(t.getEndDayTheme(), 28));
        long d = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        return d >= 0 ? d : d + 365;
    }

    static final String REGLA_CARNAVAL = "CARNAVAL";

    /** Sábado y martes de carnaval de un año (50 y 47 días antes de la Pascua). */
    static LocalDate[] carnaval(int anio) {
        LocalDate pascua = pascua(anio);
        return new LocalDate[] { pascua.minusDays(50), pascua.minusDays(47) };
    }

    /** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher, calendario gregoriano). */
    static LocalDate pascua(int anio) {
        int a = anio % 19, b = anio / 100, c = anio % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31, dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }

    /** Si el día cae entre inicio y fin (ambos incluidos); un fin anterior al inicio cruza el año. */
    static boolean vigente(SeasonalTheme t, MonthDay dia) {
        MonthDay inicio = MonthDay.of(t.getStartMonthTheme(), t.getStartDayTheme());
        MonthDay fin = MonthDay.of(t.getEndMonthTheme(), t.getEndDayTheme());
        if (!fin.isBefore(inicio)) {
            return !dia.isBefore(inicio) && !dia.isAfter(fin);
        }
        return !dia.isBefore(inicio) || !dia.isAfter(fin);
    }

    @Transactional(readOnly = true)
    public List<SeasonalTheme> listar() {
        return repository.findAllByOrderByStartMonthThemeAscStartDayThemeAsc();
    }

    @Transactional(readOnly = true)
    public SeasonalThemeConfig config() {
        return configRepository.findById(IdsConfiguracion.TEMAS_TEMPORADA).orElseGet(() -> {
            SeasonalThemeConfig nuevo = new SeasonalThemeConfig();
            nuevo.setUpdatedAtThemeConfig(OffsetDateTime.now());
            return nuevo;
        });
    }

    @Transactional
    public SeasonalTheme actualizar(UUID id, TemaRequest req) {
        SeasonalTheme t = obtener(id);
        t.setNameTheme(req.name().trim());
        t.setActiveTheme(req.active());
        // Un tema con fechas móviles (Carnaval) las calcula solo: no se guardan.
        if (t.getRuleTheme() == null) {
            validarFecha(req.startMonth(), req.startDay());
            validarFecha(req.endMonth(), req.endDay());
            t.setStartMonthTheme(req.startMonth());
            t.setStartDayTheme(req.startDay());
            t.setEndMonthTheme(req.endMonth());
            t.setEndDayTheme(req.endDay());
        }
        t.setGreetingTheme(req.greeting() == null || req.greeting().isBlank() ? null : req.greeting().trim());
        t.setUpdatedAtTheme(OffsetDateTime.now());
        return repository.save(t);
    }

    @Transactional
    public SeasonalThemeConfig configurar(ConfigRequest req) {
        String forzado = req.forcedCode() == null || req.forcedCode().isBlank() ? null : req.forcedCode().trim();
        if (forzado != null && repository.findByCodeTheme(forzado).isEmpty()) {
            throw new BadRequestException("No existe el tema " + forzado);
        }
        SeasonalThemeConfig c = config();
        c.setEnabledThemeConfig(req.enabled());
        c.setForcedCodeTheme(forzado);
        c.setUpdatedAtThemeConfig(OffsetDateTime.now());
        try {
            c.setUpdatedByTheme(userService.getCurrentUser().getIdUser());
        } catch (RuntimeException e) {
            c.setUpdatedByTheme(null);
        }
        return configRepository.save(c);
    }

    /** Fondo del login o logo propio del tema (mismas reglas que las imágenes de Marca). */
    @Transactional
    public SeasonalTheme subirImagen(UUID id, String tipo, MultipartFile archivo) {
        SeasonalTheme t = obtener(id);
        String prefijo = "tema_" + t.getCodeTheme().toLowerCase() + "_" + tipoImagen(tipo);
        String url = adminService.guardarImagenPublica(archivo, prefijo).get("url");
        switch (tipoImagen(tipo)) {
            case "fondo" -> t.setBackgroundUrlTheme(url);
            case "inicio" -> t.setDashboardUrlTheme(url);
            case "icono" -> t.setIconUrlTheme(url);
            default -> t.setLogoUrlTheme(url);
        }
        t.setUpdatedAtTheme(OffsetDateTime.now());
        return repository.save(t);
    }

    @Transactional
    public SeasonalTheme quitarImagen(UUID id, String tipo) {
        SeasonalTheme t = obtener(id);
        switch (tipoImagen(tipo)) {
            case "fondo" -> t.setBackgroundUrlTheme(null);
            case "inicio" -> t.setDashboardUrlTheme(null);
            case "icono" -> t.setIconUrlTheme(null);
            default -> t.setLogoUrlTheme(null);
        }
        t.setUpdatedAtTheme(OffsetDateTime.now());
        return repository.save(t);
    }

    public Tema aTema(SeasonalTheme t, java.util.function.UnaryOperator<String> url) {
        LocalDate hoy = LocalDate.now(ZONA);
        int m1 = t.getStartMonthTheme(), d1 = t.getStartDayTheme(), m2 = t.getEndMonthTheme(), d2 = t.getEndDayTheme();
        if (REGLA_CARNAVAL.equals(t.getRuleTheme())) {
            // Las fechas que se muestran son las del próximo carnaval (o el de este año si no pasó).
            LocalDate[] c = carnaval(hoy.getYear());
            if (hoy.isAfter(c[1])) {
                c = carnaval(hoy.getYear() + 1);
            }
            m1 = c[0].getMonthValue(); d1 = c[0].getDayOfMonth(); m2 = c[1].getMonthValue(); d2 = c[1].getDayOfMonth();
        }
        return new Tema(t.getIdTheme(), t.getCodeTheme(), t.getNameTheme(), Boolean.TRUE.equals(t.getActiveTheme()),
                m1, d1, m2, d2,
                t.getGreetingTheme(), url.apply(t.getBackgroundUrlTheme()), url.apply(t.getLogoUrlTheme()),
                url.apply(t.getDashboardUrlTheme()), url.apply(t.getIconUrlTheme()), vigente(t, hoy), t.getRuleTheme());
    }

    public TemaActual aActual(SeasonalTheme t, java.util.function.UnaryOperator<String> url) {
        return new TemaActual(t.getCodeTheme(), t.getNameTheme(), saludo(t.getGreetingTheme()),
                url.apply(t.getBackgroundUrlTheme()), url.apply(t.getLogoUrlTheme()),
                url.apply(t.getDashboardUrlTheme()), url.apply(t.getIconUrlTheme()));
    }

    /** Iconos normales de la app (los del emblema, servidos por el frontend). */
    static final String ICONO_192 = "/assets/icons/icon-192.png?v=2";
    static final String ICONO_512 = "/assets/icons/icon-512.png?v=2";
    static final String ICONO_ADAPTABLE = "/assets/icons/icon-maskable-512.png?v=2";

    /**
     * Manifiesto de la aplicación instalable (PWA).
     *
     * <p>Se genera aquí, y no como archivo fijo, para que el icono de la app
     * instalada siga la temporada: si el tema vigente tiene icono propio, el
     * manifiesto apunta a él. El navegador revisa el manifiesto al abrir la app
     * (más o menos una vez al día) y, al ver otro icono, le pregunta a la persona
     * si quiere actualizarlo. Al acabar la temporada vuelve el emblema normal y
     * pasa lo mismo. {@code id}, {@code start_url} y {@code scope} no cambian
     * nunca: si cambiaran, el navegador lo trataría como otra aplicación.
     *
     * @param iconoTemporada URL del icono del tema vigente, o null
     */
    public static Map<String, Object> manifiesto(String iconoTemporada) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "/");
        m.put("name", "Mesa de Servicios COAC");
        m.put("short_name", "Mesa COAC");
        m.put("description", "Sistema de mesa de ayuda y gestión de tickets.");
        m.put("lang", "es");
        m.put("dir", "ltr");
        m.put("start_url", "/");
        m.put("scope", "/");
        m.put("display", "standalone");
        m.put("background_color", "#062e86");
        m.put("theme_color", "#062e86");
        List<Map<String, String>> iconos = new java.util.ArrayList<>();
        if (iconoTemporada != null && !iconoTemporada.isBlank()) {
            String tipo = tipoMime(iconoTemporada);
            iconos.add(Map.of("src", iconoTemporada, "sizes", "512x512", "type", tipo, "purpose", "any"));
            iconos.add(Map.of("src", iconoTemporada, "sizes", "512x512", "type", tipo, "purpose", "maskable"));
        } else {
            iconos.add(Map.of("src", ICONO_192, "sizes", "192x192", "type", "image/png", "purpose", "any"));
            iconos.add(Map.of("src", ICONO_512, "sizes", "512x512", "type", "image/png", "purpose", "any"));
            iconos.add(Map.of("src", ICONO_ADAPTABLE, "sizes", "512x512", "type", "image/png", "purpose", "maskable"));
        }
        m.put("icons", iconos);
        return m;
    }

    private static String tipoMime(String url) {
        String u = url.toLowerCase(java.util.Locale.ROOT);
        if (u.contains(".webp")) return "image/webp";
        if (u.contains(".jpg") || u.contains(".jpeg")) return "image/jpeg";
        if (u.contains(".gif")) return "image/gif";
        return "image/png";
    }

    /** Año de fundación de la cooperativa (11 de noviembre de 1968). */
    static final int ANIO_FUNDACION = 1968;

    /** En el saludo, {anios} son los años que cumple la cooperativa este año. */
    static String saludo(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.replace("{anios}", String.valueOf(LocalDate.now(ZONA).getYear() - ANIO_FUNDACION));
    }

    private SeasonalTheme obtener(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tema", "id", id));
    }

    private static String tipoImagen(String tipo) {
        if ("fondo".equals(tipo) || "logo".equals(tipo) || "inicio".equals(tipo) || "icono".equals(tipo)) {
            return tipo;
        }
        throw new BadRequestException("Tipo de imagen no válido: use fondo, logo, inicio o icono.");
    }

    private static void validarFecha(int mes, int dia) {
        try {
            // 29 de febrero vale (MonthDay lo admite); 31 de abril no.
            MonthDay.of(mes, dia);
        } catch (DateTimeException e) {
            throw new BadRequestException("Fecha no válida: " + dia + "/" + mes);
        }
    }
}
