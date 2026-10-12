package com.mesaayuda.workflow;

import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.MesaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ejecuta las acciones de un workflow con los permisos de quien lo creó.
 *
 * <p><b>Esta clase es la pieza de seguridad más importante del motor.</b> Un
 * workflow escribe en tickets, cambia estados y notifica, así que alguien tiene
 * que decidir con qué autoridad ocurre. La respuesta no es "con los del sistema"
 * ni "con los de quien publica el evento": es <b>los que tenía el autor cuando
 * guardó el proceso</b>, guardados en {@code authority_scope}.
 *
 * <p>Lo que hace aquí tiene tres salvaguardas:
 * <ul>
 *   <li><b>Nunca eleva.</b> El ámbito viene de la base y solo se acepta si es un
 *       rol que existe. No hay forma de que un workflow se conceda más permisos
 *       de los que su autor tenía.
 *   <li><b>No adivina.</b> Si el ámbito guardado no corresponde a ningún rol, la
 *       ejecución falla en vez de seguir como SUPER_ADMIN "por si acaso".
 *   <li><b>Restaura el contexto.</b> El hilo del worker vuelve a quedar como
 *       estaba. De lo contrario, una ejecución dejaría al hilo con permisos
 *       ajenos y el siguiente proceso que pasara por él los heredaría.
 * </ul>
 */
@Component
public class WorkflowActor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActor.class);

    private static final Set<String> ROLES_CONOCIDOS = Set.of(
            "SUPER_ADMIN", "ADMIN", "AGENT", "USER");

    private final UserRepository userRepository;

    public WorkflowActor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Ejecuta la acción con la autoridad guardada.
     *
     * @param workflowId  solo para el registro: saber qué proceso acted
     * @param ambito      valor de {@code authority_scope}
     * @param autorId     quién creó el workflow
     * @param accion      la acción a ejecutar
     */
    public void ejecutarComo(UUID workflowId, String ambito, UUID autorId, Runnable accion) {
        String rol = normalizar(ambito);
        if (!ROLES_CONOCIDOS.contains(rol)) {
            // Si se llega aquí, alguien guardó a mano un ámbito inválido. Seguir
            // como SUPER_ADMIN "por si acaso" sería exactamente el fallo que el
            // ámbito existe para evitar.
            throw new IllegalStateException("El workflow " + workflowId
                    + " tiene un ámbito de autoridad desconocido: '" + ambito + "'.");
        }

        SecurityContext contextoAnterior = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(contextoDe(rol, autorId));
            log.debug("Ejecutando acción del workflow {} como {}", workflowId, rol);
            accion.run();
        } finally {
            // Siempre, también si la acción falla: el hilo del worker se reutiliza
            // y no puede quedar con los permisos del último workflow que pasó.
            SecurityContextHolder.setContext(contextoAnterior);
        }
    }

    /** Versión para acciones que devuelven un valor. */
    public <T> T ejecutarComo(UUID workflowId, String ambito, UUID autorId,
                               java.util.function.Supplier<T> accion) {
        Object[] resultado = new Object[1];
        // El cuerpo se declara como Runnable a propósito. Pasarlo suelto, el
        // compilador lo resuelve contra este mismo método (una asignación con
        // valor es válida como expresión y como sentencia), el método se vuelve
        // a llamar a sí mismo y el proceso revienta con StackOverflowError al
        // primer nodo que devuelva algo.
        Runnable cuerpo = () -> resultado[0] = accion.get();
        ejecutarComo(workflowId, ambito, autorId, cuerpo);
        @SuppressWarnings("unchecked")
        T valor = (T) resultado[0];
        return valor;
    }

    /**
     * Comprueba que un ámbito sea válido.
     *
     * <p>Se llama al publicar un workflow, además de al ejecutar. Es mejor
     * rechazar un guardado que descubrir tres meses después que un proceso lleva
     * desde marzo sin hacer nada.
     */
    public boolean ambitoValido(String ambito) {
        return ambito != null && ROLES_CONOCIDOS.contains(normalizar(ambito));
    }

    /**
     * El ámbito derivado del rol que tiene el autor ahora mismo.
     *
     * <p>Se recalcula al guardar. Si a quien creó el workflow le quitan el rol,
     * el proceso pierde la capacidad con la misma lógica con la que perdería
     * acceso cualquier otra cosa.
     */
    public String ambitoDe(User autor) {
        if (autor == null || autor.getRolUser() == null || autor.getRolUser().getNameRole() == null) {
            throw new IllegalStateException("No se puede determinar el ámbito: el autor no tiene rol.");
        }
        return normalizar(autor.getRolUser().getNameRole());
    }

    private SecurityContext contextoDe(String rol, UUID autorId) {
        User autor = Optional.ofNullable(autorId)
                .flatMap(userRepository::findById)
                .orElse(null);

        // Si el autor ya no existe, se sigue ejecutando con el ámbito guardado.
        // Puede ocurrir si se borró el usuario; se registra para que no sea una
        // sorpresa y se continúa, que el ámbito es el que manda de todos modos.
        if (autor == null) {
            log.warn("El workflow se va a ejecutar como {} pero su autor (id {}) ya no existe. "
                    + "Se usa el ámbito guardado.", rol, autorId);
        }

        List<SimpleGrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
        MesaUserDetails detalles = new MesaUserDetails(
                autor == null ? "workflow-" + rol : autor.getCorporateEmailUser(),
                "workflow-sin-contrasena", autoridades, false);

        // Una sola autoridad, la del ámbito. Nunca ROLE_ADMIN y ROLE_SUPER_ADMIN a
        // la vez "por si acaso": eso sería implementar el permiso de más dentro
        // del propio permiso.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                detalles, null, autoridades);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(auth);
        return contexto;
    }

    private String normalizar(String ambito) {
        return ambito == null ? "" : ambito.trim().toUpperCase(Locale.ROOT);
    }
}