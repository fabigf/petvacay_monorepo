package com.petvacay.identity.domain.event;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eventos del contexto de identidad que alimentan el modelo de lectura del buscador.
 *
 * <p>Cada evento declara su {@code TOPIC} como constante publica: es la unica
 * fuente de verdad que usan tanto {@code eventName()} como las anotaciones
 * {@code @KafkaListener} de los consumidores.
 *
 * <p>{@code CuidadorVerificadoEvent} y {@code PerfilCuidadorActualizadoEvent} son
 * eventos de dominio "finos" que {@code SitterProfile} levanta al cambiar de
 * estado; solo llevan el identificador del cuidador. Antes de la Fase 4 eso
 * bastaba porque el proyector del buscador vivia en el mismo proceso y podia
 * volver a consultar los repositorios de identidad para reconstruir la vista.
 * Con {@code search-service} como proceso independiente y sin acceso a la base de
 * datos de {@code identity-service}, esa relectura ya no es posible: el evento
 * publicado tiene que llevar todo lo que el modelo de lectura necesita. Por eso
 * {@code IdentityApplicationService} traduce esos eventos finos a
 * {@link CuidadorIndexadoEvent} (un evento de integracion "gordo", con el
 * snapshot completo) en el momento de publicar, sin que {@code SitterProfile}
 * tenga que conocer datos de otros agregados (como el nombre del usuario) para
 * poder levantarlo el mismo.
 */
public final class IdentityEvents {

    private IdentityEvents() {
    }

    /**
     * Un cuidador ha subido su documento y espera verificacion.
     *
     * <p>Nace con el centro de notificaciones: la solicitud existia desde la
     * Fase 1, pero nadie avisaba de ella. El administrador solo se enteraba si
     * entraba al panel por su cuenta a mirar si habia algo pendiente, que es
     * justo lo contrario de como debe funcionar una bandeja de trabajo.
     */
    public record CuidadorPendienteVerificacionEvent(
            UUID eventId,
            Instant occurredAt,
            UUID sitterId,
            String sitterName) implements DomainEvent {

        public static final String TOPIC = "petvacay.identity.cuidador-pendiente-verificacion";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return sitterId;
        }
    }

    /** El administrador aprobo el KYC: el cuidador pasa a ser indexable. */
    public record CuidadorVerificadoEvent(UUID eventId, Instant occurredAt, UUID sitterId) implements DomainEvent {

        public static final String TOPIC = "petvacay.identity.cuidador-verificado";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return sitterId;
        }
    }

    /** KYC rechazado: hay que retirar al cuidador del indice si estuviera presente. */
    public record CuidadorRechazadoEvent(UUID eventId, Instant occurredAt, UUID sitterId, String reason)
            implements DomainEvent {

        public static final String TOPIC = "petvacay.identity.cuidador-rechazado";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return sitterId;
        }
    }

    /** Cambio de tarifas, capacidad o restricciones: requiere reproyeccion. */
    public record PerfilCuidadorActualizadoEvent(UUID eventId, Instant occurredAt, UUID sitterId)
            implements DomainEvent {

        public static final String TOPIC = "petvacay.identity.perfil-actualizado";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return sitterId;
        }
    }

    /**
     * Evento de integracion (no de dominio): snapshot completo de un cuidador
     * indexable. Es lo unico que {@code search-service} necesita para materializar
     * o actualizar su vista de lectura sin depender de la disponibilidad ni del
     * esquema de {@code identity-service}.
     */
    public record CuidadorIndexadoEvent(
            UUID eventId,
            Instant occurredAt,
            UUID sitterId,
            String firstName,
            HomeType homeType,
            double latitude,
            double longitude,
            Map<ServiceType, BigDecimal> servicePrices,
            String currency,
            int maxSimultaneousPets,
            Set<Species> acceptedSpecies,
            BigDecimal maxPetWeightKg,
            String photoUrl) implements DomainEvent {

        public static final String TOPIC = "petvacay.identity.cuidador-indexado";

        @Override
        public String eventName() {
            return TOPIC;
        }

        @Override
        public UUID aggregateId() {
            return sitterId;
        }
    }
}
