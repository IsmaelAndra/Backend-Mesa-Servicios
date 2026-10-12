package com.mesaayuda.repository;

import com.mesaayuda.entity.TeamUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Las pruebas con Mockito nunca ejecutan el JPQL, así que una ruta de atributo
 * mal escrita (por ejemplo {@code user.rol} en vez de {@code user.rolUser})
 * solo revienta al arrancar la aplicación, en la primera consulta.
 * <p>
 * Este test recorre los {@code @Query} del repositorio y valida cada salto de
 * la ruta contra los campos reales de las entidades, resolviendo la relación
 * hop a hop y sin necesidad de base de datos.
 */
class TeamUserRepositoryQueryTest {

    /** Rutas con al menos un salto de relación, p. ej. {@code t.user.rolUser.nameRole}. */
    private static final Pattern PATH =
            Pattern.compile("\\bt\\.((?:\\w+\\.)+)\\w+");

    @Test
    @DisplayName("Las consultas de TeamUser usan rutas de atributo que existen en las entidades")
    void rutasDeAtributoValidas() {
        List<String> invalidas = new ArrayList<>();

        for (Method method : TeamUserRepository.class.getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query == null) {
                continue;
            }
            Matcher matcher = PATH.matcher(query.value());
            while (matcher.find()) {
                String ruta = matcher.group(1);
                String problema = validarRuta(TeamUser.class, ruta);
                if (problema != null) {
                    invalidas.add(method.getName() + ": " + problema);
                }
            }
        }

        if (!invalidas.isEmpty()) {
            fail("Rutas de atributo inválidas en las consultas: " + invalidas);
        }
    }

    /**
     * Valida cada salto de la ruta. Devuelve null si es válida, o el detalle
     * del primer salto que no existe.
     */
    private String validarRuta(Class<?> raiz, String ruta) {
        Class<?> actual = raiz;
        for (String salto : ruta.split("\\.")) {
            Field campo = buscarCampo(actual, salto);
            if (campo == null) {
                return "'" + salto + "' no existe en " + actual.getSimpleName();
            }
            actual = tipoDe(campo);
            if (actual == null) {
                // Colección o tipo no navegable: el resto de la ruta no se valida.
                return null;
            }
        }
        return null;
    }

    private Field buscarCampo(Class<?> entidad, String nombre) {
        for (Field field : entidad.getDeclaredFields()) {
            if (field.getName().equals(nombre)) {
                return field;
            }
        }
        return null;
    }

    /** Tipo destino de un salto; null si es una colección o no es navegable. */
    private Class<?> tipoDe(Field campo) {
        Class<?> tipo = campo.getType();
        if (java.util.Collection.class.isAssignableFrom(tipo) || tipo.isPrimitive()) {
            return null;
        }
        // Los envoltorios de tipo simple tampoco son navegables.
        if (tipo.getPackageName().startsWith("java.")) {
            return null;
        }
        return tipo;
    }

    @Test
    @DisplayName("TeamUser mantiene la relación con User y SupportTeam")
    void relacionesDeclaradas() {
        assertTrue(buscarCampo(TeamUser.class, "user") != null, "TeamUser debe tener el campo 'user'");
        assertTrue(buscarCampo(TeamUser.class, "team") != null, "TeamUser debe tener el campo 'team'");
    }
}
