package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.SlaPolicy;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.SlaPolicyRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.SupportTeamRepository;
import com.mesaayuda.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El catálogo alimenta los selectores de varias pantallas, así que cada entidad
 * tiene que devolver su icono y su color.
 *
 * Las políticas SLA eran la excepción: la tabla `sla_policy` sí guarda `icon` y
 * `color`, y la pantalla de gestión los deja editar, pero el catálogo no los
 * devolvía. Todas las políticas salían entonces con el mismo escudo y sin color
 * propio, que es lo que se veía en la tabla de "Políticas SLA vigentes".
 */
class AdminServiceCatalogTest {

    private AdminService service;
    private SlaPolicyRepository slaPolicyRepository;

    @BeforeEach
    void setUp() {
        service = new AdminService();
        slaPolicyRepository = Mockito.mock(SlaPolicyRepository.class);
        ReflectionTestUtils.setField(service, "slaPolicyRepository", slaPolicyRepository);
        // El resto del catalogo no es lo que se prueba aqui, pero getCatalog() lo
        // recorre entero: sin esto saltaria una NullPointerException ajena al test.
        ReflectionTestUtils.setField(service, "roleRepository", Mockito.mock(RoleRepository.class));
        ReflectionTestUtils.setField(service, "statusRepository", Mockito.mock(StatusRepository.class));
        ReflectionTestUtils.setField(service, "priorityRepository", Mockito.mock(PriorityRepository.class));
        ReflectionTestUtils.setField(service, "areaRepository", Mockito.mock(AreaRepository.class));
        ReflectionTestUtils.setField(service, "teamRepository", Mockito.mock(SupportTeamRepository.class));
        ReflectionTestUtils.setField(service, "categoryRepository", Mockito.mock(CategoryRepository.class));
        ReflectionTestUtils.setField(service, "ticketTypeRepository", Mockito.mock(TicketTypeRepository.class));
        ReflectionTestUtils.setField(service, "sucursalRepository", Mockito.mock(SucursalRepository.class));

        Mockito.when(slaPolicyRepository.findByActiveSlaPolicyTrue()).thenReturn(List.of());
    }

    private static SlaPolicy politica(String nombre, Integer resp, Integer res,
                                      String icono, String color) {
        SlaPolicy p = new SlaPolicy();
        p.setIdSlaPolicy(id(1));
        p.setNameSlaPolicy(nombre);
        p.setResponseTimeMinutes(resp);
        p.setResolutionTimeMinutes(res);
        p.setIconSlaPolicy(icono);
        p.setColorSlaPolicy(color);
        p.setActiveSlaPolicy(true);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> politicasDelCatalogo() {
        return (List<Map<String, Object>>) service.getCatalog().get("slaPolicies");
    }

    @Test
    @DisplayName("cada politica SLA viaja con su icono y su color")
    void politicasConIconoYColor() {
        Mockito.when(slaPolicyRepository.findByActiveSlaPolicyTrue()).thenReturn(List.of(
                politica("SLA Crítico", 30, 480, "fa-solid fa-bolt", "#DC2626"),
                politica("SLA Alto", 60, 1440, "fa-solid fa-triangle-exclamation", "#EA580C"),
                politica("SLA Bajo", 480, 5760, "fa-solid fa-hourglass-half", "#2563EB")));

        List<Map<String, Object>> politicas = politicasDelCatalogo();

        assertThat(politicas).hasSize(3);
        assertThat(politicas.get(0))
                .containsEntry("icon", "fa-solid fa-bolt")
                .containsEntry("color", "#DC2626");
        assertThat(politicas.get(1))
                .containsEntry("icon", "fa-solid fa-triangle-exclamation")
                .containsEntry("color", "#EA580C");
        assertThat(politicas.get(2))
                .containsEntry("icon", "fa-solid fa-hourglass-half")
                .containsEntry("color", "#2563EB");
    }

    @Test
    @DisplayName("sin icono ni color se mandan vacios, para que el frontend use su respaldo")
    void sinIconoNiColorMandaCadenasVacias() {
        Mockito.when(slaPolicyRepository.findByActiveSlaPolicyTrue())
                .thenReturn(List.of(politica("SLA Generico", 60, 600, null, null)));

        Map<String, Object> politica = politicasDelCatalogo().get(0);

        assertThat(politica).containsEntry("icon", "").containsEntry("color", "");
    }

    @Test
    @DisplayName("una politica sin tiempos no rompe el catalogo")
    void tiemposNulosNoRompenElCatalogo() {
        Mockito.when(slaPolicyRepository.findByActiveSlaPolicyTrue())
                .thenReturn(List.of(politica("SLA Sin tiempos", null, null, "fa-solid fa-clock", "#D97706")));

        Map<String, Object> politica = politicasDelCatalogo().get(0);

        assertThat(politica).containsEntry("response", null).containsEntry("resolution", null);
        assertThat(politica).containsEntry("name", "SLA Sin tiempos");
    }
}
