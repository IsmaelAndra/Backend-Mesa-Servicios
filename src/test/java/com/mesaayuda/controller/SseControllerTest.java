package com.mesaayuda.controller;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.SseEventBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Apertura del canal SSE con ticket de un solo uso.
 *
 * Lo que se fija aqui es que el canal ya no acepta el JWT en la URL y que un
 * ticket que haya quedado escrito en un log no sirve para abrir otro canal.
 */
class SseControllerTest {

    private static final String CORREO = "usuario@coac.test";

    private SseEventBroker broker;
    private UserRepository userRepository;
    private SseController controller;

    @BeforeEach
    void setUp() {
        broker = mock(SseEventBroker.class);
        userRepository = mock(UserRepository.class);
        controller = new SseController();
        ReflectionTestUtils.setField(controller, "sseEventBroker", broker);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        when(broker.subscribe(any(), anyBoolean())).thenReturn(new SseEmitter());
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void conSesion() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                CORREO, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private User usuarioActivo() {
        Role rol = new Role();
        rol.setNameRole("USER");
        User u = new User();
        u.setIdUser(id(7));
        u.setCorporateEmailUser(CORREO);
        u.setStatusUser("ACTIVE");
        u.setDeletedUser(false);
        u.setMustChangePassword(false);
        u.setRolUser(rol);
        return u;
    }

    private String pedirTicket() {
        conSesion();
        ResponseEntity<Map<String, String>> res = controller.ticket();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        return res.getBody().get("ticket");
    }

    @Test
    void sinSesionNoSeEmiteTicket() {
        assertEquals(401, controller.ticket().getStatusCode().value());
    }

    @Test
    void elTicketAbreElCanalUnaSolaVez() {
        when(userRepository.findByCorporateEmailUser(CORREO)).thenReturn(Optional.of(usuarioActivo()));
        String ticket = pedirTicket();

        assertEquals(200, controller.stream(ticket).getStatusCode().value());
        // Reutilizarlo, como haria quien lo leyera en un log, ya no abre nada.
        assertEquals(401, controller.stream(ticket).getStatusCode().value());
        verify(broker).subscribe(id(7), false);
    }

    @Test
    void unTicketInventadoOAusenteSeRechaza() {
        assertEquals(401, controller.stream("abc").getStatusCode().value());
        assertEquals(401, controller.stream(null).getStatusCode().value());
        verify(broker, never()).subscribe(any(), anyBoolean());
    }

    @Test
    void unaCuentaDesactivadaNoAbreElCanalAunqueTengaTicket() {
        User inactivo = usuarioActivo();
        inactivo.setStatusUser("INACTIVE");
        when(userRepository.findByCorporateEmailUser(CORREO)).thenReturn(Optional.of(inactivo));

        assertEquals(401, controller.stream(pedirTicket()).getStatusCode().value());
        verify(broker, never()).subscribe(any(), anyBoolean());
    }
}
