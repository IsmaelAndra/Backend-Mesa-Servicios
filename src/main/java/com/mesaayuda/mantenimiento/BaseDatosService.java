package com.mesaayuda.mantenimiento;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.MfaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Herramientas de base de datos para el SUPER_ADMIN (Administración → Base de datos):
 * descargar una copia, subir una copia (p. ej. la de producción en el ambiente de
 * pruebas) y restablecer la base a cero.
 *
 * <ul>
 *   <li>Todo pide el código de la app de autenticación ({@link MfaService#exigirCodigoObligatorio}).</li>
 *   <li>Subir y restablecer BORRAN la base: además del código, la pantalla pide
 *       escribir RESTAURAR / RESTABLECER. Funcionan en los dos ambientes.</li>
 *   <li>Usa {@code pg_dump}/{@code pg_restore} (imagen Docker: postgresql-client-16;
 *       en Windows se buscan en {@code BD_PG_BIN} o en C:\Program Files\PostgreSQL\*\bin).
 *       La contraseña va en PGPASSWORD, nunca en la línea de órdenes.</li>
 *   <li>Antes de borrar se guarda una copia de seguridad temporal: si la restauración
 *       falla, se vuelve a poner lo que había.</li>
 *   <li>Tras subir una copia se apaga el envío de correo ({@code mail_enabled=false}) y
 *       se cancela la cola pendiente: una copia de producción en pruebas no debe
 *       escribir a personas reales.</li>
 * </ul>
 */
@Service
public class BaseDatosService {

    private static final Logger log = LoggerFactory.getLogger(BaseDatosService.class);
    private static final Pattern URL_PG = Pattern.compile("^jdbc:postgresql://([^:/?]+)(?::(\\d+))?/([^?]+).*$");
    /** Un archivo de pg_dump en formato "custom" empieza por estos bytes. */
    private static final byte[] MAGIA = "PGDMP".getBytes(StandardCharsets.US_ASCII);
    private static final long TIEMPO_MAX_MIN = 30;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    private final MfaService mfaService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AreaRepository areaRepository;
    private final SystemAuditService auditoria;
    private final ArchivosCopia archivos;
    private final TransactionTemplate tx;

    private final String url;
    private final String usuario;
    private final String clave;
    private final Ambiente ambiente;
    private final String pgBin;
    /** Una sola operación a la vez. */
    private final AtomicBoolean ocupado = new AtomicBoolean(false);

    public BaseDatosService(DataSource dataSource, JdbcTemplate jdbc, Flyway flyway, MfaService mfaService,
                            UserService userService, UserRepository userRepository, RoleRepository roleRepository,
                            AreaRepository areaRepository, SystemAuditService auditoria, TransactionTemplate tx,
                            ArchivosCopia archivos,
                            @Value("${spring.datasource.url}") String url,
                            @Value("${spring.datasource.username}") String usuario,
                            @Value("${spring.datasource.password:}") String clave,
                            Ambiente ambiente,
                            @Value("${BD_PG_BIN:}") String pgBin) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.flyway = flyway;
        this.mfaService = mfaService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.areaRepository = areaRepository;
        this.auditoria = auditoria;
        this.tx = tx;
        this.archivos = archivos;
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
        this.ambiente = ambiente;
        this.pgBin = pgBin;
    }

    /** Lo que la pantalla necesita saber antes de ofrecer los botones. */
    public record Estado(String ambiente, boolean herramientas, String versionPgDump,
                         String baseDatos, String tamano, boolean autenticadorListo) {
    }

    public Estado estado() {
        User yo = userService.getCurrentUser();
        String version = null;
        try {
            version = ejecutarCorto(List.of(binario("pg_dump"), "--version")).trim();
        } catch (RuntimeException e) {
            // sin pg_dump: la pantalla lo explica
        }
        String tamano = null;
        try {
            tamano = jdbc.queryForObject("SELECT pg_size_pretty(pg_database_size(current_database()))", String.class);
        } catch (RuntimeException e) {
            // no es imprescindible
        }
        boolean autenticador = mfaService.estadoDe(yo.getIdUser())
                .map(m -> m.isEnrolled() && m.getSecretAlmacenado() != null).orElse(false);
        return new Estado(ambiente.valor(), version != null, version, conexion().base(), tamano, autenticador);
    }

    /** Nombre del archivo de la copia: mesa-servicios-<base>-<fecha>.zip (base + archivos). */
    public String nombreCopia() {
        return "mesa-servicios-" + conexion().base() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip";
    }

    /** Nombre de la base dentro del .zip. */
    static final String ENTRADA_BASE = "base-datos.dump";
    /** Límites al descomprimir una copia subida. */
    private static final long MAX_DESCOMPRIMIDO = 40L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRADAS = 500_000;

    /**
     * La copia completa: un .zip con la base ({@code base-datos.dump}, pg_dump -Fc)
     * y los archivos subidos bajo {@code archivos/} (logos, fondos, adjuntos, fotos
     * de activos, actas firmadas...). Se escribe al vuelo, sin archivo temporal.
     */
    public void descargarCompleta(OutputStream salida) throws IOException {
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(salida);
        // Las imágenes y los PDF ya van comprimidos: compresión rápida.
        zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
        zip.putNextEntry(new java.util.zip.ZipEntry(ENTRADA_BASE));
        descargar(new java.io.FilterOutputStream(zip) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() {
                // el zip sigue abierto para los archivos
            }
        });
        zip.closeEntry();
        archivos.agregarAlZip(zip);
        zip.finish();
        zip.flush();
    }

    /** Permisos de descarga de un solo uso: valor → caducidad. */
    private final Map<String, java.time.Instant> permisos = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.security.SecureRandom AZAR = new java.security.SecureRandom();

    /**
     * Comprueba el código, deja constancia y devuelve un permiso de un solo uso
     * (60 s) para bajar el archivo con un enlace normal: así el navegador lo
     * guarda directamente, sin cargar toda la base en memoria (como el ticket de SSE).
     */
    public String autorizarDescarga(String codigoMfa) {
        User yo = userService.getCurrentUser();
        mfaService.exigirCodigoObligatorio(yo, codigoMfa);
        auditoria.record("BD_DESCARGA", "BaseDatos", yo.getIdUser(), yo.getCorporateEmailUser(), conexion().base());
        java.time.Instant ahora = java.time.Instant.now();
        permisos.entrySet().removeIf(e -> e.getValue().isBefore(ahora));
        byte[] b = new byte[32];
        AZAR.nextBytes(b);
        String permiso = java.util.HexFormat.of().formatHex(b);
        permisos.put(permiso, ahora.plusSeconds(60));
        return permiso;
    }

    /** Gasta el permiso (una sola vez). */
    public void consumirPermiso(String permiso) {
        java.time.Instant caduca = permiso == null ? null : permisos.remove(permiso);
        if (caduca == null || caduca.isBefore(java.time.Instant.now())) {
            throw new org.springframework.security.access.AccessDeniedException("Permiso de descarga no válido o vencido");
        }
    }

    /** Escribe la copia (pg_dump -Fc) en la respuesta. */
    public void descargar(OutputStream salida) throws IOException {
        Process p = iniciar(List.of(binario("pg_dump"), "-Fc", "--no-owner", "--no-privileges",
                "-h", conexion().host(), "-p", conexion().puerto(), "-U", usuario, conexion().base()));
        StringBuilder errores = leerErrores(p);
        try (InputStream in = p.getInputStream()) {
            in.transferTo(salida);
        }
        esperar(p, errores, "pg_dump");
    }

    /**
     * Reemplaza la base por la copia subida. El archivo ya está en disco (temporal);
     * se borra al terminar.
     */
    /**
     * Paso previo a recibir el archivo: comprueba el código (antes de una subida
     * larga, durante la cual el código caducaría) y reserva la operación.
     * Quien llama debe terminar con {@link #restaurar} o {@link #cancelarRestauracion}.
     */
    public User prepararRestauracion(String codigoMfa) {
        return exigirRestaurar(codigoMfa);
    }

    /** La subida falló: libera la operación reservada. */
    public void cancelarRestauracion() {
        ocupado.set(false);
    }

    public void restaurar(Path copia, User yo) {
        Path trabajo = null;
        try {
            Path base = copia;
            Path carpetaArchivos = null;
            if (esZip(copia)) {
                // Copia completa (.zip): base + archivos. Se descomprime en una carpeta temporal.
                trabajo = Files.createTempDirectory("mesa-copia-");
                carpetaArchivos = descomprimir(copia, trabajo);
                base = trabajo.resolve(ENTRADA_BASE);
                if (!Files.isRegularFile(base)) {
                    throw new BadRequestException("El .zip no trae la base (" + ENTRADA_BASE + "). "
                            + "Use una copia descargada desde esta pantalla.");
                }
            }
            if (!esCopiaValida(base)) {
                throw new BadRequestException("El archivo no es una copia de la base. Use una copia descargada "
                        + "desde esta pantalla (.zip, o .dump de las versiones anteriores).");
            }
            Path baseFinal = base;
            reemplazarCon(() -> restaurarArchivo(baseFinal));
            despuesDeCambiarLaBase();
            if (carpetaArchivos != null) {
                reemplazarArchivos(carpetaArchivos);
            }
            log.warn("Base de datos RESTAURADA desde una copia por {}", yo.getCorporateEmailUser());
            auditarDespues("BD_RESTAURADA", yo.getCorporateEmailUser());
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la copia: " + e.getMessage());
        } finally {
            ocupado.set(false);
            borrar(copia);
            ArchivosCopia.borrarArbol(trabajo);
        }
    }

    /** Los archivos de la copia sustituyen a los actuales (con vuelta atrás si falla). */
    private void reemplazarArchivos(Path carpeta) {
        ArchivosCopia.Apartado antes = archivos.apartar();
        try {
            archivos.poner(carpeta);
            archivos.descartar(antes);
        } catch (IOException | RuntimeException e) {
            log.error("Fallo al poner los archivos de la copia; se devuelven los anteriores", e);
            archivos.devolver(antes);
            throw new BadRequestException("La base se restauró, pero los archivos (imágenes, adjuntos) no: "
                    + "quedaron los que había. Revise el espacio en disco y vuelva a intentarlo.");
        }
    }

    private static boolean esZip(Path archivo) {
        try (InputStream in = Files.newInputStream(archivo)) {
            byte[] cab = in.readNBytes(4);
            return cab.length == 4 && cab[0] == 'P' && cab[1] == 'K' && cab[2] == 3 && cab[3] == 4;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Descomprime la copia en {@code destino}: la base a {@code base-datos.dump} y
     * los archivos a {@code destino/archivos}. Ignora cualquier otra entrada y
     * rechaza rutas que se salgan de la carpeta. Devuelve la carpeta de archivos,
     * o null si la copia no trae la carpeta {@code archivos/} (entonces no se tocan).
     */
    private Path descomprimir(Path zipArchivo, Path destino) throws IOException {
        Path carpeta = destino.resolve("archivos");
        boolean traeArchivos = false;
        long total = 0;
        int entradas = 0;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(zipArchivo)))) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entradas > MAX_ENTRADAS) {
                    throw new BadRequestException("La copia trae demasiados archivos");
                }
                String nombre = e.getName();
                if (ENTRADA_BASE.equals(nombre)) {
                    total += ArchivosCopia.escribir(zip, destino.resolve(ENTRADA_BASE), MAX_DESCOMPRIMIDO - total);
                } else if (ArchivosCopia.PREFIJO.equals(nombre)) {
                    traeArchivos = true;
                } else if (!e.isDirectory()) {
                    Path p = ArchivosCopia.destinoSeguro(carpeta, nombre);
                    if (p != null) {
                        traeArchivos = true;
                        total += ArchivosCopia.escribir(zip, p, MAX_DESCOMPRIMIDO - total);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!traeArchivos) {
            return null;
        }
        Files.createDirectories(carpeta);
        return carpeta;
    }

    /**
     * Deja la base como recién instalada (todas las migraciones y los datos de
     * fábrica) y vuelve a crear SOLO la cuenta de quien la restablece, con su
     * contraseña de siempre. El doble factor se configura de nuevo al entrar.
     */
    public void restablecer(String codigoMfa) {
        User yo = exigirRestaurar(codigoMfa);
        try {
            String correo = yo.getCorporateEmailUser();
            String hash = yo.getPasswordUser();
            String nombre = yo.getDisplayName();
            String cedula = yo.getIdentificationUser();
            String telefono = yo.getPhoneUser();
            reemplazarCon(() -> { /* base vacía: solo migraciones */ });
            despuesDeCambiarLaBase();
            // Como recién instalada: sin imágenes de marca, adjuntos ni fotos.
            archivos.descartar(archivos.apartar());
            tx.executeWithoutResult(s -> recrearSuperAdmin(correo, hash, nombre, cedula, telefono));
            log.warn("Base de datos RESTABLECIDA a cero por {}", correo);
            auditarDespues("BD_RESTABLECIDA", correo);
        } finally {
            ocupado.set(false);
        }
    }

    // ------------------------------------------------------------------ interno

    private User exigirRestaurar(String codigoMfa) {
        User yo = userService.getCurrentUser();
        mfaService.exigirCodigoObligatorio(yo, codigoMfa);
        if (!ocupado.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay una operación sobre la base en curso. Espere a que termine.");
        }
        return yo;
    }

    /**
     * Copia de seguridad → esquema vacío → {@code llenar} → migraciones. Si algo
     * falla, se vuelve a poner la copia de seguridad.
     */
    private void reemplazarCon(Runnable llenar) {
        Path respaldo = null;
        try {
            respaldo = Files.createTempFile("mesa-respaldo-", ".dump");
            try (OutputStream out = Files.newOutputStream(respaldo)) {
                descargar(out);
            }
        } catch (IOException | RuntimeException e) {
            borrar(respaldo);
            throw new BadRequestException("No se pudo hacer la copia de seguridad previa; no se tocó la base. "
                    + motivo(e));
        }
        try {
            vaciarEsquema();
            llenar.run();
            flyway.migrate();
        } catch (RuntimeException e) {
            log.error("Fallo al reemplazar la base; se vuelve a la copia de seguridad", e);
            try {
                vaciarEsquema();
                restaurarArchivo(respaldo);
                flyway.migrate();
            } catch (RuntimeException e2) {
                log.error("NO se pudo volver a la copia de seguridad: quedó en {}", respaldo, e2);
                respaldo = null; // se conserva para recuperarla a mano
                throw new BadRequestException("Falló la operación y también la vuelta atrás. La copia previa quedó "
                        + "en el servidor; revise el registro del backend.");
            }
            throw new BadRequestException("No se pudo completar; la base quedó como estaba. " + motivo(e));
        } finally {
            borrar(respaldo);
        }
    }

    private void vaciarEsquema() {
        jdbc.execute("DROP SCHEMA IF EXISTS public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        // Las conexiones del pool guardan consultas preparadas sobre tablas que ya no existen.
        evictarConexiones();
    }

    private void restaurarArchivo(Path archivo) {
        Process p = iniciar(List.of(binario("pg_restore"), "--no-owner", "--no-privileges", "--exit-on-error",
                "-h", conexion().host(), "-p", conexion().puerto(), "-U", usuario, "-d", conexion().base(),
                archivo.toString()));
        StringBuilder errores = leerErrores(p);
        try {
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            // se ve en el código de salida
        }
        esperar(p, errores, "pg_restore");
        evictarConexiones();
    }

    /** Lo que hay que hacer siempre tras cambiar la base por otra. */
    private void despuesDeCambiarLaBase() {
        // Las sesiones que traía la copia no sirven aquí (en los dos ambientes).
        int sesiones = jdbc.update("UPDATE refresh_token SET revoked = TRUE WHERE revoked = FALSE");
        log.info("Base cambiada: {} sesiones de la copia cerradas", sesiones);
        if (!ambiente.esPruebas()) {
            // En producción la copia es la de producción: correo y demás siguen como estaban.
            evictarConexiones();
            return;
        }
        // En pruebas, lo mismo que despliegue/preparar-copia-pruebas.sql: una copia
        // de producción no debe escribir a personas reales.
        // 1. Correo apagado y cola cancelada.
        jdbc.update("UPDATE microsoft_graph_config SET mail_enabled = FALSE, enabled = FALSE");
        jdbc.update("UPDATE mail_outbox SET status_mail = 'CANCELADO' WHERE status_mail IN ('PENDIENTE', 'ENVIANDO')");
        // 2. Telefonía apagada y sin token (el puente real apunta a producción).
        jdbc.update("UPDATE telefonia_config SET enabled_telefonia = FALSE, token_hash = NULL, token_creado_at = NULL");
        jdbc.update("UPDATE llamada SET status_llamada = 'VENCIDA' WHERE status_llamada IN ('PENDIENTE', 'ENVIADA')");
        // 3. Automatizaciones desactivadas y sin ejecuciones en cola.
        jdbc.update("UPDATE workflow SET status_workflow = 'INACTIVO' WHERE status_workflow = 'ACTIVO'");
        jdbc.update("UPDATE workflow_execution SET status_execution = 'CANCELADO' "
                + "WHERE status_execution IN ('PENDIENTE', 'EJECUTANDO', 'ESPERANDO', 'APROBACION')");
        evictarConexiones();
    }

    private void recrearSuperAdmin(String correo, String hash, String nombre, String cedula, String telefono) {
        if (userRepository.existsByCorporateEmailUser(correo)) {
            return; // p. ej. el superadmin sembrado con el mismo correo
        }
        Role rol = roleRepository.findByNameRole("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Falta el rol SUPER_ADMIN tras las migraciones"));
        Area area = areaRepository.tecnologia().orElse(null);
        User u = new User();
        u.setCorporateEmailUser(correo);
        u.setPasswordUser(hash);
        u.setDisplayName(nombre);
        u.setIdentificationUser(cedula == null || cedula.isBlank() ? "1000000001" : cedula);
        u.setPhoneUser(telefono == null || telefono.isBlank() ? "0990000001" : telefono);
        u.setStatusUser("ACTIVE");
        u.setRolUser(rol);
        u.setArea(area);
        u.setMustChangePassword(false);
        u.setDeletedUser(false);
        userRepository.save(u);
    }

    private void auditarDespues(String accion, String correo) {
        try {
            auditoria.record(accion, "BaseDatos", null, correo, conexion().base());
        } catch (RuntimeException e) {
            log.warn("No se pudo auditar {}", accion, e);
        }
    }

    private boolean esCopiaValida(Path archivo) {
        try (InputStream in = Files.newInputStream(archivo)) {
            byte[] cab = in.readNBytes(MAGIA.length);
            return java.util.Arrays.equals(cab, MAGIA);
        } catch (IOException e) {
            return false;
        }
    }

    private void evictarConexiones() {
        if (dataSource instanceof HikariDataSource h && h.getHikariPoolMXBean() != null) {
            h.getHikariPoolMXBean().softEvictConnections();
        }
    }

    private record Conexion(String host, String puerto, String base) {
    }

    private Conexion conexion() {
        Matcher m = URL_PG.matcher(url == null ? "" : url);
        if (!m.matches()) {
            throw new IllegalStateException("URL de base de datos no reconocida");
        }
        return new Conexion(m.group(1), m.group(2) == null ? "5432" : m.group(2), m.group(3));
    }

    /** Ruta del ejecutable: BD_PG_BIN, el PATH o, en Windows, la instalación de PostgreSQL. */
    private String binario(String nombre) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exe = windows ? nombre + ".exe" : nombre;
        if (pgBin != null && !pgBin.isBlank()) {
            return Paths.get(pgBin, exe).toString();
        }
        if (windows) {
            Path base = Paths.get("C:\\Program Files\\PostgreSQL");
            if (Files.isDirectory(base)) {
                try (var dirs = Files.list(base)) {
                    var mejor = dirs.map(d -> d.resolve("bin").resolve(exe)).filter(Files::isRegularFile)
                            .max((a, b) -> Integer.compare(version(a), version(b)));
                    if (mejor.isPresent()) {
                        return mejor.get().toString();
                    }
                } catch (IOException e) {
                    // se intenta con el PATH
                }
            }
        }
        return exe;
    }

    private static int version(Path exe) {
        try {
            return Integer.parseInt(exe.getParent().getParent().getFileName().toString().replaceAll("\\D.*", ""));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Process iniciar(List<String> orden) {
        ProcessBuilder pb = new ProcessBuilder(orden);
        Map<String, String> env = pb.environment();
        env.put("PGPASSWORD", clave == null ? "" : clave);
        env.put("PGCONNECT_TIMEOUT", "10");
        try {
            return pb.start();
        } catch (IOException e) {
            throw new BadRequestException("No se encontró " + orden.get(0) + " en el servidor. Instale el cliente "
                    + "de PostgreSQL 16 o indique la carpeta en BD_PG_BIN.");
        }
    }

    private String ejecutarCorto(List<String> orden) {
        Process p = iniciar(orden);
        StringBuilder errores = leerErrores(p);
        try (InputStream in = p.getInputStream()) {
            String out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            esperar(p, errores, orden.get(0));
            return out;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo ejecutar " + orden.get(0));
        }
    }

    /** Lee stderr en otro hilo (si no, un proceso que escribe mucho se queda bloqueado). */
    private StringBuilder leerErrores(Process p) {
        StringBuilder sb = new StringBuilder();
        Thread t = new Thread(() -> {
            try (InputStream err = p.getErrorStream()) {
                String texto = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                synchronized (sb) {
                    sb.append(texto.length() > 4000 ? texto.substring(texto.length() - 4000) : texto);
                }
            } catch (IOException e) {
                // nada
            }
        }, "bd-stderr");
        t.setDaemon(true);
        t.start();
        return sb;
    }

    private void esperar(Process p, StringBuilder errores, String nombre) {
        try {
            if (!p.waitFor(TIEMPO_MAX_MIN, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new BadRequestException(nombre + " tardó demasiado y se detuvo.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new BadRequestException(nombre + " se interrumpió.");
        }
        if (p.exitValue() != 0) {
            String detalle;
            synchronized (errores) {
                detalle = errores.toString().trim();
            }
            // El texto de pg_* puede traer el host; va al registro, no a la pantalla.
            log.error("{} terminó con código {}: {}", nombre, p.exitValue(), detalle);
            throw new BadRequestException(nombre + " falló (código " + p.exitValue() + "). Revise el registro del backend.");
        }
    }

    private static String motivo(Exception e) {
        return e instanceof BadRequestException && e.getMessage() != null ? e.getMessage() : "";
    }

    private static void borrar(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // temporal: lo limpia el sistema
            }
        }
    }
}
