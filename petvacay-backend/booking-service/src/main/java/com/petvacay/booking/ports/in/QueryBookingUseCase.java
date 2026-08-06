package com.petvacay.booking.ports.in;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.domain.model.HandoffWindow;
import com.petvacay.booking.ports.out.ClientPetsPort;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface QueryBookingUseCase {

    /**
     * Detalle de una reserva, solo para sus dos partes.
     *
     * <p>Exige el usuario que pregunta por el mismo motivo que lo exigen las
     * transiciones: un identificador de reserva es un UUID, no un secreto, y
     * puede acabar en un log, en la barra de direcciones o en una captura. Sin
     * esta comprobacion, conocerlo bastaba para leer quienes son las dos partes,
     * que mascotas se entregan, el desglose economico completo y — si la hubo —
     * la disputa con las notas de su resolucion.
     */
    Booking getById(UUID bookingId, UUID actingUserId);

    List<Booking> listByClient(UUID clientId);

    List<Booking> listBySitter(UUID sitterId);

    /**
     * Listado transversal por estado, para el back-office. Sin el, un
     * administrador solo podria abrir una reserva en disputa si ya conociera su
     * identificador de memoria.
     */
    List<Booking> listByStatus(BookingStatus status);

    /**
     * Fichas de las mascotas de una reserva, para sus dos partes.
     *
     * <p>La comprobacion de quien puede verlas vive aqui y no en
     * identity-service porque es este contexto el que sabe que un cuidador tiene
     * derecho a la ficha de una mascota que no es suya: se la han confiado.
     */
    List<ClientPetsPort.PetDetails> listPetsOfBooking(UUID bookingId, UUID actingUserId);

    /**
     * Dirección exacta del cuidador y franja de entrega/recogida de la reserva
     * (negocio.md 4.2 y 5, evento {@code PagoConfirmadoEvent}).
     *
     * <p>Solo disponible desde que la reserva esta {@code isFunded()} — el pago
     * ya retenido en custodia — nunca antes: en {@code SOLICITADO} o
     * {@code PENDIENTE_PAGO} el cliente todavia podria no llegar a confirmar
     * nada, y revelar el domicilio del cuidador en ese punto no tiene
     * contrapartida ninguna para el. Es la misma frontera que ya usa
     * {@code blocksCalendar()} para decidir cuando el cuidador esta
     * comprometido de verdad.
     */
    HandoffInfo getHandoffInfo(UUID bookingId, UUID actingUserId);

    record HandoffInfo(String sitterAddress, double sitterLatitude, double sitterLongitude,
                      HandoffWindow window) {
    }

    /**
     * Plazas libres de un cuidador, noche a noche, en el rango indicado.
     *
     * <p>La responde booking-service y no el modelo de lectura del buscador a
     * proposito: lo que se enseña al cliente antes de reservar tiene que ser
     * exactamente lo que se le va a aplicar al reservar. La proyeccion del
     * buscador viaja por Kafka y puede ir un instante por detras, lo que
     * produciria el peor resultado posible — ofrecer una fecha y rechazarla al
     * confirmarla.
     */
    List<DailyAvailability> availability(UUID sitterId, LocalDate start, LocalDate end);

    /**
     * @param capacity plazas totales del cuidador esa noche
     * @param occupied mascotas ya comprometidas por reservas que bloquean calendario
     */
    record DailyAvailability(LocalDate date, int capacity, int occupied) {

        /**
         * Plazas libres esa noche.
         *
         * <p>Lleva {@code @JsonProperty} porque Jackson serializa de un record
         * solo sus componentes: sin la anotacion este metodo no salia en el JSON
         * y el calendario del navegador — que compara {@code free < mascotas} —
         * leia {@code undefined}, con lo que la comparacion era siempre falsa y
         * NINGUN dia llegaba a bloquearse. Un fallo mudo: la pantalla se veia
         * perfecta y el rechazo solo aparecia al confirmar la reserva.
         *
         * <p>Se calcula aqui y no en el cliente para que la nocion de "plaza
         * libre" tenga un solo dueno, el mismo que valida la reserva.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("free")
        public int free() {
            return Math.max(0, capacity - occupied);
        }
    }
}
