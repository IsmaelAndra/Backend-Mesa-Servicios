package com.mesaayuda.service;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Un jefe responde por una sola área: la de su cuenta (V33). */
class JefaturaServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private JefaturaService servicio() {
        return new JefaturaService(userRepository);
    }

    private static User usuario(String rol, UUID areaPropia) {
        User u = new User();
        u.setIdUser(id(5));
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        if (areaPropia != null) {
            Area a = new Area();
            a.setIdArea(areaPropia);
            u.setArea(a);
        }
        return u;
    }

    @Test
    void elJefeRespondePorSuAreaYSoloPorEsa() {
        User jefe = usuario("AREA_MANAGER", id(1));
        assertEquals(Set.of(id(1)), servicio().areasACargo(jefe));
        assertTrue(servicio().esJefeDe(jefe, id(1)));
        assertFalse(servicio().esJefeDe(jefe, id(2)));
    }

    @Test
    void quienNoEsJefeNoTieneAreasACargo() {
        assertTrue(servicio().areasACargo(usuario("AGENT", id(1))).isEmpty());
        assertTrue(servicio().areasACargo(usuario("SUPER_ADMIN", id(1))).isEmpty());
        assertTrue(servicio().areasACargo(usuario("AREA_MANAGER", null)).isEmpty());
        assertTrue(servicio().areasACargo(null).isEmpty());
    }

    @Test
    void losJefesDeUnAreaSalenDeLasCuentas() {
        User jefe = usuario("AREA_MANAGER", id(1));
        when(userRepository.jefesActivosDe(id(1))).thenReturn(List.of(jefe));
        assertEquals(List.of(jefe), servicio().jefesDe(id(1)));
        assertTrue(servicio().jefesDe(null).isEmpty());
    }

    @Test
    void elJefeDelAreaQueAtiendeGestionaElTicket() {
        User jefe = usuario("AREA_MANAGER", id(1));
        TicketAccessPolicy politica = new TicketAccessPolicy();
        ReflectionTestUtils.setField(politica, "jefaturaService", servicio());

        Ticket t = new Ticket();
        Area tecnologia = new Area();
        tecnologia.setIdArea(id(1));
        t.setAreaDestinoTicket(tecnologia);
        assertTrue(politica.puedeEscribir(t, jefe));

        Area otra = new Area();
        otra.setIdArea(id(2));
        t.setAreaDestinoTicket(otra);
        assertFalse(politica.puedeEscribir(t, jefe));
    }
}
