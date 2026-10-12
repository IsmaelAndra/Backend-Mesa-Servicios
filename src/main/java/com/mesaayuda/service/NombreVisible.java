package com.mesaayuda.service;

import com.mesaayuda.entity.User;

/**
 * Nombre que se muestra de una persona.
 *
 * <p>Se_synced desde Microsoft Graph, así que {@code display_name} es el nombre
 * tal como lo tiene en Outlook (normalmente "Nombre Apellido").
 *
 * <p>Estaba duplicado: TicketService lo resolvia bien y NotificationService, que
 * es quien manda los correos, devolvía el correo de la empresa. Por eso los
 * correos decían "Estimado/a <b>usuario@coacmagisterio.fin.ec</b>" en lugar del
 * nombre de la persona.
 */
public final class NombreVisible {

    private NombreVisible() {
    }

    /**
     * Nombre de la persona, con el siguiente orden:
     * <ol>
     *   <li>el nombre de Outlook ({@code display_name});</li>
     *   <li>el nombre y los apellidos, sivinieran de otro sitio;</li>
     *   <li>la parte del correo anterior a la arroba, que al menos identifica;</li>
     *   <li>el identificador de la cuenta, para no dejar el hueco vacío.</li>
     * </ol>
     *
     * @return nunca {@code null}; devuelve cadena vacía si no hay nada.
     */
    public static String de(User u) {
        if (u == null) {
            return "";
        }
        if (!esVacia(u.getDisplayName())) {
            return u.getDisplayName().trim();
        }
        String nombres = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName() : "")).trim();
        if (!nombres.isBlank()) {
            return nombres;
        }
        String correo = u.getCorporateEmailUser();
        if (correo == null || correo.isBlank()) {
            return "";
        }
        int arroba = correo.indexOf('@');
        return arroba > 0 ? correo.substring(0, arroba) : correo;
    }

    private static boolean esVacia(String s) {
        return s == null || s.isBlank();
    }
}