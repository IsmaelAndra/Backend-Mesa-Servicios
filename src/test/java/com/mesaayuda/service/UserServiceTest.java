package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.mapper.UserMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Mock private SystemAuditService systemAuditService;

    @org.mockito.Mock private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;


    @InjectMocks private UserService userService;

    private Role role;
    private Area area;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setNameRole("AGENT");
        area = new Area();
        area.setIdArea(id(1));
        area.setNameArea("TI");
    }

    private RegisterRequest request(String password) {
        return new RegisterRequest("1723", "agente@coacmagisterio.fin.ec",
                password, "0999", "AGENT", id(1), null);
    }

    @Test
    void create_withBlankPassword_generatesAndNotifies() {
        when(userRepository.existsByCorporateEmailUser(any())).thenReturn(false);
        when(userRepository.existsByIdentificationUser(any())).thenReturn(false);
        when(roleRepository.findByNameRole("AGENT")).thenReturn(Optional.of(role));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area));

        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        when(passwordEncoder.encode(encoded.capture())).thenAnswer(inv -> "HASH-" + inv.getArgument(0));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request("   "));

        User created = saved.getValue();
        assertTrue(encoded.getValue().length() >= 8, "debe generarse una contraseña aleatoria");
        assertEquals("HASH-" + encoded.getValue(), created.getPasswordUser());
        assertTrue(created.getMustChangePassword());

        ArgumentCaptor<String> emailed = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyNewAccount(eq(created), emailed.capture());
        assertEquals(encoded.getValue(), emailed.getValue(), "el correo debe llevar la misma clave generada");
    }

    @Test
    void create_withProvidedPassword_usesProvided() {
        when(userRepository.existsByCorporateEmailUser(any())).thenReturn(false);
        when(userRepository.existsByIdentificationUser(any())).thenReturn(false);
        when(roleRepository.findByNameRole("AGENT")).thenReturn(Optional.of(role));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area));
        when(passwordEncoder.encode("MiClave123")).thenReturn("HASH-MiClave123");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request("MiClave123"));

        assertEquals("HASH-MiClave123", saved.getValue().getPasswordUser());
        verify(notificationService).notifyNewAccount(eq(saved.getValue()), eq("MiClave123"));
    }

    @Test
    void create_duplicateEmail_throws() {
        when(userRepository.existsByCorporateEmailUser(any())).thenReturn(true);
        assertThrows(BadRequestException.class, () -> userService.create(request("MiClave123")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void update_blankPassword_doesNotChange() {
        User existing = new User();
        existing.setIdUser(id(1));
        existing.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        existing.setIdentificationUser("1723");
        existing.setPasswordUser("hash-que-no-toca");
        existing.setDeletedUser(false);
        existing.setRolUser(role);
        existing.setArea(area);

        when(userRepository.findById(id(1))).thenReturn(Optional.of(existing));
        when(roleRepository.findByNameRole("AGENT")).thenReturn(Optional.of(role));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area));

        userService.update(id(1), request("   "));

        verify(passwordEncoder, never()).encode(any());
        assertEquals("hash-que-no-toca", existing.getPasswordUser());

        verify(notificationService, never()).notifyNewAccount(any(), any());
    }

    @Test
    void update_withPassword_encodesAndSetsMustChange() {
        User existing = new User();
        existing.setIdUser(id(1));
        existing.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        existing.setIdentificationUser("1723");
        existing.setPasswordUser("hash-viejo");
        existing.setMustChangePassword(false);
        existing.setDeletedUser(false);
        existing.setRolUser(role);
        existing.setArea(area);

        when(userRepository.findById(id(1))).thenReturn(Optional.of(existing));
        when(roleRepository.findByNameRole("AGENT")).thenReturn(Optional.of(role));
        when(areaRepository.findById(id(1))).thenReturn(Optional.of(area));
        when(passwordEncoder.encode("Nueva123!")).thenReturn("HASH-NUEVA");

        userService.update(id(1), request("Nueva123!"));

        assertEquals("HASH-NUEVA", existing.getPasswordUser());
        assertTrue(existing.getMustChangePassword());
    }

    @Test
    void getCurrentUser_notAuthenticated_throws() {
        assertThrows(BadRequestException.class, () -> userService.getCurrentUser());
    }
}