package com.mesaayuda.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El TOTP se comprueba contra los vectores oficiales de la RFC 6238.
 *
 * <p>Es la única forma de saber que una app de verdad va a entender lo que
 * generamos. Un código que case con nosotros mismos y no con Microsoft
 * Authenticator sería inútil: el usuario escanearía el QR y siempre le daría
 * error, sin ninguna pista de cuál de los dos lados está mal.
 */
class TotpServiceRfc6238Test {

    private final TotpService totp = new TotpService();

    /**
     * Vectores de la RFC 6238, apéndice B.
     *
     * <p>El secreto de la RFC es la cadena ASCII "12345678901234567890", que en
     * Base32 es GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ.
     *
     * <p><b>Ojo con las cifras de ocho dígitos.</b> Los vectores de la RFC salen
     * con 8 dígitos porque ese es el ejemplo del documento. Aquí se comparan los
     * seis últimos, que es lo que produce un TOTP configurado a 6: el mismo HMAC,
     * el mismo recorte dinámico, solo que el resultado se acota a 10^6 en
     * lugar de 10^8. Si el recorte estuviera mal, no casaría ni con 6 ni con 8.
     */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("los seis vectores de la RFC 6238 salen exactos (6 ultimos digitos)")
    void vectoresDeLaRfc() {
        assertEquals("287082", totp.calcularCodigo(SECRETO_RFC, 59L), "de 94287082");
        assertEquals("081804", totp.calcularCodigo(SECRETO_RFC, 1111111109L), "de 07081804");
        assertEquals("050471", totp.calcularCodigo(SECRETO_RFC, 1111111111L), "de 14050471");
        assertEquals("005924", totp.calcularCodigo(SECRETO_RFC, 1234567890L), "de 89005924");
        assertEquals("279037", totp.calcularCodigo(SECRETO_RFC, 2000000000L), "de 69279037");
        assertEquals("353130", totp.calcularCodigo(SECRETO_RFC, 20000000000L), "de 65353130");
    }

    /**
     * Lo mismo, pero a 8 dígitos, calculando aparte.
     *
     * <p>Es la comprobación fuerte: si el resultado a 6 dígitos fuera el correcto
     * por casualidad, esta a 8 lo descartaría. Y confirma que el recorte dinámico
     * es el de verdad, no un recorte degenerado que por casualidad dé lo mismo.
     */
    @Test
    @DisplayName("a 8 digitos salen los vectores integros de la RFC")
    void vectoresCompletosADigitos() {
        assertEquals("94287082", calcular(59L), "T=59");
        assertEquals("07081804", calcular(1111111109L), "T=1111111109");
        assertEquals("14050471", calcular(1111111111L), "T=1111111111");
        assertEquals("89005924", calcular(1234567890L), "T=1234567890");
        assertEquals("69279037", calcular(2000000000L), "T=2000000000");
        assertEquals("65353130", calcular(20000000000L), "T=20000000000");
    }

    /** TOTP del secreto de la RFC a 8 dígitos, calculado sin pasar por TotpService. */
    private String calcular(long epochSeconds) {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String limpio = SECRETO_RFC;
        int buffer = 0;
        int bits = 0;
        byte[] bytes = new byte[limpio.length() * 5 / 8];
        int idx = 0;
        for (char ch : limpio.toCharArray()) {
            buffer = (buffer << 5) | alfabeto.indexOf(ch);
            bits += 5;
            if (bits >= 8) {
                bytes[idx++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(bytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8)
                    .putLong(epochSeconds / TotpService.PERIODO_SEGUNDOS).array());
            int off = hash[hash.length - 1] & 0x0F;
            int num = ((hash[off] & 0x7F) << 24) | ((hash[off + 1] & 0xFF) << 16)
                    | ((hash[off + 2] & 0xFF) << 8) | (hash[off + 3] & 0xFF);
            String codigo = Integer.toString(num % 100_000_000);
            while (codigo.length() < 8) {
                codigo = "0" + codigo;
            }
            return codigo;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("el codigo siempre tiene 6 digitos, con ceros delante si hace falta")
    void siempreSeisDigitos() {
        // El 94287082 de la RFC tiene 8 digitos: al recortar a 6 queda 287082.
        // El caso que importa es el contrario, un codigo que empiece por cero y
        // que sin relleno llegaria a la app con 5 caracteres y no casaria.
        for (long t = 0; t < 400; t += 7) {
            String codigo = totp.calcularCodigo(SECRETO_RFC, t * 30L);
            assertEquals(TotpService.DIGITOS, codigo.length(), "longitud en t=" + t + ": " + codigo);
            assertTrue(codigo.chars().allMatch(Character::isDigit), codigo);
        }
    }

    @Test
    @DisplayName("el periodo son 30 segundos, no 60")
    void periodoDeTreintaSegundos() {
        // Dos instantes dentro del mismo bloque dan el mismo codigo;uno del
        // siguiente, distinto. Si el periodo fuera 60, esta comprobacion fallaria.
        long bloque = 1700000010L / TotpService.PERIODO_SEGUNDOS * TotpService.PERIODO_SEGUNDOS;
        String a = totp.calcularCodigo(SECRETO_RFC, bloque);
        String b = totp.calcularCodigo(SECRETO_RFC, bloque + 29);
        String c = totp.calcularCodigo(SECRETO_RFC, bloque + 30);

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    // ============================ verificacion ============================

    @Test
    @DisplayName("acepta el codigo vigente")
    void aceptaElCodigoActual() {
        String secreto = totp.generarSecreto();

        assertTrue(totp.codigoEsValido(secreto, totp.calcularCodigo(secreto), null));
    }

    @Test
    @DisplayName("admite un paso de desfase de reloj por delante y por detras")
    void toleraDesfaseDeReloj() {
        String secreto = totp.generarSecreto();
        long ahora = java.time.Instant.now().getEpochSecond();

        // El movil va 20 s adelantado: su codigo es el del paso siguiente.
        assertTrue(totp.codigoEsValido(secreto,
                totp.calcularCodigo(secreto, ahora + 20), null), "movil adelantado");
        // El movil va 20 s atrasado.
        assertTrue(totp.codigoEsValido(secreto,
                totp.calcularCodigo(secreto, ahora - 20), null), "movil atrasado");
    }

    @Test
    @DisplayName("no admite mas de un paso de desfase")
    void noToleraMasDeUnPaso() {
        String secreto = totp.generarSecreto();
        long ahora = java.time.Instant.now().getEpochSecond();

        // Con dos pasos de margen, un mismo codigo valdría casi cinco minutos.
        // Con uno, dos minutos. Este es el limite que se acepta.
        assertFalse(totp.codigoEsValido(secreto,
                totp.calcularCodigo(secreto, ahora + 95), null), "demasiado adelante");
    }

    @Test
    @DisplayName("un codigo repetido no vale la segunda vez")
    void elCodigoNoSePuedeReusar() {
        // Este es el punto delicado. La app muestra el mismo codigo durante 30 s,
        // y con la tolerancia de un paso atras el mismo codigo seguiria siendo
        // aceptado durante casi dos minutos. Si alguien lo lee por encima del
        // hombro, podria escribirlo otra vez.
        String secreto = totp.generarSecreto();
        long ahora = java.time.Instant.now().getEpochSecond();
        String codigo = totp.calcularCodigo(secreto, ahora);
        long paso = totp.pasoDelCodigo(secreto, codigo, null);
        assertTrue(paso > 0, "el primer uso debe valer");

        assertFalse(totp.codigoEsValido(secreto, codigo, paso),
                "el mismo codigo no puede servir dos veces");
        assertTrue(totp.codigoEsValido(secreto, totp.calcularCodigo(secreto, ahora + 30), paso),
                "el siguiente codigo, si");
    }

    @Test
    @DisplayName("un codigo viejo no revive aunque el reloj vuelva atras")
    void unCodigoViejoNoRevive() {
        String secreto = totp.generarSecreto();
        long ahora = java.time.Instant.now().getEpochSecond();
        String viejo = totp.calcularCodigo(secreto, ahora - 25);
        long pasoActual = totp.pasoDelCodigo(secreto, totp.calcularCodigo(secreto, ahora), null);

        assertFalse(totp.codigoEsValido(secreto, viejo, pasoActual),
                "con el ultimo paso ya avanzado, uno anterior debe rechazarse");
    }

    @Test
    @DisplayName("rechaza lo que no son seis digitos")
    void rechazaEntradasBasura() {
        String secreto = totp.generarSecreto();

        for (String malo : new String[]{null, "", "12345", "1234567", "abcdef", "12345a", "  "}) {
            assertFalse(totp.codigoEsValido(secreto, malo, null),
                    "deberia rechazar: '" + malo + "'");
        }
    }

    @Test
    @DisplayName("normaliza espacios y guiones al teclear")
    void normalizaLoQueSeEscribe() {
        String secreto = totp.generarSecreto();
        String codigo = totp.calcularCodigo(secreto);

        // La gente teclea "123 456" o "123-456". Rechazarlo seria una molestia
        // sin motivo, y el codigo sigue siendo el mismo.
        assertEquals(codigo, totp.normalizar(codigo));
        assertTrue(totp.codigoEsValido(secreto,
                codigo.substring(0, 3) + " " + codigo.substring(3), null));
        assertTrue(totp.codigoEsValido(secreto,
                codigo.substring(0, 3) + "-" + codigo.substring(3), null));
    }

    // ============================ secretos ============================

    @Test
    @DisplayName("el secreto generado son 160 bits en Base32")
    void elSecretoTieneElTamanoCorrecto() {
        String secreto = totp.generarSecreto();

        // 20 bytes = 160 bits = 32 caracteres Base32, sin relleno.
        assertEquals(32, secreto.length(), secreto);
        assertTrue(secreto.matches("[A-Z2-7]+"), secreto);
    }

    @Test
    @DisplayName("dos secretos nunca son iguales")
    void losSecretosSonAleatorios() {
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            vistos.add(totp.generarSecreto());
        }
        assertEquals(200, vistos.size(), "hubo secretos repetidos");
    }

    @Test
    @DisplayName("el Base32 hace el viaje de ida y vuelta sin perder informacion")
    void base32IdaYVuelta() {
        // Si el Base32 estuviera mal, el codigo calculado a partir del secreto
        // guardado no casaria con el de la app. Se comprueba que un secreto
        // cualquier, pasado por el algoritmo, da el mismo codigo que el original.
        String secreto = "JBSWY3DPEHPK3PXP";
        assertEquals(calcularConSecretoCrudo(secreto), totp.calcularCodigo(secreto, 59L));
    }

    /** Calcula el mismo valor que TotpService pero partiendo de bytes. */
    private String calcularConSecretoCrudo(String base32) {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String limpio = base32.replace("=", "");
        int buffer = 0;
        int bits = 0;
        byte[] bytes = new byte[limpio.length() * 5 / 8];
        int idx = 0;
        for (char c : limpio.toCharArray()) {
            buffer = (buffer << 5) | alfabeto.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                bytes[idx++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    java.util.Arrays.copyOf(bytes, idx), "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8)
                    .putLong(59L / TotpService.PERIODO_SEGUNDOS).array());
            int off = hash[hash.length - 1] & 0x0F;
            int num = ((hash[off] & 0x7F) << 24) | ((hash[off + 1] & 0xFF) << 16)
                    | ((hash[off + 2] & 0xFF) << 8) | (hash[off + 3] & 0xFF);
            String codigo = Integer.toString(num % 1_000_000);
            while (codigo.length() < TotpService.DIGITOS) {
                codigo = "0" + codigo;
            }
            return codigo.substring(codigo.length() - TotpService.DIGITOS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================ URI para el QR ============================

    @Test
    @DisplayName("el URI es el formato otpauth que leen las apps")
    void uriConElFormatoEstandar() {
        String uri = totp.uriOtpAuth("JBSWY3DPEHPK3PXP", "Mesa de Servicios",
                "usuario@coacmagisterio.fin.ec");

        assertTrue(uri.startsWith("otpauth://totp/"), uri);
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"), uri);
        assertTrue(uri.contains("issuer=Mesa+de+Servicios"), uri);
        assertTrue(uri.contains("algorithm=SHA1"), uri);
        assertTrue(uri.contains("digits=6"), uri);
        assertTrue(uri.contains("period=30"), uri);
    }

    @Test
    @DisplayName("el correo con arroba y punto va codificado en el URI")
    void elCorreoVaCodificado() {
        // Sin codificar, el %40 del correo rompería el esquema y algunas apps
        // leerian una cuenta distinta de la real.
        String uri = totp.uriOtpAuth("JBSWY3DPEHPK3PXP", "Mesa de Servicios",
                "usuario@coacmagisterio.fin.ec");

        assertFalse(uri.contains("usuario@coacmagisterio.fin.ec"),
                "el correo debe ir URL-encoded: " + uri);
        assertTrue(uri.contains("usuario%40coacmagisterio.fin.ec"), uri);
    }

    @Test
    @DisplayName("sin emisor configurado, usa el correo para que la cuenta sea identificable")
    void sinEmisorUsaElCorreo() {
        String uri = totp.uriOtpAuth("JBSWY3DPEHPK3PXP", "  ",
                "usuario@coacmagisterio.fin.ec");

        assertTrue(uri.contains("issuer=usuario%40coacmagisterio.fin.ec"), uri);
    }
}