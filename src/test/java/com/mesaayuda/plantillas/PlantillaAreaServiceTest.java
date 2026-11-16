package com.mesaayuda.plantillas;

import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantillaAreaServiceTest {

    @Mock private PlantillaAreaRepository repo;
    @Mock private NotificationTemplateRepository plantillas;
    @Mock private AreaRepository areas;
    @Mock private UserService userService;
    @Mock private JefaturaService jefaturaService;
    @Mock private SystemAuditService auditoria;
    @InjectMocks private PlantillaAreaService servicio;

    private final UUID suArea = id(10);
    private final UUID otraArea = id(20);

    private User usuario(String rol) {
        User u = new User();
        u.setIdUser(id(1));
        u.setCorporateEmailUser("x@coac");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        return u;
    }

    @BeforeEach
    void areasExisten() {
        lenient().when(areas.existsById(any())).thenReturn(true);
        lenient().when(areas.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void elJefeGuardaLaDeSuArea() {
        User jefe = usuario("AREA_MANAGER");
        when(userService.getCurrentUser()).thenReturn(jefe);
        when(jefaturaService.areasACargo(jefe)).thenReturn(Set.of(suArea));
        when(repo.findByNombreAndAreaId("TICKET_CREADO", suArea)).thenReturn(Optional.empty());
        when(repo.findByAreaId(suArea)).thenReturn(List.of());

        servicio.guardar(suArea, "TICKET_CREADO", new PlantillaAreaService.Guardar("Hola [id]", "<p>Cuerpo</p>", true));

        verify(repo).save(argThat(p -> p.getAreaId().equals(suArea) && p.getNombre().equals("TICKET_CREADO")));
    }

    @Test
    void elJefeNoTocaOtraArea() {
        User jefe = usuario("AREA_MANAGER");
        when(userService.getCurrentUser()).thenReturn(jefe);
        when(jefaturaService.areasACargo(jefe)).thenReturn(Set.of(suArea));

        assertThrows(AccessDeniedException.class, () -> servicio.guardar(otraArea, "TICKET_CREADO",
                new PlantillaAreaService.Guardar("a", "b", true)));
        verify(repo, never()).save(any());
    }

    @Test
    void elAuditorSoloLee() {
        when(userService.getCurrentUser()).thenReturn(usuario("AUDITOR"));
        assertThrows(AccessDeniedException.class, () -> servicio.restablecer(suArea, "TICKET_CREADO"));
    }

    @Test
    void lasDeCuentaNoSePersonalizan() {
        when(userService.getCurrentUser()).thenReturn(usuario("SUPER_ADMIN"));
        assertThrows(BadRequestException.class, () -> servicio.guardar(suArea, "RESET_PASSWORD",
                new PlantillaAreaService.Guardar("a", "b", true)));
    }

    @Test
    void quitaScriptsManejadoresYJavascript() {
        String limpio = PlantillaAreaService.limpiar(
                "<p onclick=\"x()\">Hola</p><script>alert(1)</script><a href=\"javascript:alert(1)\">a</a>");
        assertFalse(limpio.contains("<script"));
        assertFalse(limpio.toLowerCase().contains("onclick"));
        assertFalse(limpio.toLowerCase().contains("javascript:"));
        assertTrue(limpio.contains("Hola"));
    }
}
