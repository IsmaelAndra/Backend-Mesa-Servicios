package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Borrado de una sede.
 *
 * Es un borrado logico, pero aun asi avisa si queda algo apuntando a la sede: las
 * personas asignadas se pueden reubicar, y los tickets son historial y no se
 * pueden perder. En los dos casos la salida recomendada es desactivar la sede,
 * que es lo que hace el metodo cuando si se puede borrar.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceSucursalTest {

    @Mock private SucursalRepository sucursalRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketRepository ticketRepository;

    @InjectMocks private AdminService adminService;

    private Sucursal quito;

    @BeforeEach
    void setUp() {
        quito = new Sucursal();
        quito.setIdSucursal(id(1));
        quito.setCodeSucursal("QUI");
        quito.setNameSucursal("Quito");
        quito.setDeletedSucursal(false);
        quito.setStatusSucursal(true);
    }

    @Test
    @DisplayName("una sede sin personas ni tickets se da de baja logicamente")
    void borraCuandoNoHayNadaApuntando() {
        when(sucursalRepository.findById(id(1))).thenReturn(Optional.of(quito));
        when(userRepository.countBySucursal_IdSucursalAndDeletedUserFalse(id(1))).thenReturn(0L);
        when(ticketRepository.countBySucursalTicket_IdSucursalAndDeletedTicketFalse(id(1))).thenReturn(0L);

        adminService.deleteSucursal(id(1));

        verify(sucursalRepository).save(quito);
        assertTrue(quito.getDeletedSucursal());
        assertFalse(quito.getStatusSucursal());
    }

    @Test
    @DisplayName("con personas asignadas avisa y no borra")
    void noBorraConPersonasAsignadas() {
        when(sucursalRepository.findById(id(1))).thenReturn(Optional.of(quito));
        when(userRepository.countBySucursal_IdSucursalAndDeletedUserFalse(id(1))).thenReturn(3L);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> adminService.deleteSucursal(id(1)));

        assertTrue(e.getMessage().contains("3 persona(s)"));
        verify(sucursalRepository, never()).save(quito);
        assertFalse(quito.getDeletedSucursal());
    }

    @Test
    @DisplayName("con tickets historicos avisa y sugiere desactivar")
    void noBorraConTicketsAsociados() {
        when(sucursalRepository.findById(id(1))).thenReturn(Optional.of(quito));
        when(userRepository.countBySucursal_IdSucursalAndDeletedUserFalse(id(1))).thenReturn(0L);
        when(ticketRepository.countBySucursalTicket_IdSucursalAndDeletedTicketFalse(id(1))).thenReturn(12L);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> adminService.deleteSucursal(id(1)));

        assertTrue(e.getMessage().contains("12 ticket(s)"));
        assertTrue(e.getMessage().contains("Desact"));
        verify(sucursalRepository, never()).save(quito);
    }

    @Test
    @DisplayName("una sede inexistente responde 404")
    void sedeInexistente() {
        when(sucursalRepository.findById(id(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminService.deleteSucursal(id(99)));
    }

    @Test
    @DisplayName("el codigo de la sede se conserva al actualizarla")
    void actualizaConservandoElCodigo() {
        when(sucursalRepository.findById(id(1))).thenReturn(Optional.of(quito));

        adminService.saveSucursal(id(1), "QUI", "Quito - Matriz", "Sede central", null, null,
                "fa-solid fa-city", "#062e86", true);

        assertEquals("QUI", quito.getCodeSucursal());
        assertEquals("Quito - Matriz", quito.getNameSucursal());
        verify(sucursalRepository).save(quito);
    }
}