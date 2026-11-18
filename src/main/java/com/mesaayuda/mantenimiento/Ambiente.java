package com.mesaayuda.mantenimiento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Ambiente en el que corre esta instalación: PRODUCCION o PRUEBAS (variable
 * {@code APP_AMBIENTE} del .env). Sale en la barra superior (insignia
 * "PRUEBAS") y decide qué se apaga al subir una copia de la base: en pruebas,
 * correo, Microsoft Graph, telefonía y automatizaciones, para no escribir a
 * personas reales con datos de producción.
 *
 * <p>Va en una variable y no en la base a propósito: al subir una copia de
 * producción a pruebas, un valor guardado en la base pasaría a decir "producción".
 * Sin la variable se toma PRODUCCION (lo más prudente: no apaga nada).
 */
@Component
public class Ambiente {

    public static final String PRODUCCION = "PRODUCCION";
    public static final String PRUEBAS = "PRUEBAS";

    private final String valor;

    public Ambiente(@Value("${APP_AMBIENTE:PRODUCCION}") String valor) {
        String v = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        this.valor = v.startsWith("PRUEBA") || v.equals("TEST") || v.equals("QA") || v.startsWith("DESARROLLO")
                || v.equals("DEV") ? PRUEBAS : PRODUCCION;
    }

    public String valor() {
        return valor;
    }

    public boolean esPruebas() {
        return PRUEBAS.equals(valor);
    }
}
