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
 * Cifrado simétrico (AES-256-GCM) para el client_secret de Microsoft Graph.
 *
 * <p>La clave maestra se toma de la variable de entorno {@code GRAPH_ENCRYPTION_KEY}
 * y NUNCA se almacena en base de datos ni en el código fuente. El secreto no se
 * hashea porque el backend debe poder recuperarlo para autenticarse contra Microsoft.</p>
 */
@Service
public class GraphCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${GRAPH_ENCRYPTION_KEY:}")
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
            throw new BadRequestException("No se pudo cifrar el client_secret");
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH) {
                throw new BadRequestException("El secreto cifrado no tiene un formato válido");
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

    private SecretKeySpec key() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new BadRequestException("Falta la variable de entorno GRAPH_ENCRYPTION_KEY");
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
