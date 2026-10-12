package com.mesaayuda.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapa con caducidad y tope de tamaño, para estado de sesión en memoria.
 *
 * <p>Existía porque el estado de chat del asistente IA era un
 * {@code ConcurrentHashMap} sin ninguna de las dos cosas. El fallo es doble:
 *
 * <ul>
 *   <li><b>Sin tope.</b> La clave es el identificador de sesión y quien la elige
 *       es el cliente, así que cualquiera con sesión podía mandar ids distintos en
 *       cada llamada y hacer crecer el mapa sin límite. Cada entrada es pequeña,
 *       pero el total no lo es: es una vía lenta de agotar la memoria del
 *       servidor.
 *   <li><b>Sin caducidad.</b> Una entrada solo se borraba al reintentar cuando
 *       Google ya había purgado la conversación. Mientras eso no pasara, el
 *       {@code interaction_id} se conservaba para siempre y cada mensaje
 *       reenviaba contexto viejo en una conversación que ya no le servía a
 *       nadie.
 * </ul>
 *
 * <p>Es sincronizado a propósito: el mapa es pequeño y se accede desde los hilos
 * de petición de HTTP. Un {@code ConcurrentHashMap} con tareas de barrido daría
 * más concurrencia a cambio de más superficie de errores, y el coste de un
 * bloqueo aquí es despreciable comparado con una llamada al proveedor de IA.
 *
 * <p>El descarte por antigüedad usa orden de inserción, no de acceso: así una
 * sesión muy activa no se renueva eternamente y las entradas viejas se van
 * siempre. Al hablar con la IA, "hace mucho que no hablas" y "no vuelves nunca"
 * son el mismo caso.
 *
 * @param <K> tipo de clave
 * @param <V> tipo de valor
 */
public class ExpiringStateMap<K, V> {

    private final Map<K, Entry<V>> mapa = new LinkedHashMap<>();
    private final Duration ttl;
    private final int maxEntradas;
    private final Clock reloj;

    public ExpiringStateMap(Duration ttl, int maxEntradas) {
        this(ttl, maxEntradas, Clock.systemUTC());
    }

    /**
     * Constructor con reloj explicito, para poder probar la caducidad sin esperar.
     *
     * <p>Probar un TTL con el reloj del sistema no es fiable: depende de que pase
     * tiempo entre dos llamadas a Instant.now(), y la resolucion del reloj no
     * garantiza ni eso. Con un reloj inyectado, "avanza treinta minutos" es una
     * linea y el test es determinista.
     */
    ExpiringStateMap(Duration ttl, int maxEntradas, Clock reloj) {
        this.ttl = ttl;
        this.maxEntradas = maxEntradas;
        this.reloj = reloj;
    }

    private final class Entry<V> {
        final V valor;
        final Instant escritoEn;

        Entry(V valor) {
            this.valor = valor;
            this.escritoEn = reloj.instant();
        }
    }

    /**
     * Valor vigente de la clave, o null si no está o caducó.
     *
     * <p>Una entrada caducada se borra al leerla. Ascender el borrado al que lee
     * evita depender de que alguien escriba otra vez en esa clave para que se
     * libere, que es justo lo que pasa con una sesión que se abandona.
     */
    private Instant relojDelPropietario() {
        return reloj.instant();
    }

    public synchronized V get(K clave) {
        Entry<V> e = mapa.get(clave);
        if (e == null) {
            return null;
        }
        if (caducada(e)) {
            mapa.remove(clave);
            return null;
        }
        return e.valor;
    }

    /** Guarda el valor renovando su vigencia. */
    public synchronized void put(K clave, V valor) {
        if (valor == null) {
            mapa.remove(clave);
            return;
        }
        purgar();
        mapa.put(clave, new Entry<>(valor));
        // El borrado va despues de insertar: si el mapa estaba lleno, la
        // purga previa ya dejo hueco y esta linea no hace falta; si no estaba,
        // todavia no hay nada que tirar.
        while (mapa.size() > maxEntradas) {
            Iterator<K> it = mapa.keySet().iterator();
            if (!it.hasNext()) {
                break;
            }
            it.next();
            it.remove();
        }
    }

    public synchronized void remove(K clave) {
        mapa.remove(clave);
    }

    /**
     * Lee y borra en una sola operación. Es lo que necesita un valor de un solo
     * uso: con {@code get} y {@code remove} por separado, dos peticiones a la vez
     * podrían leer la misma entrada antes de que ninguna la borrase.
     */
    public synchronized V tomar(K clave) {
        V valor = get(clave);
        mapa.remove(clave);
        return valor;
    }

    /** Entradas vivas. Pensado para pruebas y para diagnóstico. */
    public synchronized int size() {
        purgar();
        return mapa.size();
    }

    /** Borra todo. Pensado para pruebas. */
    public synchronized void clear() {
        mapa.clear();
    }

    /**
     * Tira lo caducado.
     *
     * <p>Recorre el mapa entero en vez de usar un orden de expiración aparte
     * porque el tamaño está acotado a unos pocos miles de entradas: barrer es
     * más simple y no se puede desincronizar del orden de inserción.
     */
    private void purgar() {
        Iterator<Map.Entry<K, Entry<V>>> it = mapa.entrySet().iterator();
        while (it.hasNext()) {
            if (caducada(it.next().getValue())) {
                it.remove();
            }
        }
    }

    private boolean caducada(Entry<V> e) {
        return !e.escritoEn.plus(ttl).isAfter(reloj.instant());
    }
}