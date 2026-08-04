package com.petvacay.payment.ports.out;

import java.util.UUID;

/**
 * Registro de eventos ya procesados: convierte a los consumidores en idempotentes.
 *
 * <p>Cierra la limitación que la Fase 3 documentó y dejó abierta a propósito.
 * Kafka garantiza entrega *al menos una vez*, no exactamente una: un rebalanceo
 * de particiones, un reinicio antes de comprometer el offset o un
 * {@code auto-offset-reset} bastan para que un evento se entregue dos veces.
 * En un servicio que mueve dinero eso significaría duplicar apuntes en el libro
 * mayor y descuadrar las cuentas.
 *
 * <p>La clave es el {@code eventId}, que todos los eventos de dominio ya llevan
 * desde la Fase 1. Al ser el identificador la clave primaria, la propia base de
 * datos rechaza el duplicado: no hay ventana de carrera entre comprobar y
 * escribir, como sí la habría con un "si no existe, inserta" en dos pasos.
 */
public interface ProcessedEventPort {

    /**
     * Marca el evento como procesado.
     *
     * @return {@code true} si es la primera vez y hay que procesarlo;
     *         {@code false} si ya estaba registrado y debe ignorarse.
     */
    boolean markProcessed(UUID eventId, String eventName);
}
