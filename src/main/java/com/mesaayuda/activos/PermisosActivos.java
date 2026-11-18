package com.mesaayuda.activos;

import com.mesaayuda.entity.User;
import com.mesaayuda.security.Roles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Quién gestiona y quién ve los activos (Fase 4):
 * <ul>
 *   <li><b>Gestiona</b>: SUPER_ADMIN y el personal (jefe o agente) de un área con
 *       {@code gestiona_activos_area} (Tecnología y Desarrollo). Ven y manejan
 *       TODOS los activos, de cualquier área.</li>
 *   <li><b>Ve todo, sin tocar</b>: AUDITOR.</li>
 *   <li>Cualquier otra persona: solo los activos que tiene asignados, para
 *       aceptar sus actas ("Mis equipos").</li>
 * </ul>
 */
@Component
public class PermisosActivos {

    public boolean gestiona(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        if (Roles.esSuperAdmin(rol)) {
            return true;
        }
        boolean personal = Roles.esJefe(rol) || Roles.AGENTE.equals(rol);
        return personal && u.getArea() != null && Boolean.TRUE.equals(u.getArea().getGestionaActivosArea());
    }

    public boolean veTodo(User u) {
        return gestiona(u) || (u != null && u.getRolUser() != null && Roles.AUDITOR.equals(u.getRolUser().getNameRole()));
    }

    /**
     * Configurar activos (tipos y numeración): solo el súper administrador y el JEFE de
     * un área que gestiona activos. Los agentes registran y mueven equipos, pero no
     * cambian el maestro de tipos ni la numeración.
     */
    public boolean configura(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        return Roles.esSuperAdmin(rol)
                || (Roles.esJefe(rol) && u.getArea() != null && Boolean.TRUE.equals(u.getArea().getGestionaActivosArea()));
    }

    public void exigirConfiguracion(User u) {
        if (!configura(u)) {
            throw new AccessDeniedException("La configuración de activos la hacen los jefes de Tecnología y Desarrollo");
        }
    }

    public void exigirGestion(User u) {
        if (!gestiona(u)) {
            throw new AccessDeniedException("Los activos los gestionan Tecnología y Desarrollo");
        }
    }
}
