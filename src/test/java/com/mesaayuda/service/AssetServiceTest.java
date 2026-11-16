package com.mesaayuda.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.AssetRequest;
import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.AssetHistory;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.mapper.AssetHistoryMapper;
import com.mesaayuda.mapper.AssetMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AssetHistoryRepository;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import com.mesaayuda.activos.AssetType;
import com.mesaayuda.activos.AssetTypeRepository;
import com.mesaayuda.activos.NumeracionActivos;
import com.mesaayuda.activos.PermisosActivos;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio de activos: estados coherentes con la asignación, rastro
 * completo en el historial (quién lo tenía, quién lo cambió) y el corte de
 * visibilidad y gestión por rol y por área.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHistoryRepository assetHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private AreaRepository areaRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private AssetMapper assetMapper;
    @Mock private AssetHistoryMapper assetHistoryMapper;
    @Mock private UserService userService;
    @Mock private SystemAuditService systemAuditService;
    @Mock private PresenceService presenceService;
    @Spy private PermisosActivos permisos = new PermisosActivos();
    @Mock private NumeracionActivos numeracion;
    @Mock private AssetTypeRepository tipos;

    @InjectMocks private AssetService assetService;

    private final Area ti = area(id(50), true);
    private final Area finanzas = area(id(51), false);

    /** gestiona: Tecnología y Desarrollo gestionan los activos (V36). */
    private static Area area(UUID id, boolean gestiona) {
        Area a = new Area();
        a.setIdArea(id);
        a.setNameArea("Area " + id);
        a.setGestionaActivosArea(gestiona);
        return a;
    }

    private void tipoLaptop() {
        AssetType t = new AssetType();
        t.setIdAssetType(id(70));
        t.setNombre("Laptop");
        t.setPrefijo("LAP");
        lenient().when(tipos.porNombre("LAPTOP")).thenReturn(Optional.of(t));
    }

    private static User usuario(UUID id, String rol, Area area) {
        User u = new User();
        u.setIdUser(id);
        u.setCorporateEmailUser("u" + id.getLeastSignificantBits() + "@coac.local");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        u.setArea(area);
        u.setDeletedUser(false);
        return u;
    }

    private static Asset activo(UUID id, User asignado, Area area) {
        Asset a = new Asset();
        a.setIdAsset(id);
        a.setCodeAsset("TI-LAP-001");
        a.setNameAsset("Laptop");
        a.setTypeAsset("LAPTOP");
        a.setStatusAsset(asignado != null ? "ASIGNADO" : "EN_BODEGA");
        a.setAssignedUser(asignado);
        a.setArea(area);
        a.setDeletedAsset(false);
        return a;
    }

    private static AssetRequest peticion(UUID asignadoId, UUID areaId, String estado) {
        return new AssetRequest("TI-LAP-001", "Laptop", "LAPTOP", "Dell", "XPS",
                "SN-1", estado, null, null, BigDecimal.TEN, null, null, null, null,
                asignadoId, areaId, null, null, null, null, null, null, null, null);
    }

    private void guardarDevuelveLoMismo() {
        tipoLaptop();
        lenient().when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<AssetHistory> historial() {
        ArgumentCaptor<AssetHistory> c = ArgumentCaptor.forClass(AssetHistory.class);
        verify(assetHistoryRepository, atLeastOnce()).save(c.capture());
        return c.getAllValues();
    }

    // ------------------------------------------------------------------ altas

    @Test
    void sinAsignadoNaceDisponibleYQuedaElAlta() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        guardarDevuelveLoMismo();

        assetService.create(peticion(null, null, null));

        List<AssetHistory> h = historial();
        assertEquals(1, h.size());
        assertEquals("ALTA", h.get(0).getActionType());
        assertEquals(admin, h.get(0).getPerformedBy());
    }

    @Test
    void conAsignadoNaceAsignadoConAltaYAsignacion() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        User destino = usuario(id(2), "USER", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(userRepository.findById(id(2))).thenReturn(Optional.of(destino));
        guardarDevuelveLoMismo();

        assetService.create(peticion(id(2), null, null));

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository, atLeastOnce()).save(captor.capture());
        assertEquals("ASIGNADO", captor.getValue().getStatusAsset());
        List<AssetHistory> h = historial();
        assertEquals(List.of("ALTA", "ASIGNACION"), h.stream().map(AssetHistory::getActionType).toList());
        assertEquals(destino, h.get(1).getUser());
    }

    @Test
    void conAreaYSedeLasResuelveDesdeElCatalogo() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(id(4));
        when(userService.getCurrentUser()).thenReturn(admin);
        when(areaRepository.findById(id(51))).thenReturn(Optional.of(finanzas));
        when(sucursalRepository.findById(id(4))).thenReturn(Optional.of(sucursal));
        guardarDevuelveLoMismo();

        assetService.create(new AssetRequest("TI-LAP-001", "Laptop", "LAPTOP", null, null, null, null,
                null, null, null, null, null, null, null, null, id(51), id(4),
                null, null, null, null, null, null, null));

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository, atLeastOnce()).save(captor.capture());
        assertEquals(id(51), captor.getValue().getArea().getIdArea());
        assertEquals(id(4), captor.getValue().getSucursal().getIdSucursal());
    }

    // ------------------------------------------------------------ visibilidad

    @Test
    void unUsuarioNoPuedeVerElActivoDeOtro() {
        User solicitante = usuario(id(1), "USER", ti);
        when(userService.getCurrentUser()).thenReturn(solicitante);
        when(assetRepository.findById(id(9)))
                .thenReturn(Optional.of(activo(id(9), usuario(id(2), "USER", ti), null)));

        assertThrows(AccessDeniedException.class, () -> assetService.findById(id(9)));
    }

    @Test
    void elDuenoSiVeSuActivo() {
        User solicitante = usuario(id(1), "USER", ti);
        assertTrue(assetService.puedeVer(solicitante, activo(id(9), solicitante, null)));
    }

    @Test
    void tecnologiaYDesarrolloGestionanTodosLosActivos() {
        User agenteTi = usuario(id(1), "AGENT", ti);
        User deFinanzas = usuario(id(3), "USER", finanzas);
        assertTrue(assetService.puedeVer(agenteTi, activo(id(9), deFinanzas, finanzas)), "de otra área también");
        assertTrue(assetService.puedeGestionar(agenteTi, activo(id(9), deFinanzas, finanzas)));
    }

    @Test
    void elJefeDeOtraAreaSoloVeLoSuyo() {
        User jefeFinanzas = usuario(id(1), "AREA_MANAGER", finanzas);
        User deFinanzas = usuario(id(3), "USER", finanzas);
        assertFalse(assetService.puedeVer(jefeFinanzas, activo(id(9), deFinanzas, finanzas)));
        assertFalse(assetService.puedeGestionar(jefeFinanzas, activo(id(9), jefeFinanzas, finanzas)));
        assertTrue(assetService.puedeVer(jefeFinanzas, activo(id(9), jefeFinanzas, finanzas)));
    }

    @Test
    void elAuditorVeTodoPeroNoGestiona() {
        User auditor = usuario(id(1), "AUDITOR", finanzas);
        assertTrue(assetService.puedeVer(auditor, activo(id(9), null, ti)));
        assertFalse(assetService.puedeGestionar(auditor, activo(id(9), null, ti)));
    }

    @Test
    void otraAreaNoRegistraActivos() {
        when(userService.getCurrentUser()).thenReturn(usuario(id(1), "AREA_MANAGER", finanzas));
        assertThrows(AccessDeniedException.class, () -> assetService.create(peticion(null, null, null)));
    }

    @Test
    void sinCodigoTomaElSiguienteDeLaNumeracion() {
        when(userService.getCurrentUser()).thenReturn(usuario(id(1), "AGENT", ti));
        when(numeracion.siguienteCodigo("LAP")).thenReturn("TI-LAP-000124");
        guardarDevuelveLoMismo();

        assetService.create(new AssetRequest(" ", "Laptop", "LAPTOP", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null));

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository, atLeastOnce()).save(captor.capture());
        assertEquals("TI-LAP-000124", captor.getValue().getCodeAsset());
        assertEquals(id(70), captor.getValue().getAssetTypeId());
        assertEquals("EN_BODEGA", captor.getValue().getStatusAsset());
    }

    // ------------------------------------------------- movimientos y estados

    @Test
    void reasignarGuardaQuienLoTeniaQuienLoRecibeYLaNota() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        User antes = usuario(id(2), "USER", ti);
        User despues = usuario(id(3), "USER", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(activo(id(9), antes, ti)));
        when(userRepository.findById(id(3))).thenReturn(Optional.of(despues));
        guardarDevuelveLoMismo();

        assetService.assign(id(9), id(3), "acta 12");

        AssetHistory h = historial().get(0);
        assertEquals("REASIGNACION", h.getActionType());
        assertEquals(despues, h.getUser());
        assertEquals(antes, h.getPreviousUser());
        assertEquals(admin, h.getPerformedBy());
        assertEquals("acta 12", h.getNotes());
    }

    @Test
    void asignarALaMismaPersonaSeRechaza() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        User duenio = usuario(id(2), "USER", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(activo(id(9), duenio, ti)));
        when(userRepository.findById(id(2))).thenReturn(Optional.of(duenio));

        assertThrows(BadRequestException.class, () -> assetService.assign(id(9), id(2), null));
    }

    @Test
    void enReparacionNoSePuedeAsignar() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        Asset a = activo(id(9), null, ti);
        a.setStatusAsset("EN_REPARACION");
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(a));
        when(userRepository.findById(id(2))).thenReturn(Optional.of(usuario(id(2), "USER", ti)));

        assertThrows(BadRequestException.class, () -> assetService.assign(id(9), id(2), null));
    }

    @Test
    void darDeBajaDevuelveAntesElActivo() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        User duenio = usuario(id(2), "USER", ti);
        Asset a = activo(id(9), duenio, ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(a));
        guardarDevuelveLoMismo();

        assetService.changeStatus(id(9), "BAJA");

        assertEquals("BAJA", a.getStatusAsset());
        assertNull(a.getAssignedUser());
        assertEquals(List.of("DEVOLUCION", "BAJA"), historial().stream().map(AssetHistory::getActionType).toList());
    }

    @Test
    void asignadoNoSePideAMano() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(activo(id(9), null, ti)));

        assertThrows(BadRequestException.class, () -> assetService.changeStatus(id(9), "ASIGNADO"));
    }

    @Test
    void alTerminarLaReparacionVuelveConSuDuenio() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        User duenio = usuario(id(2), "USER", ti);
        Asset a = activo(id(9), duenio, ti);
        a.setStatusAsset("EN_REPARACION");
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(a));
        guardarDevuelveLoMismo();

        // Un cliente antiguo aún manda DISPONIBLE: equivale a EN_BODEGA.
        assetService.changeStatus(id(9), "DISPONIBLE");

        assertEquals("ASIGNADO", a.getStatusAsset());
        assertEquals(duenio, a.getAssignedUser());
        assertEquals("FIN_REPARACION", historial().get(0).getActionType());
    }

    @Test
    void unEstadoFueraDelCatalogoSeRechaza() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(activo(id(9), null, ti)));

        assertThrows(BadRequestException.class, () -> assetService.changeStatus(id(9), "PERDIDO"));
    }

    @Test
    void devolverSinNadieAsignadoSeRechaza() {
        User admin = usuario(id(1), "SUPER_ADMIN", ti);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(assetRepository.findById(id(9))).thenReturn(Optional.of(activo(id(9), null, ti)));

        assertThrows(BadRequestException.class, () -> assetService.unassign(id(9), null));
    }
}
