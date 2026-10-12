package com.mesaayuda.workflow;

import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Definición y versionado de workflows.
 *
 * <p>La regla que sostiene todo el servicio es una sola: <b>una versión publicada
 * no se toca</b>. Editar un proceso guarda una versión nueva y deja la anterior
 * intacta. Como cada ejecución guarda con qué versión arrancó, una
 * automatización que empezó antes del cambio termina con las reglas que encontró
 * al empezar.
 *
 * <p>La alternativa, editar la definición vigente en sitio, es más barata de
 * escribir y produce dos males: procesos que cambian de comportamiento sin
 * que nadie lo decida, e historiales que dicen "se ejecutó la v1" cuando el grafo
 * de la v1 era otro. Ninguno de los dos se detecta hasta que hace falta.
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    /** Longitudes máximas, para que el constructor no pueda guardar un mapa gigante. */
    private static final int NOMBRE_MAX = 120;
    private static final int DESCRIPCION_MAX = 500;
    private static final int DEFINICION_MAX = 200_000;

    @Autowired private WorkflowRepository repository;
    @Autowired private WorkflowVersionRepository versionRepository;
    @Autowired private UserService userService;
    @Autowired private SystemAuditService systemAuditService;
    @Autowired private WorkflowValidationService validation;
    @Autowired private ObjectMapper objectMapper;

    // ============================ Consulta ============================

    public List<Workflow> listar() {
        return repository.findByDeletedWorkflowFalseOrderByNameWorkflowAsc();
    }

    public Workflow obtener(UUID id) {
        return repository.findById(id)
                .filter(w -> !Boolean.TRUE.equals(w.getDeletedWorkflow()))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", id));
    }

    /** Versiones de un workflow, de la más reciente a la más antigua. */
    public List<WorkflowVersion> versiones(UUID id) {
        obtener(id);
        return versionRepository.findByWorkflowWorkflowOrderByVersionWorkflowDesc(id);
    }

    /** La versión vigente, o {@code null} si el workflow nunca se publicó. */
    public Optional<WorkflowVersion> versionVigente(UUID workflowId) {
        return versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(workflowId);
    }

    // ============================ Alta ============================

    /**
     * Crea un workflow en BORRADOR.
     *
     * <p>Borrador y no activo a propósito: un proceso recién creado no se ejecuta
     * aunque tenga trigger. Alguien tiene que mirarlo y activarlo, porque en las
     * primeras horas un grafo casi siempre está mal.
     */
    @Transactional
    public Workflow crear(String nombre, String descripcion, String triggerType,
                          String triggerConfig) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new BadRequestException("El workflow necesita un nombre.");
        }
        if (limpio.length() > NOMBRE_MAX) {
            throw new BadRequestException("El nombre no puede pasar de " + NOMBRE_MAX + " caracteres.");
        }
        if (descripcion != null && descripcion.length() > DESCRIPCION_MAX) {
            throw new BadRequestException("La descripción no puede pasar de "
                    + DESCRIPCION_MAX + " caracteres.");
        }
        WorkflowTriggerType disparador = exigirDisparador(triggerType);
        // La columna es jsonb y jsonb no admite texto libre. Si se guardara tal
        // cual, el fallo aparecería como un error de PostgreSQL al insertar, sin
        // decir qué campo vino mal. Se comprueba aquí para poder decirlo.
        String config = validarJson(triggerConfig, "la configuración del disparador");
        if (disparador == WorkflowTriggerType.SCHEDULED) {
            WorkflowProgramacion.validar(config);
        }
        if (repository.findByNameWorkflowAndDeletedWorkflowFalse(limpio).isPresent()) {
            throw new BadRequestException("Ya existe un workflow llamado '" + limpio + "'.");
        }

        User autor = userService.getCurrentUser();
        Workflow w = new Workflow();
        w.setNameWorkflow(limpio);
        w.setDescriptionWorkflow(recortar(descripcion, DESCRIPCION_MAX));
        w.setStatusWorkflow(Workflow.ESTADO_BORRADOR);
        w.setTriggerType(disparador.name());
        w.setTriggerConfig(config);
        w.setCurrentVersion(0);
        w.setCreatedByWorkflow(autor.getIdUser());
        w.setUpdatedByWorkflow(autor.getIdUser());
        // El alcance lo decide quien guarda, nunca lo que llegue en el cuerpo de
        // la petición. Si se aceptara desde fuera, un workflow podría pedir más
        // permisos de los que su autor tiene.
        w.setAuthorityScope(ambitoDe(autor));
        w.setCreatedAtWorkflow(OffsetDateTime.now());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        w.setRunCountWorkflow(0L);

        Workflow guardado = repository.save(w);
        log.info("Workflow creado: {} (id {}) por {}", limpio, guardado.getIdWorkflow(),
                autor.getCorporateEmailUser());
        systemAuditService.record("WORKFLOW_CREATE", "Workflow", guardado.getIdWorkflow(),
                autor.getCorporateEmailUser(), "Creado el workflow '" + limpio + "'");
        return guardado;
    }

    /**
     * Publica una versión del grafo.
     *
     * <p>Si la anterior estaba vigente, se desactiva pero <b>no se borra</b>. Es lo
     * que permite que una ejecución a medio camino termine con las reglas con las
     * que empezó.
     */
    @Transactional
    public WorkflowVersion publicarVersion(UUID workflowId, WorkflowGraphDef grafo) {
        Workflow w = obtener(workflowId);
        User autor = userService.getCurrentUser();

        // Se valida antes de tocar nada. Un grafo roto que se guarda es un workflow
        // que falla tres días después, cuando nadie recuerda quién lo hizo.
        validation.exigirValido(grafo, w.getTriggerType());

        String definicion = serializar(grafo);
        if (definicion.length() > DEFINICION_MAX) {
            throw new BadRequestException("La definición del workflow es demasiado grande.");
        }

        // La versión vigente se desactiva ANTES de activar la nueva. Al revés
        // violaríamos el índice único parcial de una sola versión vigente.
        versionVigente(workflowId).ifPresent(vigente -> {
            vigente.setIsActiveVersion(false);
            versionRepository.save(vigente);
        });

        int numero = versionRepository
                .findFirstByWorkflowWorkflowOrderByVersionWorkflowDesc(workflowId)
                .map(v -> v.getVersionWorkflow() + 1)
                .orElse(1);

        WorkflowVersion nueva = new WorkflowVersion();
        nueva.setWorkflowWorkflow(w.getIdWorkflow());
        nueva.setVersionWorkflow(numero);
        nueva.setDefinitionWorkflow(definicion);
        nueva.setIsActiveVersion(true);
        nueva.setPublishedAtWorkflow(OffsetDateTime.now());
        nueva.setCreatedByWorkflow(autor.getIdUser());
        nueva.setCreatedAtWorkflow(OffsetDateTime.now());
        WorkflowVersion guardada = versionRepository.save(nueva);

        w.setCurrentVersion(numero);
        w.setUpdatedByWorkflow(autor.getIdUser());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        // Publicar una versión nueva de un workflow activo lo deja activo: quien
        // iba a ejecutarse sigue ejecutándose, con el grafo nuevo. Si lo que se
        // quiere es parar, la operación aparte es desactivar.
        if (Workflow.ESTADO_ERROR.equals(w.getStatusWorkflow())) {
            // Si estaba en ERROR por una definición anterior inválida, publicar
            // una válida lo rescata.
            w.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        }
        // Quien publica pasa a ser el responsable: el motor actúa con su autoridad.
        // Es lo que permite rescatar un workflow cuyo autor se dio de baja (el motor
        // lo pone en ERROR) sin tener que crearlo de nuevo.
        w.setCreatedByWorkflow(autor.getIdUser());
        w.setAuthorityScope(ambitoDe(autor));
        if (w.estaActivo()) {
            programar(w);
        }
        repository.save(w);

        log.info("Workflow {} publicado en la versión {}", w.getNameWorkflow(), numero);
        systemAuditService.record("WORKFLOW_UPDATE", "Workflow", w.getIdWorkflow(),
                autor.getCorporateEmailUser(),
                "Publicada la versión " + numero + " del workflow '" + w.getNameWorkflow() + "'");
        return guardada;
    }

    // ============================ Estado ============================

    @Transactional
    public void activar(UUID workflowId) {
        Workflow w = obtener(workflowId);
        if (w.estaActivo()) {
            return;
        }
        // Sin versión vigente el motor se quedaría sin grafo que ejecutar. Se
        // comprueba aquí porque el fallo sería en producción, no al activar.
        versionVigente(workflowId).orElseThrow(() -> new BadRequestException(
                "El workflow no tiene ninguna versión publicada. Publíquela antes de activarlo."));

        w.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        w.setUpdatedByWorkflow(userService.getCurrentUser().getIdUser());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        programar(w);
        repository.save(w);

        log.info("Workflow activado: {}", w.getNameWorkflow());
        systemAuditService.record("WORKFLOW_ACTIVATE", "Workflow", workflowId,
                userService.getCurrentUser().getCorporateEmailUser(),
                "Activado el workflow '" + w.getNameWorkflow() + "'");
    }

    @Transactional
    public void desactivar(UUID workflowId) {
        Workflow w = obtener(workflowId);
        w.setStatusWorkflow(Workflow.ESTADO_INACTIVO);
        w.setUpdatedByWorkflow(userService.getCurrentUser().getIdUser());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        w.setNextRunAtWorkflow(null);
        repository.save(w);

        log.info("Workflow desactivado: {}", w.getNameWorkflow());
        systemAuditService.record("WORKFLOW_DEACTIVATE", "Workflow", workflowId,
                userService.getCurrentUser().getCorporateEmailUser(),
                "Desactivado el workflow '" + w.getNameWorkflow() + "'");
    }

    /**
     * Borrado lógico.
     *
     * <p>No se borran versiones ni ejecuciones: el historial tiene que seguir
     * disponible para poder explicar qué hizo un workflow hace seis meses, aunque
     * el proceso ya no exista.
     */
    @Transactional
    public void borrar(UUID workflowId) {
        Workflow w = obtener(workflowId);
        w.setDeletedWorkflow(true);
        w.setStatusWorkflow(Workflow.ESTADO_INACTIVO);
        w.setNextRunAtWorkflow(null);
        w.setUpdatedByWorkflow(userService.getCurrentUser().getIdUser());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        repository.save(w);

        log.info("Workflow eliminado (logico): {}", w.getNameWorkflow());
        systemAuditService.record("WORKFLOW_DELETE", "Workflow", workflowId,
                userService.getCurrentUser().getCorporateEmailUser(),
                "Eliminado el workflow '" + w.getNameWorkflow() + "'");
    }

    // ============================ Utilidades ============================

    /**
     * Ámbito con el que se ejecutará el workflow.
     *
     * <p>Es el rol de quien lo guarda, y no admite variantes: se guarda tal cual.
     * Si el autor pierde ese rol, el workflow pierde la capacidad con la misma
     * lógica con la que perdería acceso cualquier otra cosa. Es lo que impide que
     * un proceso se convierta en una vía para saltarse los permisos de quien lo
     * programó.
     */
    /**
     * Cambia nombre, descripción o disparador. El grafo no se toca aquí: va por
     * versiones, que no se modifican una vez publicadas.
     */
    @Transactional
    public Workflow actualizar(UUID workflowId, String nombre, String descripcion,
                               String triggerType, String triggerConfig) {
        Workflow w = obtener(workflowId);
        User autor = userService.getCurrentUser();
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > NOMBRE_MAX) {
            throw new BadRequestException("El nombre es obligatorio y no puede pasar de " + NOMBRE_MAX
                    + " caracteres.");
        }
        if (descripcion != null && descripcion.length() > DESCRIPCION_MAX) {
            throw new BadRequestException("La descripción no puede pasar de " + DESCRIPCION_MAX + " caracteres.");
        }
        repository.findByNameWorkflowAndDeletedWorkflowFalse(limpio)
                .filter(otro -> !otro.getIdWorkflow().equals(workflowId))
                .ifPresent(otro -> {
                    throw new BadRequestException("Ya existe un workflow llamado '" + limpio + "'.");
                });
        WorkflowTriggerType disparador = exigirDisparador(triggerType);
        String config = validarJson(triggerConfig, "la configuración del disparador");
        if (disparador == WorkflowTriggerType.SCHEDULED) {
            WorkflowProgramacion.validar(config);
        }
        String antes = w.getTriggerType() + " " + w.getTriggerConfig();
        w.setNameWorkflow(limpio);
        w.setDescriptionWorkflow(descripcion);
        w.setTriggerType(disparador.name());
        w.setTriggerConfig(config);
        w.setUpdatedByWorkflow(autor.getIdUser());
        w.setUpdatedAtWorkflow(OffsetDateTime.now());
        if (w.estaActivo()) {
            programar(w);
        }
        Workflow guardado = repository.save(w);
        log.info("Workflow {} actualizado por {}", limpio, autor.getCorporateEmailUser());
        systemAuditService.record("WORKFLOW_UPDATE", "Workflow", workflowId, autor.getCorporateEmailUser(),
                recortar("Datos del workflow '" + limpio + "' modificados. Disparador: " + antes
                        + " -> " + disparador.name() + " " + config, 1000));
        return guardado;
    }

    private WorkflowTriggerType exigirDisparador(String triggerType) {
        WorkflowTriggerType disparador = WorkflowTriggerType.desde(triggerType);
        if (disparador == null) {
            throw new BadRequestException("Disparador desconocido: '" + triggerType + "'. Disponibles: "
                    + java.util.Arrays.toString(WorkflowTriggerType.values()) + ".");
        }
        return disparador;
    }

    /** Fija la próxima ejecución de un programado; en los demás la deja vacía. */
    private void programar(Workflow w) {
        if (WorkflowTriggerType.SCHEDULED.name().equals(w.getTriggerType())) {
            w.setNextRunAtWorkflow(WorkflowProgramacion.siguiente(w.getTriggerConfig(), OffsetDateTime.now()));
        } else {
            w.setNextRunAtWorkflow(null);
        }
    }

    private String ambitoDe(User autor) {
        if (autor == null || autor.getRolUser() == null
                || autor.getRolUser().getNameRole() == null) {
            throw new BadRequestException("No se puede identificar el rol del autor.");
        }
        return autor.getRolUser().getNameRole().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Comprueba que un documento sea JSON válido y lo devuelve recortado.
 *
     * <p>Un {@code null} o vacío se convierte en {@code {}} en lugar de dejar la
     * columna nula: el motor leerá este campo siempre, y tener que distinguir
     * "sin configuración" de "configuración vacía" en cada lectura no aporta nada.
     */
    private String validarJson(String texto, String que) {
        if (texto == null || texto.isBlank()) {
            return "{}";
        }
        String recortado = recortar(texto.trim(), 4000);
        try {
            objectMapper.readTree(recortado);
            return recortado;
        } catch (Exception e) {
            throw new BadRequestException("El workflow necesita un disparador, y " + que
                    + " debe ser un JSON válido (por ejemplo {\"clave\":\"valor\"}).");
        }
    }

    private String serializar(WorkflowGraphDef grafo) {
        try {
            return objectMapper.writeValueAsString(grafo);
        } catch (Exception e) {
            // Un grafo con un Map de configuración raro es la causa tipica. Se
            // devuelve un mensaje util en vez de la excepcion de Jackson, que no
            // le dice nada a quien esta construyendo el workflow.
            throw new BadRequestException("La definición del workflow no se pudo guardar: "
                    + e.getMessage());
        }
    }

    private String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}