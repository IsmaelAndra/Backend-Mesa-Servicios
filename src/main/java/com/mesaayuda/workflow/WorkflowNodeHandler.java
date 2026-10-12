package com.mesaayuda.workflow;

/**
 * Qué sabe hacer un nodo.
 *
 * <p><b>Implementar esta interfaz es lo único que añade capacidad al motor.</b> Por
 * eso es también lo único que hay que revisar cuando aparece un tipo nuevo: la
 * garantía de que un workflow no puede hacer nada arbitrario se apoya
 * enteramente en que esta lista está cerrada en el código. Un bean que se
 * registre aquí sin querer un nodo capaz de ejecutar comandos sería un agujero,
 * y por eso el registro se valida contra {@link WorkflowNodeType} al arrancar.
 *
 * <p>Un nodo hace un efecto y dice por dónde seguir. No elige el siguiente nodo:
 * eso es trabajo del motor, que es quien tiene el grafo. Si un handler decidiera a
 * dónde ir, el motor dejaría de poder garantizar el límite de pasos y el rastro.
 */
public interface WorkflowNodeHandler {

    /** Tipo de nodo que atiende. Tiene que existir en {@link WorkflowNodeType}. */
    WorkflowNodeType tipo();

    /**
     * Ejecuta el efecto del nodo.
     *
     * @param contexto estado de la ejecución. Se lee y se escribe, pero lo que el
     *                 nodo produzca para el resto del flujo va en el contexto
     * @param nodo     definición del nodo, con su configuración
     * @return por dónde seguir, si hay que parar, o el motivo del fallo
     */
    WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo);
}