package com.mesaayuda.service;

import com.mesaayuda.entity.AiConfig;
import com.mesaayuda.repository.AiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cifra la api_key de la IA que quedo guardada en texto plano.
 *
 * <p>La columna api_key se guardaba en claro mientras el client_secret de Graph
 * ya estaba cifrado con AES-GCM. Dos credenciales de proveedor con el mismo riesgo
 * de facturacion, tratadas de forma distinta: con solo lectura de la tabla, la
 * clave de IA salia en claro y la de Graph no.
 *
 * <p>No se puede migrar en schema.sql porque el cifrado necesita la clave
 * maestra, que vive en el entorno y no en la base. Por eso se hace al arrancar.
 *
 * <p>Es idempotente por dos vias: la marca api_key_encrypted evita volver a mirar
 * una fila ya revisada, y el descifrado condicional evita tocar lo que ya esta
 * cifrado. Si la clave maestra no esta definida, no se hace nada y se avisa: es
 * preferible dejar la clave en claro, con el aviso, a borrarla y dejar la IA sin
 * poder funcionar.
 */
@Component
public class AiSecretMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSecretMigrationRunner.class);

    @Autowired
    private AiConfigRepository aiConfigRepository;

    @Autowired
    private SecretCryptoService secretCryptoService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElse(null);
            if (cfg == null || cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
                return;
            }
            if (Boolean.TRUE.equals(cfg.getApiKeyEncrypted())) {
                return;
            }
            // Si la clave maestra falta, encrypt lanzaria y la aplicacion no
            // arrancaria. No es aceptable por una migracion de datos, asi que se
            // comprueba antes y se sale avisando.
            secretCryptoService.comprobarClaveDisponible();

            String cifrada = secretCryptoService.encrypt(cfg.getApiKey());
            cfg.setApiKey(cifrada);
            cfg.setApiKeyEncrypted(true);
            aiConfigRepository.save(cfg);
            log.info("Se cifro la api_key de IA que estaba almacenada en texto plano.");
        } catch (Exception e) {
            log.warn("No se pudo cifrar la api_key de IA existente: {}. Se deja como esta; "
                    + "vuelva a guardarla desde el panel para que quede cifrada.", e.getMessage());
        }
    }
}