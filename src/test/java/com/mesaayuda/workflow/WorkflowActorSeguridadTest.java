package com.mesaayuda.workflow;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La autoridad con la que se ejecutan las acciones de un workflow.
 *
 * <p>Aquí está el compromiso de seguridad del motor, y las pruebas son
 * deliberadamente agresivas: la amenaza no es que el código falle, es que
 * alguien añada una línea que lo dejo pasar por alto. Un workflow que pudiera
 * actuar con más permisos que su autor convertiría la automatización en una forma
 * de saltarse el sistema de permisos, y eso no se ve en los tests de los tickets.
 */
class WorkflowActorSeguridadTest {

    private WorkflowActor actor;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        actor = new WorkflowActor(userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String autoridadActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("un workflow de SUPER_ADMIN ejecuta con una sola autoridad")
    void superAdminEjecutaConSuperAdmin() {
        // La comprobación va dentro de la acción a propósito: al terminar, el
        // contexto ya está restaurado y no hay ninguna autoridad. Comprobarlo
        // después comprobaría que el hilo está limpio, que es otra prueba.
        actor.ejecutarComo(id(1), "SUPER_ADMIN", id(1), () -> assertEquals("ROLE_SUPER_ADMIN", autoridadActual()));

        assertNull(autoridadActual());
    }

    @Test
    @DisplayName("un workflow de ADMIN ejecuta como ADMIN y no como SUPER_ADMIN")
    void adminNoSeConvierteEnSuperAdmin() {
        // El punto exacto del que se trata: el ADMIN tiene acceso al panel, pero
        // un proceso suyo no puede heredarlo.
        actor.ejecutarComo(id(1), "ADMIN", id(1), () -> {
            assertEquals("ROLE_ADMIN", autoridadActual());
            assertFalse(autoridadActual().contains("SUPER"));
        });
    }

    @Test
    @DisplayName("el contexto vuelve como estaba despues de actuar")
    void restauraElContexto() {
        // El worker reutiliza hilos. Si el contexto no se restaurara, el proceso
        // siguiente en pasar por ese hilo heredaría los permisos del anterior.
        SecurityContext original = SecurityContextHolder.createEmptyContext();
        original.setAuthentication(null);
        SecurityContextHolder.setContext(original);

        actor.ejecutarComo(id(1), "SUPER_ADMIN", id(1), () ->
                assertEquals("ROLE_SUPER_ADMIN", autoridadActual()));

        assertNull(autoridadActual(),
                "tras la acción el hilo debe quedar como estaba, sin autoridad heredada");
    }

    @Test
    @DisplayName("el contexto se restaura aunque la acción falle")
    void restauraElContextoTrasUnFallo() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());

        assertThrows(IllegalStateException.class, () ->
                actor.ejecutarComo(id(1), "SUPER_ADMIN", id(1), () -> {
                    throw new IllegalStateException("fallo de la acción");
                }));

        assertNull(autoridadActual());
    }

    @Test
    @DisplayName("un ámbito desconocido no se ejecuta como SUPER_ADMIN")
    void ambitoDesconocidoNoSeEjecuta() {
        // La tentación sería esta línea. Es exactamente el fallo que el ámbito
        // existe para evitar: si el valor guardado no se reconoce, no se cae
        // hacia arriba, se corta.
        assertThrows(IllegalStateException.class, () ->
                actor.ejecutarComo(id(1), "PODER_TOTAL", id(1), () ->
                        fail("no debería llegar a ejecutarse")));

        assertNull(autoridadActual());
    }

    @Test
    @DisplayName("un ámbito vacío no se ejecuta")
    void ambitoVacioNoSeEjecuta() {
        assertThrows(IllegalStateException.class, () ->
                actor.ejecutarComo(id(1), "", id(1), () -> fail("no debería ejecutarse")));
        assertThrows(IllegalStateException.class, () ->
                actor.ejecutarComo(id(1), null, id(1), () -> fail("no debería ejecutarse")));
    }

    @Test
    @DisplayName("el ámbito se normaliza sin cambiar el permiso")
    void normalizaElAmbito() {
        // Guardar "super_admin" en minúsculas no puede significar un permiso
        // distinto, ni quedarse sin ejecutar.
        actor.ejecutarComo(id(1), " super_admin ", id(1), () ->
                assertEquals("ROLE_SUPER_ADMIN", autoridadActual()));
        actor.ejecutarComo(id(1), "Admin", id(1), () ->
                assertEquals("ROLE_ADMIN", autoridadActual()));
    }

    @Test
    @DisplayName("un workflow sigue funcionando si su autor ya no existe")
    void autorInexistenteNoRompeElProceso() {
        when(userRepository.findById(id(99))).thenReturn(java.util.Optional.empty());

        // El ámbito es lo que manda. Que el autor ya no exista no significa que el
        // proceso deba saltarse la decisión que se tomó al guardarlo.
        actor.ejecutarComo(id(1), "ADMIN", id(99), () ->
                assertEquals("ROLE_ADMIN", autoridadActual()));
    }

    @Test
    @DisplayName("el ámbito se deriva del rol que tiene el autor ahora")
    void ambitoSeDerivaDelRolActual() {
        Role rol = new Role();
        rol.setNameRole("admin");
        User autor = new User();
        autor.setIdUser(id(5));
        autor.setRolUser(rol);

        assertEquals("ADMIN", actor.ambitoDe(autor));
    }

    @Test
    @DisplayName("un autor sin rol no produce un ámbito")
    void autorSinRolNoProduceAmbito() {
        // Sin rol no hay permiso que derivar. Devolver un ámbito por defecto sería
        // inventar permisos que nadie concedio.
        assertThrows(IllegalStateException.class, () -> actor.ambitoDe(new User()));
        assertThrows(IllegalStateException.class, () -> actor.ambitoDe(null));
    }

    @Test
    @DisplayName("solo los cuatro roles del sistema son ambitos validos")
    void soloRolesConocidos() {
        assertTrue(actor.ambitoValido("SUPER_ADMIN"));
        assertTrue(actor.ambitoValido("admin"));
        assertTrue(actor.ambitoValido("AGENT"));
        assertTrue(actor.ambitoValido("USER"));

        assertFalse(actor.ambitoValido("ROOT"));
        assertFalse(actor.ambitoValido("ROLE_SUPER_ADMIN"));
        assertFalse(actor.ambitoValido(""));
        assertFalse(actor.ambitoValido(null));
    }

    @Test
    @DisplayName("la accion con valor devuelve lo que produjo")
    void ejecutaConValor() {
        String resultado = actor.ejecutarComo(id(1), "ADMIN", id(1), () -> {
            assertEquals("ROLE_ADMIN", autoridadActual());
            return "hecho";
        });

        assertEquals("hecho", resultado);
        assertNull(autoridadActual());
    }

    private static void fail(String mensaje) {
        throw new AssertionError(mensaje);
    }
}