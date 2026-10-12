package com.mesaayuda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Límites del motor de workflows.
 *
 * <p><b>Única excepción al patrón de configuración del proyecto.</b> El resto de
 * clases usa {@code @Value} en campos sueltos; aquí hay más de una decena de
 * propiedades que se leen siempre juntas y que tienen que entenderse como un
 * contrato (un límite de pasos sin límite de tiempo no protege de nada). Una clase
 * de propiedades las declara una vez y las valida al arrancar.
 *
 * <p>Todos tienen valor por defecto suficiente para arrancar en desarrollo, pero
 * todos deberían revisarse antes de poner el motor en producción.
 */
@Component
@ConfigurationProperties(prefix = "app.workflow")
public class WorkflowProperties {

    /** Pasos máximos en una ejecución. Corta un bucle sin gastar CPU. */
    private int maxPasos = 200;

    /**
     * Cascada máxima entre workflows encadenados.
     *
     * <p>A dispara B dispara C... Con 5 se corta antes de que un par de procesos
     * mal configurados se alimenten entre sí.
     */
    private int maxProfundidad = 5;

    /** Duración máxima de una ejecución, en horas. 7 días. */
    private int maxHorasEjecucion = 168;

    /** Ejecuciones por workflow y hora. Techo de seguridad. */
    private int maxEjecucionesPorHora = 60;

    /** Ejecuciones simultáneas por workflow. Evita que un ticket dispare en avalancha. */
    private int maxEjecucionesSimultaneas = 5;

    /** Ejecuciones simultáneas en todo el motor. Tamaño del lote del worker. */
    private int maxEjecucionesConcurrentes = 10;

    /**
 * Hilos del pool del motor.
 *
     * <p>Separado del pool de correo a propósito, y no por capricho: un lote de
     * llamadas a NEXA puede tardar 60 segundos cada una. Si compartieran pool, los
     * correos del help desk se quedarían sin hilos mientras el motor trabaja.
     */
    private int threads = 2;

    /** Reintentos ante fallo transitorio. NEXA caída, Graph caído. */
    private int maxReintentos = 1;

    /**
     * Reintentos de un nodo que falla por una causa pasajera (IA saturada,
     * timeout de Microsoft Graph). Cada uno espera el doble que el anterior.
     */
    private int maxReintentosNodo = 3;

    public int getMaxReintentosNodo() {
        return maxReintentosNodo;
    }

    public void setMaxReintentosNodo(int maxReintentosNodo) {
        this.maxReintentosNodo = maxReintentosNodo;
    }

    /**
     * Minutos antes de considerar abandonada una ejecución en EJECUTANDO.
     *
     * <p>Si el proceso muere a mitad de una ejecución, nadie vuelve a tocarla y se
     * queda colgada para siempre. Este valor es el plazo de rescate.
     */
    private int minutosEjecucionAbandonada = 30;

    /** Minutos que caduca una clave de deduplicación. */
    private int minutosDeduplicacion = 1440;

    /** Días que se guardan las ejecuciones terminadas antes de purgarlas. */
    private int diasRetencionEjecuciones = 90;

    /** Frecuencia del worker, en milisegundos. */
    private long intervaloWorker = 1000L;

    /** Si el motor ejecuta de verdad. En pruebas se apaga. */
    private boolean enabled = true;

    public int getMaxPasos() {
        return maxPasos;
    }

    public void setMaxPasos(int maxPasos) {
        this.maxPasos = maxPasos;
    }

    public int getMaxProfundidad() {
        return maxProfundidad;
    }

    public void setMaxProfundidad(int maxProfundidad) {
        this.maxProfundidad = maxProfundidad;
    }

    public int getMaxHorasEjecucion() {
        return maxHorasEjecucion;
    }

    public void setMaxHorasEjecucion(int maxHorasEjecucion) {
        this.maxHorasEjecucion = maxHorasEjecucion;
    }

    public int getMaxEjecucionesPorHora() {
        return maxEjecucionesPorHora;
    }

    public void setMaxEjecucionesPorHora(int maxEjecucionesPorHora) {
        this.maxEjecucionesPorHora = maxEjecucionesPorHora;
    }

    public int getMaxEjecucionesSimultaneas() {
        return maxEjecucionesSimultaneas;
    }

    public void setMaxEjecucionesSimultaneas(int maxEjecucionesSimultaneas) {
        this.maxEjecucionesSimultaneas = maxEjecucionesSimultaneas;
    }

    public int getMaxEjecucionesConcurrentes() {
        return maxEjecucionesConcurrentes;
    }

    public void setMaxEjecucionesConcurrentes(int maxEjecucionesConcurrentes) {
        this.maxEjecucionesConcurrentes = maxEjecucionesConcurrentes;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMaxReintentos() {
        return maxReintentos;
    }

    public void setMaxReintentos(int maxReintentos) {
        this.maxReintentos = maxReintentos;
    }

    public int getMinutosEjecucionAbandonada() {
        return minutosEjecucionAbandonada;
    }

    public void setMinutosEjecucionAbandonada(int minutosEjecucionAbandonada) {
        this.minutosEjecucionAbandonada = minutosEjecucionAbandonada;
    }

    public int getMinutosDeduplicacion() {
        return minutosDeduplicacion;
    }

    public void setMinutosDeduplicacion(int minutosDeduplicacion) {
        this.minutosDeduplicacion = minutosDeduplicacion;
    }

    public int getDiasRetencionEjecuciones() {
        return diasRetencionEjecuciones;
    }

    public void setDiasRetencionEjecuciones(int diasRetencionEjecuciones) {
        this.diasRetencionEjecuciones = diasRetencionEjecuciones;
    }

    public long getIntervaloWorker() {
        return intervaloWorker;
    }

    public void setIntervaloWorker(long intervaloWorker) {
        this.intervaloWorker = intervaloWorker;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}