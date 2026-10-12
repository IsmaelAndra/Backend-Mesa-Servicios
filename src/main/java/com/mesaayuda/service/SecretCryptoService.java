package com.mesaayuda.service;

import com.mesaayuda.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado simetrico (AES-256-GCM) para credenciales de proveedores externos.
 *
 * <p>Se usa para el client_secret de Microsoft Graph y para la api_key del
 * proveedor de IA. Las dos credenciales tienen el mismo perfil de riesgo: dan acceso a un
 * servicio de pago y guardarlas en claro en la base significa que cualquiera con
 * lectura de la tabla se lleva la capacidad de facturar a la institucion.
 *
 * <p>La clave maestra se toma de una variable de entorno y NUNCA se almacena en base
 * de datos ni en el codigo fuente. El secreto no se hashea porque el backend debe poder
 * recuperarlo para autenticarse contra el proveedor.
 */
@Service
public class SecretCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Clave maestra del cifrado.
     *
     * <p>Se mantiene el nombre {@code GRAPH_ENCRYPTION_KEY} por compatibilidad con
     * los despliegues ya configurados, pero la clase ya no es solo de Graph: ahora
     * cifra tambien la api_key del proveedor de IA. Se acepta el nombre neutro
     * {@code SECRET_ENCRYPTION_KEY} y tiene prioridad si estan las dos, para que
     * un despliegue nuevo no dependa de una variable que no describe lo que
     * protege.
     */
    @Value("${SECRET_ENCRYPTION_KEY:${GRAPH_ENCRYPTION_KEY:}}")
    private String masterKey;

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo cifrar el secreto");
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH) {
                throw new BadRequestException("El secreto cifrado no tiene un formato valido");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo descifrar el client_secret. "
                    + "Verifique que GRAPH_ENCRYPTION_KEY sea la misma con la que se guardó.");
        }
    }

    /**
     * Descifra un valor que puede haber quedado guardado en claro.
     *
     * <p>Las credenciales de Graph ya se guardaban cifradas, pero la api_key de
     * la IA no: estaba en texto plano en la tabla. Al pasar a cifrarla, las
     * instalaciones que ya tienen una clave guardada siguen teniendo el valor en
     * claro, y sin esta compatibilidad dejarian de poder usarla hasta que el
     * administrador la volviera a escribir.
     *
     * <p>Se distingue el formato por el separador en base64: AES-GCM aqui emite
     * IV (12 bytes) seguido del texto cifrado, y la base64 resultante lleva un
     * {@code +} o un {@code /}. Un valor en claro nunca lo lleva.
     *
     * @return el valor en claro, sea el que hubiera o el descifrado
     */
    public String decryptIfNeeded(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.contains("+") && !stored.contains("/")) {
            return stored;
        }
        return decrypt(stored);
    }

  /**
   * Comprueba que hay clave maestra sin cifrar nada.
   *
   * <p>La usan las migraciones de arranque, que no pueden dejar la aplicacion sin
   * arrancar por un detalle de datos.
   */
  public void comprobarClaveDisponible() {
      key();
  }
    private SecretKeySpec key() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new BadRequestException("Falta la clave maestra de cifrado en el entorno");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] derived = digest.digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new BadRequestException("No se pudo derivar la clave de cifrado");
        }
    }
}
