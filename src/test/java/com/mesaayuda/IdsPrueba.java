package com.mesaayuda;

import java.util.UUID;

/**
 * Identificadores de prueba.
 *
 * <p>Desde V3__ids_uuid.sql todas las claves son UUID. Las pruebas usaban
 * números (1L, 3L…) porque se leen de un vistazo; {@code id(3)} conserva esa
 * lectura y devuelve siempre el mismo UUID para el mismo número
 * (00000000-0000-0000-0000-000000000003), así que dos {@code id(3)} son iguales
 * y {@code id(3)} y {@code id(4)} no.
 */
public final class IdsPrueba {

    private IdsPrueba() {
    }

    public static UUID id(long n) {
        return new UUID(0L, n);
    }
}
