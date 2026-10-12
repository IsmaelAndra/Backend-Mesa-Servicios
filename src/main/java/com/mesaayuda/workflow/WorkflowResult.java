package com.mesaayuda.workflow;

/**
 * Lo que un nodo le dice al motor después de ejecutarse.
 *
 * <p>Son tres casos y nada más, y se mantienen separados a propósito:
 * <ul>
 *   <li><b>avanzar por un puerto</b>: el flujo continúa. Un nodo con una sola
 *       salida no dice cuál, porque no tiene elección.
 *   <li><b>parar en un estado</b>: la ejecución queda esperando o termina. Es lo
 *       que hacen los nodos de espera y aprobación.
 *   <li><b>fallar</b>: la ejecución va a ERROR y se guarda el motivo.
 * </ul>
 *
 * <p>Un nodo que necesita parar no puede simplemente "no hacer nada": si lo hiciera,
 * el motor seguiría por el conector siguiente y una aprobación pendiente se
 * saltaría al movimiento siguiente del proceso. Por eso detener es explícito.
 */
public final class WorkflowResult {

    private final String puerto;
    private final String estadoFinal;
    private final String motivo;
    private final boolean fallo;
    private final boolean reintentable;

    private WorkflowResult(String puerto, String estadoFinal, String motivo, boolean fallo) {
        this(puerto, estadoFinal, motivo, fallo, false);
    }

    private WorkflowResult(String puerto, String estadoFinal, String motivo, boolean fallo, boolean reintentable) {
        this.puerto = puerto;
        this.estadoFinal = estadoFinal;
        this.motivo = motivo;
        this.fallo = fallo;
        this.reintentable = reintentable;
    }

    /**
     * Fallo por una causa pasajera ajena al workflow (IA saturada, timeout de un
     * servicio externo). El motor lo reintenta más tarde en vez de dar la
     * ejecución por perdida; un error de configuración no debe usar esto.
     */
    public static WorkflowResult fallarReintentable(String motivo) {
        return new WorkflowResult(null, "ERROR", motivo, true, true);
    }

    public boolean esReintentable() {
        return reintentable;
    }

    /** Continúa por el puerto indicado. {@code null} si el nodo tiene una sola vía. */
    public static WorkflowResult avanzarPor(String puerto) {
        return new WorkflowResult(puerto, null, null, false);
    }

    /** Continúa por la salida única del nodo. */
    public static WorkflowResult avanzar() {
        return new WorkflowResult(null, null, null, false);
    }

    /**
     * Se detiene en un estado sin error.
     *
     * @param estadoFinal por ejemplo {@code ESPERANDO} o {@code APROBACION}
     * @param motivo      texto para la traza, no necesariamente error
     */
    public static WorkflowResult parar(String estadoFinal, String motivo) {
        return new WorkflowResult(null, estadoFinal, motivo, false);
    }

    /** La ejecución termina con error. */
    public static WorkflowResult fallar(String motivo) {
        return new WorkflowResult(null, "ERROR", motivo, true);
    }

    public boolean continua() {
        return estadoFinal == null;
    }

    public boolean esFallo() {
        return fallo;
    }

    public String getPuerto() {
        return puerto;
    }

    public String getEstadoFinal() {
        return estadoFinal;
    }

    public String getMotivo() {
        return motivo;
    }
}