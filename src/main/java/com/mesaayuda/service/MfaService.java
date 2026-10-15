package com.mesaayuda.service;

import com.mesaayuda.dto.MfaChallengeResponse;
import com.mesaayuda.dto.MfaSettingResponse;
import com.mesaayuda.entity.MfaChallenge;
import com.mesaayuda.entity.MfaRecoveryCode;
import com.mesaayuda.entity.MfaSetting;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.UserMfa;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.MfaChallengeRepository;
import com.mesaayuda.repository.MfaRecoveryCodeRepository;
import com.mesaayuda.repository.MfaSettingRepository;
import com.mesaayuda.repository.UserMfaRepository;
import com.mesaayuda.security.TotpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble factor por código temporal (TOTP).
 *
 * <p>El flujo tiene dos caminos y es importante no confundirlos:
 *
 * <ol>
 *   <li><b>Alta.</b> El usuario entra bien con su contraseña pero no tiene app.
 *       Se le devuelve un QR y una clave. Hasta que no teclee un código válido no
 *       queda enrolado, y hasta entonces no tiene sesión: el segundo factor no
 *       puede ser algo que se pueda saltarse.
 *   <li><b>Verificación.</b> Ya está enrolado y teclea el código de su app.
 * </ol>
 *
 * <p>En los dos casos el login NO devuelve tokens. Devuelve un desafío de un solo
 * uso, y los tokens salen de {@link AuthService} solo cuando el código cuadra.
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    /**
     * Vida del desafío.
     *
     * <p>Corta a propósito. Un desafío es una contraseña a medio usar: durante su
     * vigencia, quien lo tenga puede completar el login sin volver a saber la
     * clave. Cinco minutos dan de sobra para mirar el móvil.
     */
    private static final long DESAFIO_MINUTOS = 5;

    /** Cuántos códigos de recuperación se generan en el alta. */
    private static final int CODIGOS_RECUPERACION = 10;

    private static final String ALFABETO_RECUPERACION = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Autowired private MfaSettingRepository settingRepository;
    @Autowired private UserMfaRepository userMfaRepository;
    @Autowired private MfaChallengeRepository challengeRepository;
    @Autowired private MfaRecoveryCodeRepository recoveryCodeRepository;
    @Autowired private TotpService totp;
    @Autowired private SecretCryptoService secretCrypto;
    @Autowired private RateLimitService rateLimitService;
    @Autowired private SystemAuditService systemAuditService;
    @Autowired private PasswordEncoder passwordEncoder;

    private final SecureRandom aleatorio = new SecureRandom();

    // ============================ interruptor ============================

    /**
     * Estado global. Si la fila no existe, el MFA está apagado.
     *
     * <p>Se devuelve sin exceptionar porque esta comprobación está en el camino
     * del login: si la tabla se leyera con un Optional vacío al arrancar sin
     * semilla, todos los usuarios se quedarían fuera sin poder entrar.
     */
    @Transactional(readOnly = true)
    public boolean activo() {
        return settingRepository.findById(MfaSetting.UNICO)
                .map(MfaSetting::isMfaEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public MfaSettingResponse estado(UUID superAdminId) {
        MfaSetting s = ajuste(superAdminId);
        return new MfaSettingResponse(s.isMfaEnabled(), s.getIssuerMfa(),
                userMfaRepository.countByEnrolledTrue(),
                superAdminId != null
                        && userMfaRepository.findByUser_IdUser(superAdminId)
                        .map(UserMfa::isEnrolled).orElse(false));
    }

    /**
     * Activa o desactiva el doble factor.
     *
     * <p>Al desactivar no se borra nada: los secretos siguen guardados y cifrados.
     * Si mañana se vuelve a activar, quien ya estaba enrolado sigue entrando con
     * su app sin volver a escanear nada. Borrarlos obligaría a reconfigurar a
     * todo el mundo por una decisión administrativa, y eso solo pasa de Pedir la
     * contraseña a tener que pedir el teléfono.
     */
    @Transactional
    public MfaSettingResponse cambiar(boolean habilitado, String emisor, UUID superAdminId) {
        MfaSetting s = ajuste(superAdminId);
        boolean antes = s.isMfaEnabled();
        s.setMfaEnabled(habilitado);
        if (emisor != null && !emisor.isBlank()) {
            s.setIssuerMfa(emisor.trim());
        }
        s.setUpdatedAtMfa(OffsetDateTime.now());
        s.setUpdatedByMfa(superAdminId);
        settingRepository.save(s);

        systemAuditService.record("MFA_SETTING", "MfaSetting", MfaSetting.UNICO, null,
                "Doble factor " + (habilitado ? "ACTIVADO" : "DESACTIVADO")
                        + (antes != habilitado ? "" : " (sin cambio de estado)"));
log.info("Doble factor {} por el usuario {}", habilitado ? "activado" : "desactivado",
                  superAdminId);

          if (habilitado) {
              // Aviso, no error: activar es lo correcto aunque el super
              // administrador aún no tenga app. Lo que no se puede es que se entere
              // después, cuando ya no pueda entrar, porque su propio acceso
              // exigirá el código en la siguiente pantalla.
              MfaSettingResponse tras = estado(superAdminId);
              if (!tras.superAdminEnrolled()) {
                  log.warn("El doble factor se ha activado y la cuenta del super administrador "
                          + "aún no tiene app configurada: tendrá que enrolarse en el próximo acceso.");
              }
              return tras;
          }
          return estado(superAdminId);
      }

    private MfaSetting ajuste(UUID superAdminId) {
        return settingRepository.findById(MfaSetting.UNICO).orElseGet(() -> {
            // Se crea la fila si falta, para que una instalación sin data.sql
            // pueda activar el MFA desde la pantalla sin tocar la base a mano.
            MfaSetting nuevo = new MfaSetting();
            nuevo.setId(MfaSetting.UNICO);
            nuevo.setIssuerMfa("Mesa de Servicios");
            nuevo.setMfaEnabled(false);
            nuevo.setUpdatedAtMfa(OffsetDateTime.now());
            nuevo.setUpdatedByMfa(superAdminId);
            return settingRepository.save(nuevo);
        });
    }

    // ============================ paso 1: login ============================

    /**
     * Prepara lo que el login tiene que devolver en lugar de tokens.
     *
     * @return null si el usuario no necesita segundo factor
     */
    @Transactional
    public MfaChallengeResponse prepararDesafio(User user) {
        if (!activo()) {
            return null;
        }
// Aquí antes había una excepción para el super administrador. Se quitó: una
        // cuenta con más poder que ninguna es exactamente la que más importa
        // proteger, y dejarla con un solo factor convertía al super administrador
        // en el punto débil de toda la instalación.
        //
        // El riesgo que la excepción cubría era quedar encerrado fuera de la
        // organización. Se cubre por otro lado: los códigos de recuperación se
        // generan para cualquier cuenta, incluido el super administrador, y cada
        // alta los muestra una única vez. Además desactivar el doble factor exige
        // contraseña Y un código vigente, así que ni siquiera con los códigos
        // quemados se puede dejar la cuenta en un factor sin darme cuenta.
        UserMfa mfa = registro(user);
        // Al pedir un desafío nuevo se invalidan los que quedaran vivos.
        challengeRepository.invalidarVivos(user.getIdUser(), OffsetDateTime.now());

        if (!mfa.isEnrolled()) {
            return prepararAlta(user, mfa);
        }

        String token = nuevoDesafio(user, MfaChallenge.PROPOSITO_LOGIN);
        return new MfaChallengeResponse(MfaChallengeResponse.STATUS_VERIFICAR, token,
                null, null, emisor(), null);
    }

    /**
     * Genera el secreto y el QR de un alta en curso.
     *
     * <p>El secreto va directo a {@code secretPendiente}, no al definitivo: hasta
     * que haya un código correcto, esa entrada de la app del usuario es
     *rometheus y confirmarla como buena dejaría una cuenta medio configurada que
     * nadie puede recuperar.
     */
    private MfaChallengeResponse prepararAlta(User user, UserMfa mfa) {
        // Si ya hay un alta a medias reciente se reutiliza su secreto. Antes cada
        // login generaba uno nuevo: quien escaneaba el QR, volvía atrás (o tecleaba
        // mal y repetía el acceso) y escaneaba otra vez acababa con dos cuentas en
        // el Authenticator, una de ellas inservible.
        String secreto = secretoPendienteVigente(mfa);
        if (secreto == null) {
            secreto = totp.generarSecreto();
            mfa.setSecretPendiente(secretCrypto.encrypt(secreto));
            mfa.setCreadoAtUserMfa(OffsetDateTime.now());
        }
        mfa.setSecretAlmacenado(null);
        mfa.setEnrolled(false);
        mfa.setUltimoContador(null);
        userMfaRepository.save(mfa);

        String token = nuevoDesafio(user, MfaChallenge.PROPOSITO_ENROLL);
        return new MfaChallengeResponse(MfaChallengeResponse.STATUS_ENROLAR, token,
                totp.uriOtpAuth(secreto, emisor(), user.getCorporateEmailUser()),
                // Se agrupa en bloques de cuatro: son 32 caracteres y se dictan
                // mucho mejor así que en una sola tirada.
                formatear(secreto),
                emisor(), null);
    }

    private String nuevoDesafio(User user, String purpose) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        MfaChallenge c = new MfaChallenge();
        c.setUser(user);
        c.setTokenHash(hash(token));
        c.setPurpose(purpose);
        c.setExpiresAt(OffsetDateTime.now().plusMinutes(DESAFIO_MINUTOS));
        c.setCreatedAt(OffsetDateTime.now());
        challengeRepository.save(c);
        return token;
    }

    // ============================ paso 2: confirmar ============================

    /**
     * Valida el código y devuelve los códigos de recuperación si acaba de enrolarse.
     *
     * @return códigos de recuperación recién generados, o lista vacía si ya estaba
     *         enrolado
     */
    @Transactional
    public List<String> confirmar(String tokenDesafio, String codigo, String codigoRecuperacion) {
        MfaChallenge desafio = validarDesafio(tokenDesafio);
        User user = desafio.getUser();
        // El desafío admite reintentos (un código mal tecleado no debe obligar a
        // repetir el login), así que sin cupo se podía recorrer el millón de
        // códigos posibles mientras el desafío siguiera vivo.
        rateLimitService.exigir("mfa", String.valueOf(user.getIdUser()),
                RateLimitService.MAX_MFA, RateLimitService.VENTANA_MFA);

if (MfaChallenge.PROPOSITO_ENROLL.equals(desafio.getPurpose())) {
            return completarAlta(user, codigo, desafio);
        }
        return verificarLogin(user, codigo, codigoRecuperacion, desafio);
    }

    private List<String> completarAlta(User user, String codigo, MfaChallenge desafio) {
        UserMfa mfa = registro(user);
        String pendiente = mfa.getSecretPendiente();
        if (pendiente == null) {
            throw new BadRequestException(
                    "El alta ya no está disponible. Vuelva a iniciar sesión para empezar de nuevo.");
        }
        String secreto = secretCrypto.decrypt(pendiente);

        // En el alta se admite cualquier código válido, sin comprobar reutilización:
        // es el primero y el usuario puede teclear uno que la app ya haya rotado.
        if (!totp.codigoEsValido(secreto, codigo, null)) {
            systemAuditService.record("MFA_ENROLL_FAILED", "User", user.getIdUser());
            contarFalloMfa(user);
            throw new BadRequestException("El código no es correcto. Revise la hora del móvil e inténtelo otra vez.");
        }

        // Del pendiente al definitivo: ahora sí.
        mfa.setSecretAlmacenado(pendiente);
        mfa.setSecretPendiente(null);
        mfa.setEnrolled(true);
        mfa.setVerificadoAtUserMfa(OffsetDateTime.now());
        userMfaRepository.save(mfa);

List<String> recuperacion = generarCodigosRecuperacion(user);

        // El desafío se consume también en el alta. Sin esto, el mismo QR que
        // confirmó el alta podría volver a confirmar otro alta con un código
        // nuevo: quien lo interceptara podría reutilizar el desafío para
        // reconfigurar el doble factor de la cuenta.
        consumir(desafio);

        systemAuditService.record("MFA_ENROLLED", "User", user.getIdUser());
        log.info("Doble factor enrolado para el usuario {}", user.getIdUser());
        return recuperacion;
    }

    private List<String> verificarLogin(User user, String codigo, String codigoRecuperacion,
                                         MfaChallenge desafio) {
        UserMfa mfa = registro(user);
        if (!mfa.isEnrolled() || mfa.getSecretAlmacenado() == null) {
            throw new BadRequestException("El doble factor de esta cuenta no está configurado.");
        }

        // Un intento con los dos a la vez no debe pasar. Se comprueba antes de
        // consumir el desafío para que el usuario pueda reintentar con el correcto.
        boolean conCodigo = codigo != null && !codigo.isBlank();
        boolean conRecuperacion = codigoRecuperacion != null && !codigoRecuperacion.isBlank();
        if (conCodigo && conRecuperacion) {
            throw new BadRequestException("Envíe el código de la app o un código de recuperación, no los dos.");
        }

        if (conCodigo) {
            String secreto = secretCrypto.decrypt(mfa.getSecretAlmacenado());
            long paso = totp.pasoDelCodigo(secreto, codigo, mfa.getUltimoContador());
            if (paso < 0) {
                systemAuditService.record("MFA_FAILED", "User", user.getIdUser());
                contarFalloMfa(user);
                throw new BadRequestException("El código no es correcto o ya ha caducado.");
            }
            mfa.setUltimoContador(paso);
            mfa.setVerificadoAtUserMfa(OffsetDateTime.now());
            userMfaRepository.save(mfa);
        } else if (conRecuperacion) {
            if (!usarCodigoRecuperacion(user, codigoRecuperacion)) {
                systemAuditService.record("MFA_RECOVERY_FAILED", "User", user.getIdUser());
                contarFalloMfa(user);
                throw new BadRequestException("El código de recuperación no es válido o ya se usó.");
            }
            log.info("El usuario {} entró con un código de recuperación", user.getIdUser());
        } else {
            throw new BadRequestException("Introduzca el código de seis dígitos.");
        }

// El desafío se consume aquí, no al principio: si el código fuera
        // equivocado, el usuario necesita otro intento con el mismo desafío.
        consumir(desafio);
        systemAuditService.record("MFA_VERIFIED", "User", user.getIdUser());
        return List.of();
    }

    /** Suma un fallo al cupo de doble factor del usuario, en su propia transacción. */
    private void contarFalloMfa(User user) {
        rateLimitService.registrarAparte("mfa", String.valueOf(user.getIdUser()),
                RateLimitService.MAX_MFA, RateLimitService.VENTANA_MFA);
    }

    /** Marca un desafío como gastado. Un desafío, un uso. */
    private void consumir(MfaChallenge desafio) {
        desafio.setUsedAt(OffsetDateTime.now());
        challengeRepository.save(desafio);
    }

    private MfaChallenge validarDesafio(String tokenDesafio) {
        String limpio = tokenDesafio == null ? "" : tokenDesafio.trim();
        if (limpio.isEmpty()) {
            throw new BadRequestException("El desafío no es válido o ha caducado. Inicie sesión otra vez.");
        }
        MfaChallenge c = challengeRepository.findByTokenHash(hash(limpio))
                .orElseThrow(() -> new BadRequestException(
                        "El desafío no es válido o ha caducado. Inicie sesión otra vez."));
        if (c.getUsedAt() != null) {
            throw new BadRequestException("El desafío ya se usó. Inicie sesión otra vez.");
        }
        if (c.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("El desafío ha caducado. Inicie sesión otra vez.");
        }
        return c;
    }

    // ============================ códigos de recuperación ============================

    private List<String> generarCodigosRecuperacion(User user) {
        recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(user.getIdUser())
                .forEach(c -> recoveryCodeRepository.delete(c));

List<String> enClaro = new ArrayList<>();
        for (int i = 0; i < CODIGOS_RECUPERACION; i++) {
            // El codigo se genera SIN guion y se compara sin guion. El guion solo
            // se pone al mostrarlo. Al revés se rompia: se guardaba el hash de
            // "ABCD3-FGHI5" y se buscaba el hash de "ABCD3FGHI5", asi que ningun
            // codigo de recuperacion servia para nada.
            String codigo = generarCodigoRecuperacion();
            MfaRecoveryCode r = new MfaRecoveryCode();
            r.setUser(user);
            r.setCodeHash(hash(codigo));
            r.setCreadoAtMfa(OffsetDateTime.now());
            recoveryCodeRepository.save(r);
            enClaro.add(mostrar(codigo));
        }
        return enClaro;
    }

    /** Inserta el guion del medio solo para que se lea mejor al teclear. */
    private String mostrar(String codigo) {
        return codigo.length() > 5 ? codigo.substring(0, 5) + "-" + codigo.substring(5) : codigo;
    }

    /**
     * Genera nuevos códigos de recuperación, invalidando los anteriores.
     *
     * <p>Se usa cuando el usuario cree que le han visto los viejos. Al
     * regenerarlos, los que ya tenía dejan de servir.
     *
     * @param cuerpo lo que llegue en el POST; no se usa para validar nada. La
     *               contraseña se pide como campo aparte del JSON porque el
     *               endpoint acepta cuerpo vacío y no conviene que un cliente
     *               mal formado se quede sin hacer nada.
     */
    @Transactional
    public List<String> regenerar(User user, String cuerpo) {
        UserMfa mfa = registro(user);
        if (!mfa.isEnrolled()) {
            throw new BadRequestException("Primero debe completar el alta del doble factor.");
        }
        List<String> nuevos = generarCodigosRecuperacion(user);
        systemAuditService.record("MFA_RECOVERY_REGENERATED", "User", user.getIdUser());
        log.info("Códigos de recuperación regenerados para el usuario {}", user.getIdUser());
        return nuevos;
    }

    /**
     * Quita el doble factor a una cuenta.
     *
     * <p>Exige las dos pruebas: la contraseña y un código válido de la app. Con
     * una sola bastaría: quien se siente delante de un equipo ya desbloqueado
     * podría quitarse el segundo factor y dejar la cuenta en un solo factor, que
     * es justo lo que se quiere evitar.
     */
    @Transactional
    public void desactivar(User user, String password, String codigo) {
        UserMfa mfa = registro(user);
        if (!mfa.isEnrolled() || mfa.getSecretAlmacenado() == null) {
            throw new BadRequestException("Esta cuenta no tiene doble factor activado.");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordUser())) {
            systemAuditService.record("MFA_DISABLE_FAILED", "User", user.getIdUser());
            throw new BadRequestException("La contraseña es incorrecta.");
        }
        String secreto = secretCrypto.decrypt(mfa.getSecretAlmacenado());
        if (!totp.codigoEsValido(secreto, codigo, mfa.getUltimoContador())) {
            systemAuditService.record("MFA_DISABLE_FAILED", "User", user.getIdUser());
            throw new BadRequestException("El código no es correcto o ya ha caducado.");
        }

        // Se borran el secreto y los códigos de recuperación: si no, un token de
        // sesión robado con el que alguien entró podría recuperar el acceso a la
        // app de autenticación de una cuenta que ya no lo tiene.
        mfa.setSecretAlmacenado(null);
        mfa.setSecretPendiente(null);
        mfa.setEnrolled(false);
        mfa.setUltimoContador(null);
        userMfaRepository.save(mfa);
        recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(user.getIdUser())
                .forEach(recoveryCodeRepository::delete);

        systemAuditService.record("MFA_DISABLED", "User", user.getIdUser());
        log.info("Doble factor desactivado para el usuario {}", user.getIdUser());
    }

    @Transactional(readOnly = true)
    public long codigosRecuperacionRestantes(UUID userId) {
        return recoveryCodeRepository.countByUser_IdUserAndUsadoAtIsNull(userId);
    }

    private boolean usarCodigoRecuperacion(User user, String introducido) {
        String limpio = introducido.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        String buscado = hash(limpio);
        for (MfaRecoveryCode r : recoveryCodeRepository.findByUser_IdUserAndUsadoAtIsNull(user.getIdUser())) {
            if (MessageDigest.isEqual(r.getCodeHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    buscado.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                r.setUsadoAt(OffsetDateTime.now());
                recoveryCodeRepository.save(r);
                return true;
            }
        }
        return false;
    }

    /**
     * Código de recuperación: diez caracteres sin vocales ni letras parecidas.
     *
     * <p>Se evita la O y el 0, y la I y el 1, porque estos códigos se dictan por
     * teléfono con frecuencia y la ambigüedad los convierte en un modo de fallo.
     */
private String generarCodigoRecuperacion() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            out.append(ALFABETO_RECUPERACION.charAt(aleatorio.nextInt(ALFABETO_RECUPERACION.length())));
        }
        return out.toString();
    }

    // ============================ utilidades ============================

    @Transactional
    public UserMfa registro(User user) {
        return userMfaRepository.findByUser_IdUser(user.getIdUser()).orElseGet(() -> {
            UserMfa nuevo = new UserMfa();
            nuevo.setUser(user);
            nuevo.setCreadoAtUserMfa(OffsetDateTime.now());
            nuevo.setDesactivableUsuario(true);
            return userMfaRepository.save(nuevo);
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserMfa> estadoDe(UUID userId) {
        return userMfaRepository.findByUser_IdUser(userId);
    }

private String emisor() {
        return settingRepository.findById(MfaSetting.UNICO)
                .map(MfaSetting::getIssuerMfa)
                .filter(s -> s != null && !s.isBlank())
                .orElse("Mesa de Servicios");
    }

    /** Horas durante las que un alta a medias conserva su QR. */
    private static final long HORAS_ALTA_PENDIENTE = 24;

    /**
     * Secreto del alta a medias si sigue vigente, o null para generar uno nuevo.
     * Pasado el plazo se renueva: un QR que lleva días a la vista (capturas,
     * pantallas compartidas) ya no debería servir.
     */
    private String secretoPendienteVigente(UserMfa mfa) {
        if (mfa.isEnrolled() || mfa.getSecretPendiente() == null || mfa.getCreadoAtUserMfa() == null
                || mfa.getCreadoAtUserMfa().isBefore(OffsetDateTime.now().minusHours(HORAS_ALTA_PENDIENTE))) {
            return null;
        }
        try {
            return secretCrypto.decrypt(mfa.getSecretPendiente());
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el secreto pendiente del usuario; se genera uno nuevo");
            return null;
        }
    }

    private String formatear(String secreto) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < secreto.length(); i += 4) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(secreto, i, Math.min(i + 4, secreto.length()));
        }
        return out.toString();
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}