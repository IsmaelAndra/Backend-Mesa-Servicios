package com.mesaayuda.workflow;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * La configuración del disparador se guarda en una columna {@code jsonb}.
 *
 * <p>El motivo de estas pruebas es concreto: {@code jsonb} en PostgreSQL no
 * admite texto libre. Con el mapeo habitual de un {@code String}, un disparador
 * con parámetros escritos en texto plano se traducía en un error de PostgreSQL
 * al insertar, del tipo "sintaxis de entrada no válida para tipo json", que no
 * señala el campo ni dice qué se esperaba. Aquí se comprueba que el rechazo
 * llegue antes, con un mensaje que sí explica qué escribir.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowTriggerConfigTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowVersionRepository versionRepository;
    @Mock private UserService userService;
    @Mock private SystemAuditService systemAuditService;

    @InjectMocks private WorkflowService service;

    @BeforeEach
    void setUp() {
        Role rol = new Role();
        rol.setNameRole("SUPER_ADMIN");
        User autor = new User();
        autor.setIdUser(id(1));
        autor.setCorporateEmailUser("root@coacmagisterio.fin.ec");
        autor.setRolUser(rol);
        when(userService.getCurrentUser()).thenReturn(autor);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("una configuracion de disparador valida se guarda tal cual")
    void jsonValidoSeGuarda() {
        Workflow w = service.crear("X", null, "SCHEDULED", "{\"modo\":\"CRON\",\"cron\":\"0 0 8 * * MON\"}");

        assertEquals("{\"modo\":\"CRON\",\"cron\":\"0 0 8 * * MON\"}", w.getTriggerConfig());
    }

    @Test
    @DisplayName("sin configuracion se guarda un objeto vacio, no null")
    void sinConfiguracionGuardaObjetoVacio() {
        // El motor lee siempre este campo. Dejarlo nulo obligaria a comprobarlo
        // en cada lectura para distinguirlo de un objeto vacio, sin ganar nada.
        assertEquals("{}", service.crear("X", null, "TICKET_CREATED", null).getTriggerConfig());
        assertEquals("{}", service.crear("Y", null, "TICKET_CREATED", "   ").getTriggerConfig());
    }

    @Test
    @DisplayName("texto plano se rechaza con un mensaje que explica que es JSON")
    void textoPlanoSeRechaza() {
        // Es el caso que fallaba contra la base: "Criticos a infraestructura" no
        // es JSON, y PostgreSQL lo rechazaba con un error incomprensible.
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.crear("X", null, "TICKET_CREATED", "Criticos a infraestructura"));

        assertEquals(true, e.getMessage().contains("JSON"),
                "el mensaje debe decir que se espera un JSON: " + e.getMessage());
    }

    @Test
    @DisplayName("un JSON truncado tambien se rechaza")
    void jsonTruncadoSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> service.crear("X", null, "TICKET_CREATED", "{\"cron\":"));
    }

    @Test
    @DisplayName("el tipo de disparador se normaliza a mayusculas")
    void tipoDeDisparadorNormalizado() {
        // Evita que TICKET_CREATED y ticket_created acaben siendo dos workflows
        // distintos para el registro de disparadores.
        assertEquals("TICKET_CREATED",
                service.crear("X", null, "ticket_created", null).getTriggerType());
    }

    @Test
    @DisplayName("un disparador que no existe se rechaza al guardar")
    void disparadorDesconocidoSeRechaza() {
        // Antes era texto libre: "TICKET_CREADO" se guardaba y el workflow quedaba
        // activo sin dispararse nunca.
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.crear("X", null, "TICKET_CREADO", null));
        assertEquals(true, e.getMessage().contains("TICKET_CREATED"), e.getMessage());
    }

    @Test
    @DisplayName("un programado necesita una programación válida")
    void programadoSinModoSeRechaza() {
        assertThrows(BadRequestException.class, () -> service.crear("X", null, "SCHEDULED", "{}"));
        assertThrows(BadRequestException.class,
                () -> service.crear("X", null, "SCHEDULED", "{\"modo\":\"INTERVALO\",\"minutos\":1}"));
        assertThrows(BadRequestException.class,
                () -> service.crear("X", null, "SCHEDULED", "{\"modo\":\"CRON\",\"cron\":\"* * * * * *\"}"));
    }
}
