package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.mapper.UserMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RefreshTokenRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Privilegios administrativos y revocación de sesiones.
 * <p>
 * El rol venía del cuerpo de la petición sin comprobar nada, así que un ADMIN
 * podía promoverse a SUPER_ADMIN (o promover a otro) y quedar por encima de las
 * restricciones que se aplican al resto de administradores. Y al cambiar el
 * rol, el estado o al eliminar a alguien, sus sesiones seguían vivas: el
 * refresh token ya emitido seguía renovando la entrada.
 * <p>
 * Estas pruebas montan un SecurityContext real en lugar de mockear
 * getCurrentUser(), porque hasRole() se llama a sí mismo y un mock no intercepta
 * las llamadas internas de la clase.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceAuthorizationTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Mock private SystemAuditService systemAuditService;
    @Mock private MicrosoftGraphUserSyncService microsoftGraphUserSyncService;
    @Mock private PresenceService presenceService;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.mockito.Mock private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;


    @InjectMocks private UserService userService;

    @BeforeEach
    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Role rol(String nombre) {
        Role r = new Role();
        r.setNameRole(nombre);
        return r;
    }

    private static Area area(UUID id) {
        Area a = new Area();
        a.setIdArea(id);
        return a;
    }

    /** Deja al actor autenticado de verdad, para que hasRole() lo vea. */
    private void actuarioComo(UUID id, String correo, String nombreRol) {
        User actor = new User();
        actor.setIdUser(id);
        actor.setCorporateEmailUser(correo);
        actor.setRolUser(rol(nombreRol));
        // lenient: cuando el rol que se asigna no es SUPER_ADMIN, la
        // comprobación ni siquiera consulta quién es el actor, y el stub se
        // quedaría sin usar.
        lenient().when(userRepository.findByCorporateEmailUser(correo)).thenReturn(Optional.of(actor));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(correo, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + nombreRol))));
    }

    private static RegisterRequest peticion(String id, String correo, String clave,
                                            String telefono, String nombreRol) {
        return new RegisterRequest(id, correo, clave, telefono, nombreRol, id(1), null);
    }

    // ============================ ESCALADA DE PRIVILEGIOS ============================

    @Test
    @DisplayName("un ADMIN no puede crear un SUPER_ADMIN")
    void adminNoCreaSuperAdmin() {
        actuarioComo(id(5), "admin@coacmagisterio.fin.ec", "ADMIN");
        when(roleRepository.findByNameRole("SUPER_ADMIN")).thenReturn(Optional.of(rol("SUPER_ADMIN")));

        assertThrows(AccessDeniedException.class, () -> userService.create(
                peticion("200", "nuevo@coacmagisterio.fin.ec", "ClaveFuerte123!", "099", "SUPER_ADMIN")));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN tampoco puede promoverse a sí mismo en una actualización")
    void adminNoSePromueve() {
        actuarioComo(id(5), "admin@coacmagisterio.fin.ec", "ADMIN");

        User objetivo = new User();
        objetivo.setIdUser(id(5));
        objetivo.setCorporateEmailUser("admin@coacmagisterio.fin.ec");
        objetivo.setIdentificationUser("1000000002");
        objetivo.setRolUser(rol("ADMIN"));
        when(userRepository.findById(id(5))).thenReturn(Optional.of(objetivo));
        when(roleRepository.findByNameRole("SUPER_ADMIN")).thenReturn(Optional.of(rol("SUPER_ADMIN")));

        assertThrows(AccessDeniedException.class, () -> userService.update(id(5),
                peticion("1000000002", "admin@coacmagisterio.fin.ec", null, "099", "SUPER_ADMIN")));
    }

    @Test
    @DisplayName("un SUPER_ADMIN sí puede crear otro SUPER_ADMIN")
    void superAdminSiPuede() {
        actuarioComo(id(1), "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");
        when(roleRepository.findByNameRole("SUPER_ADMIN")).thenReturn(Optional.of(rol("SUPER_ADMIN")));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area(id(1))));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-en-prueba");

        // No debe lanzar por el rol: el SUPER_ADMIN sí puede crear otro.
        assertDoesNotThrow(() -> userService.create(
                peticion("200", "nuevo@coacmagisterio.fin.ec", "ClaveFuerte123!", "099", "SUPER_ADMIN")));
    }

    // ============================ REVOCACIÓN DE SESIONES ============================

    @Test
    @DisplayName("desactivar a un usuario cierra sus sesiones abiertas")
    void desactivarRevocaSesiones() {
        User objetivo = new User();
        objetivo.setIdUser(id(7));
        objetivo.setRolUser(rol("AGENT"));
        when(userRepository.findById(id(7))).thenReturn(Optional.of(objetivo));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);

        userService.changeStatus(id(7), "INACTIVE");

        verify(refreshTokenRepository).revokeAllByUser(id(7));
    }

    @Test
    @DisplayName("eliminar a un usuario cierra sus sesiones abiertas")
    void eliminarRevocaSesiones() {
        User objetivo = new User();
        objetivo.setIdUser(id(8));
        objetivo.setRolUser(rol("AGENT"));
        when(userRepository.findById(id(8))).thenReturn(Optional.of(objetivo));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.delete(id(8));

        verify(refreshTokenRepository).revokeAllByUser(id(8));
    }

    @Test
    @DisplayName("cambiar el rol de un usuario invalida su sesión")
    void cambiarRolRevocaSesiones() {
        actuarioComo(id(1), "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");

        User objetivo = new User();
        objetivo.setIdUser(id(9));
        objetivo.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        objetivo.setIdentificationUser("1000000003");
        objetivo.setRolUser(rol("AGENT"));
        when(userRepository.findById(id(9))).thenReturn(Optional.of(objetivo));
        when(roleRepository.findByNameRole("USER")).thenReturn(Optional.of(rol("USER")));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area(id(1))));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);

        userService.update(id(9),
                peticion("1000000003", "agente@coacmagisterio.fin.ec", null, "099", "USER"));

        verify(refreshTokenRepository).revokeAllByUser(id(9));
    }

    @Test
    @DisplayName("una actualización que no toca rol ni contraseña no cierra la sesión")
    void actualizacionInocuaNoRevoca() {
        actuarioComo(id(1), "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");

        User objetivo = new User();
        objetivo.setIdUser(id(9));
        objetivo.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        objetivo.setIdentificationUser("1000000003");
        objetivo.setRolUser(rol("AGENT"));
        when(userRepository.findById(id(9))).thenReturn(Optional.of(objetivo));
        when(roleRepository.findByNameRole("AGENT")).thenReturn(Optional.of(rol("AGENT")));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area(id(1))));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);

        userService.update(id(9),
                peticion("1000000003", "agente@coacmagisterio.fin.ec", null, "0995555", "AGENT"));

        // Solo cambió el teléfono: no hay motivo para echar al usuario.
        verify(refreshTokenRepository, never()).revokeAllByUser(any(java.util.UUID.class));
    }

    @Test
    @DisplayName("un fallo al revocar no rompe la actualización del usuario")
    void falloAlRevocarNoPropaga() {
        actuarioComo(id(1), "superadmin@coacmagisterio.fin.ec", "SUPER_ADMIN");

        User objetivo = new User();
        objetivo.setIdUser(id(9));
        objetivo.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        objetivo.setIdentificationUser("1000000003");
        objetivo.setRolUser(rol("AGENT"));
        when(userRepository.findById(id(9))).thenReturn(Optional.of(objetivo));
        when(roleRepository.findByNameRole("USER")).thenReturn(Optional.of(rol("USER")));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area(id(1))));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any())).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("fallo de base de datos"))
                .when(refreshTokenRepository).revokeAllByUser(any(java.util.UUID.class));

        // La revocación es defensa adicional: si falla, el usuario ya está
        // guardado igual, así que no debe tumbar la operación.
        assertDoesNotThrow(() -> userService.update(id(9),
                peticion("1000000003", "agente@coacmagisterio.fin.ec", null, "099", "USER")));
    }
}
