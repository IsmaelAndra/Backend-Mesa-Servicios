package com.mesaayuda.service;

import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Área de cada jefe. Un jefe responde por UNA sola área: la de su cuenta
 * (users.area_id). La tabla area_jefe de la V28, que permitía varias, se
 * retiró en la V33. Se sigue devolviendo un conjunto para que la visibilidad
 * (AlcanceTickets) y el ámbito de configuración no cambien de forma.
 */
@Service
public class JefaturaService {

    private final UserRepository userRepository;

    public JefaturaService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** El área del jefe (un solo elemento); vacío si no es jefe o no tiene área. */
    public Set<UUID> areasACargo(User u) {
        if (u == null || u.getRolUser() == null || !Roles.esJefe(u.getRolUser().getNameRole())
                || u.getArea() == null) {
            return Set.of();
        }
        return Set.of(u.getArea().getIdArea());
    }

    public boolean esJefeDe(User u, UUID areaId) {
        return areaId != null && areasACargo(u).contains(areaId);
    }

    /** Jefes activos de un área (para avisarles). */
    @Transactional(readOnly = true)
    public List<User> jefesDe(UUID areaId) {
        return areaId == null ? List.of() : userRepository.jefesActivosDe(areaId);
    }
}
