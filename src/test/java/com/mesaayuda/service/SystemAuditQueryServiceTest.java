package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.SystemAuditLogResponse;
import com.mesaayuda.entity.AuditLog;
import com.mesaayuda.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.when;

/**
 * Consulta de la auditoría del sistema.
 *
 * <p>Lo que se comprueba aquí no es tanto el filtrado, sino que la consulta no
 * pueda abrirse en exceso. Esta tabla solo crece y guarda texto libre en cada
 * fila; un endpoint que acepte "trae todo" es un endpoint que se puede usar para
 * tumbar el servidor con dos clics.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemAuditQueryServiceTest {

    @Mock private AuditLogRepository repository;

    @InjectMocks private SystemAuditQueryService servicio;

    @BeforeEach
    void setUp() {
        when(repository.buscar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private List<SystemAuditLogResponse> consultar(String accion, String usuario,
                                                  int pagina, int tamano) {
        return servicio.consultar(accion, usuario, null, null, pagina, tamano);
    }

    @Test
    @DisplayName("sin filtros devuelve una página por el máximo permitido")
    void sinFiltrosRespetaElTope() {
        consultar(null, null, 0, 5000);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).buscar(
                isNull(), isNull(), notNull(), notNull(), p.capture());

        // Aunque pidan 5000 filas, el tope son 200. Cargar más de lo que hace
        // falta para pintar una pantalla es trabajo tirado.
        assertEquals(200, p.getValue().getPageSize());
    }

    @Test
    @DisplayName("una pagina negativa se trata como la primera")
    void paginaNegativaNoRompe() {
        consultar(null, null, -5, 50);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).buscar(
                isNull(), isNull(), notNull(), notNull(), p.capture());

        assertEquals(0, p.getValue().getPageNumber());
    }

    @Test
    @DisplayName("el tamano cero se sube a uno")
    void tamanoCeroNoRompe() {
        consultar(null, null, 0, 0);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).buscar(
                isNull(), isNull(), notNull(), notNull(), p.capture());

        assertEquals(1, p.getValue().getPageSize());
    }

    @Test
    @DisplayName("el busqueda por usuario se acota en longitud")
    void elBusquedaPorUsuarioSeAcota() {
        // Un comodín de miles de caracteres sobre una columna de texto es una
        // forma barata de cargar el servidor. Se recorta antes de llegar al SQL.
        consultar(null, "a".repeat(500), 0, 50);

        ArgumentCaptor<String> p = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(repository).buscar(
                isNull(), p.capture(), notNull(), notNull(), any(Pageable.class));

        assertTrue(p.getValue().length() <= 152,
                "el patron no debe crecer sin limite: " + p.getValue().length());
        assertTrue(p.getValue().startsWith("%") && p.getValue().endsWith("%"));
    }

    @Test
    @DisplayName("la accion llega limpia o nula, nunca vacia")
    void laAccionSeNormaliza() {
        ArgumentCaptor<String> acciones = ArgumentCaptor.forClass(String.class);
        consultar("  ", null, 0, 50);
        consultar("  MFA_ENROLLED  ", null, 0, 50);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).buscar(
                acciones.capture(), isNull(), notNull(), notNull(), any(Pageable.class));

        assertEquals(null, acciones.getAllValues().get(0),
                "un espacio en blanco no debe contar como filtro");
        assertEquals("MFA_ENROLLED", acciones.getAllValues().get(1),
                "los espacios sobrantes se recortan");
    }

    @Test
    @DisplayName("sin sesion detras, el usuario sale como 'sistema'")
    void sinUsuarioSaleComoSistema() {
        // Una tarea programada o un arranque no tienen sesion. Dejar la celda
        // vacia se lee como "falta el dato", cuando en realidad significa algo
        // concreto: lo hizo el servidor por su cuenta.
        AuditLog a = new AuditLog();
        a.setIdAudit(id(1));
        a.setAction("MFA_ENROLLED");
        a.setCreatedAt(OffsetDateTime.now());
        when(repository.buscar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(a));

        List<SystemAuditLogResponse> res = consultar(null, null, 0, 50);

        assertEquals("sistema", res.get(0).usuario());
    }

    @Test
    @DisplayName("el resultado conserva los datos de la fila")
    void mapeaLaFilaCompleta() {
        OffsetDateTime fecha = OffsetDateTime.now();
        AuditLog a = new AuditLog();
        a.setIdAudit(id(7));
        a.setUsername("ana@coacmagisterio.fin.ec");
        a.setAction("REFRESH_TOKEN_REUSE");
        a.setEntity("User");
        a.setEntityId(id(42));
        a.setIpAddress("10.1.2.3");
        a.setDetails("se cerraron todas sus sesiones");
        a.setHttpMethod("POST");
        a.setRequestUri("/api/auth/refresh");
        a.setRequestId("abc-123");
        a.setCreatedAt(fecha);
        when(repository.buscar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(a));

        SystemAuditLogResponse r = consultar(null, null, 0, 50).get(0);

        assertEquals(id(7), r.id());
        assertEquals("ana@coacmagisterio.fin.ec", r.usuario());
        assertEquals("REFRESH_TOKEN_REUSE", r.accion());
        assertEquals("User", r.entidad());
        assertEquals(id(42), r.entidadId());
        assertEquals("10.1.2.3", r.ip());
        assertEquals("POST", r.metodo());
        assertEquals("/api/auth/refresh", r.ruta());
        assertEquals("abc-123", r.requestId());
        assertEquals(fecha, r.fecha());
        assertNotNull(r.detalles());
    }
}