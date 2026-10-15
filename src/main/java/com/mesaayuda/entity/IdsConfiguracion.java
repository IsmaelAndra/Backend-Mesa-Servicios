package com.mesaayuda.entity;

import java.util.UUID;

/**
 * Ids fijos de las tablas de fila unica (singleton).
 *
 * <p>Session_config, routing_config, chat_config, error_page_config,
 * privacy_config y mfa_setting guardan su configuracion en una sola fila. Con
 * los ids numericos era "1"; desde V3__ids_uuid.sql cada tabla conserva un
 * uuid FIJO, el mismo que se usa aqui, para que el panel las encuentre sin
 * depender del orden de insercion del seed.
 */
public final class IdsConfiguracion {

    public static final UUID SESION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID ROUTING = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID CHAT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID ERROR_PAGE = UUID.fromString("00000000-0000-0000-0000-000000000004");
    public static final UUID PRIVACIDAD = UUID.fromString("00000000-0000-0000-0000-000000000005");
    public static final UUID MFA = UUID.fromString("00000000-0000-0000-0000-000000000006");
    public static final UUID TELEFONIA = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private IdsConfiguracion() {
    }
}