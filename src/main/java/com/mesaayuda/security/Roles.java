package com.mesaayuda.security;

import java.util.Locale;
import java.util.Set;

/**
 * Roles de la mesa de servicios multi-área.
 *
 * <ul>
 *   <li>{@link #SUPER_ADMIN}: todo el sistema.</li>
 *   <li>{@link #JEFE} (AREA_MANAGER): los tickets, el equipo y la configuración de su área.</li>
 *   <li>{@link #AGENTE}: atiende los tickets de su área; no reasigna.</li>
 *   <li>{@link #USUARIO}: crea y sigue sus propios tickets.</li>
 *   <li>{@link #AUDITOR}: consulta todo en solo lectura ({@code AuditorSoloLecturaFilter}).</li>
 * </ul>
 *
 * <p>El antiguo ADMIN ya no existe: V27 pasó a sus usuarios a jefe de área. Lo que
 * antes era "ADMIN o SUPER_ADMIN" ahora es solo SUPER_ADMIN, salvo los permisos
 * concretos que se dan al jefe (y que se acotan a su área).
 */
public final class Roles {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String JEFE = "AREA_MANAGER";
    public static final String AGENTE = "AGENT";
    public static final String USUARIO = "USER";
    public static final String AUDITOR = "AUDITOR";

    /** Quien atiende tickets: súper admin, jefes y agentes. */
    public static final Set<String> PERSONAL = Set.of(SUPER_ADMIN, JEFE, AGENTE);

    /** Lo que puede asignar un jefe que gestiona usuarios: nunca súper admin ni auditor. */
    public static final Set<String> ASIGNABLES_POR_JEFE = Set.of(JEFE, AGENTE, USUARIO);

    private Roles() {
    }

    public static String normalizar(String rol) {
        return rol == null ? "" : rol.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean esPersonal(String rol) {
        return PERSONAL.contains(normalizar(rol));
    }

    public static boolean esSuperAdmin(String rol) {
        return SUPER_ADMIN.equals(normalizar(rol));
    }

    public static boolean esJefe(String rol) {
        return JEFE.equals(normalizar(rol));
    }

    public static boolean esAuditor(String rol) {
        return AUDITOR.equals(normalizar(rol));
    }

    /** Súper admin o auditor: ven todo (el auditor solo para leer). */
    public static boolean veTodo(String rol) {
        return esSuperAdmin(rol) || esAuditor(rol);
    }
}
