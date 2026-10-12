package com.mesaayuda.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Códigos TOTP tal como los entiende cualquier app de autenticación (RFC 6238).
 *
 * <p>Se implementa aquí en lugar de añadir una dependencia porque el algoritmo
 * son unas cuarenta líneas y, sobre todo, porque se puede comprobar contra los
 * vectores de prueba que publica la propia RFC. Una biblioteca externa daría el
 * mismo resultado, pero no se podría verificar igual de barato.
 *
 * <p>Lo que hace, resumido: convierte la hora en un número de pasos de 30
 * segundos, ese número alimenta un HMAC-SHA1 con el secreto, y los últimos cuatro
 * bytes de ese HMAC se recortan a seis dígitos. Microsoft Authenticator y
 * Passbolt hacen exactamente esto, así que el mismo código sirve en las dos.
 *
 * <p><b>Por qué importa la protección contra reuso.</b> La app muestra el mismo
 * código durante 30 segundos seguidos, y con la tolerancia de un paso hacia atrás
 * un mismo código sería válido unas dos minutos. Si alguien lo ve por encima del
 * hombro, podría escribirlo otra vez. Por eso {@link #codigoEsValido} recuerda el
 * último paso aceptado y no vuelve a admitir uno igual o anterior.
 */
@Service
public class TotpService {

    /** Longitud del código. Es lo que espera cualquier app: 6 dígitos. */
    public static final int DIGITOS = 6;

    /** Segundos por código. El estándar son 30. */
    public static final long PERIODO_SEGUNDOS = 30L;

    /**
     * Cuántos pasos se admiten por delante y por detrás.
     *
     * <p>Uno por delante y uno por detrás (30 s en total) es lo habitual: cubre el
     * desfase de reloj entre el móvil y el servidor. Dos o más abriría la puerta a
     * que un código serves para un intervalo de tres minutos.
     */
    public static final int TOLERANCIA_PASOS = 1;

    private static final String ALGORITMO = "HmacSHA1";

    /** Base32 según RFC 4648, que es como viajan los secretos en los QR. */
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Genera un secreto nuevo de 160 bits, que es lo que recommends la RFC 4226.
     *
     * <p>En Base32 porque es el formato que leen las apps; si se guardara en otro,
     * habría que traducirlo al montar el QR y sería un paso donde equivocarse.
     */
    public String generarSecreto() {
        byte[] bytes = new byte[20];
        aleatorio.nextBytes(bytes);
        return base32Codificar(bytes);
    }

    /**
     * Calcula el código que corresponde a un instante.
     *
     * <p>Se hace público a propósito: los tests lo necesitan para comprobar
     * vectores conocidos, y no tiene sentido esconder un algoritmo sin secretos.
     */
    public String calcularCodigo(String secretoBase32, long epochSeconds) {
        long paso = epochSeconds / PERIODO_SEGUNDOS;
        // El recorte dinámico da un numero de 31 bits; el codigo son los ultimos
        // DIGITOS decimales de ese numero, no una lectura de seis bytes.
        int numero = numeroDinamico(base32Decodificar(secretoBase32), paso) % 1_000_000;
        // La cuenta atrasa hasta el numero de digitos pedido, con ceros delante.
        // Un codigo que empieza por 0 es perfectamente valido: sin esto, el 012345
        // llegaria a la app como 12345 y no casaria nunca.
        String codigo = Integer.toString(numero);
        while (codigo.length() < DIGITOS) {
            codigo = "0" + codigo;
        }
        return codigo.substring(codigo.length() - DIGITOS);
    }

    /** Código para el momento actual. */
    public String calcularCodigo(String secretoBase32) {
        return calcularCodigo(secretoBase32, Instant.now().getEpochSecond());
    }

    /**
     * Comprueba un código escrito por el usuario.
     *
     * @param ultimoPasoAceptado último paso ya usado, para no admitir dos veces
     *                            el mismo; null si es el primero
     * @return el paso que corresponde al código, o -1 si no vale
     */
    public long pasoDelCodigo(String secretoBase32, String codigo, Long ultimoPasoAceptado) {
        String limpio = normalizar(codigo);
        if (limpio.length() != DIGITOS || !limpio.chars().allMatch(Character::isDigit)) {
            return -1L;
        }
        long ahora = Instant.now().getEpochSecond() / PERIODO_SEGUNDOS;
        for (long paso = ahora - TOLERANCIA_PASOS; paso <= ahora + TOLERANCIA_PASOS; paso++) {
            if (!calcularCodigo(secretoBase32, paso * PERIODO_SEGUNDOS).equals(limpio)) {
                continue;
            }
            // El codigo es correcto. Ahora, no reused: si ya se acepto este paso o
            // uno posterior, es un reintento del mismo codigo.
            if (ultimoPasoAceptado != null && paso <= ultimoPasoAceptado) {
                return -1L;
            }
            return paso;
        }
        return -1L;
    }

    public boolean codigoEsValido(String secretoBase32, String codigo, Long ultimoPasoAceptado) {
        return pasoDelCodigo(secretoBase32, codigo, ultimoPasoAceptado) >= 0;
    }

    /**
     * URI que leen las apps al escanear el QR.
     *
     * <p>Formato otpauth, el estándar de facto. El nombre de la cuenta va
     * URL-encoded porque los correos contienen arroba y punto, y sin codificar
     * algunas apps lo parten mal.
     */
    public String uriOtpAuth(String secretoBase32, String emisor, String correo) {
        StringBuilder uri = new StringBuilder("otpauth://totp/");
        uri.append(encode(issuer(correo, emisor))).append("%3A").append(encode(correo));
        uri.append("?secret=").append(secretoBase32);
        uri.append("&issuer=").append(encode(issuer(correo, emisor)));
        uri.append("&algorithm=SHA1");
        uri.append("&digits=").append(DIGITOS);
        uri.append("&period=").append(PERIODO_SEGUNDOS);
        return uri.toString();
    }

    /** Emisor que verá el usuario en su app. */
    private String issuer(String correo, String emisor) {
        if (emisor != null && !emisor.isBlank()) {
            return emisor;
        }
        // Sin emisor configurado, al menos que la cuenta sea identificable.
        return correo == null || !correo.contains("@") ? "Mesa de Servicios" : correo;
    }

    /** Quita espacios y guiones: la gente los escribe al teclear. */
    public String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return codigo.replaceAll("[\\s-]", "").trim();
    }

    private String encode(String valor) {
        return java.net.URLEncoder.encode(valor == null ? "" : valor,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Recorte dinámico de la RFC 4226: devuelve un número de 31 bits.
     *
     * <p>El desplazamiento del último nibble es lo que impide que los últimos
     * cuatro bytes del HMAC, casi iguales entre sí, produzcan códigos
     * correlacionados. El bit más alto del primer byte se pone a cero para que el
     * número quepa en un entero con signo.
     */
    private int numeroDinamico(byte[] secreto, long paso) {
        byte[] hash = hmac(paso, secreto);
        int offset = hash[hash.length - 1] & 0x0F;
        return ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
    }

    private byte[] hmac(long paso, byte[] secreto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 no disponible", e);
        }
    }

    private String base32Codificar(byte[] datos) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    private byte[] base32Decodificar(String base32) {
        String limpio = base32 == null ? "" : base32.replace("=", "").replace(" ", "")
                .toUpperCase(Locale.ROOT);
        byte[] salida = new byte[limpio.length() * 5 / 8];
        int indice = 0;
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto no valido: caracter '" + c + "'");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida[indice++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(salida, indice);
    }
}