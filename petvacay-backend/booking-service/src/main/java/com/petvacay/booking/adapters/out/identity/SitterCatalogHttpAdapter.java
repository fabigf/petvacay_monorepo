package com.petvacay.booking.adapters.out.identity;

import com.petvacay.booking.ports.out.SitterCatalogPort;
import com.petvacay.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Capa anticorrupcion entre reservas e identidad.
 *
 * <p>Hasta la Fase 3 esta clase resolvia la consulta en proceso, leyendo
 * directamente los repositorios de identidad porque ambos contextos vivian en el
 * mismo monolito. Desde la Fase 4, {@code identity-service} es un proceso
 * independiente con su propia base de datos, asi que la unica forma valida de
 * consultarlo es a traves de su API — exactamente el cambio que el javadoc
 * original de esta clase ya anticipaba. Ni el dominio ni
 * {@code BookingApplicationService} se han tenido que tocar: siguen dependiendo
 * de {@link SitterCatalogPort}, ajenos a que la implementacion ahora hace una
 * llamada HTTP en lugar de una lectura en memoria.
 *
 * <p>El contrato {@code SitterSummaryResponse} se duplica deliberadamente aqui en
 * vez de compartirse desde una libreria comun con {@code identity-service}: en
 * microservicios, compartir el modelo compilado de un endpoint interno crea
 * acoplamiento de despliegue entre ambos servicios (cualquier cambio de forma en
 * uno obliga a recompilar el otro). Duplicar un DTO de 6 campos en el limite de
 * integracion es la practica estandar y mas barata que esa alternativa.
 */
@Component
public class SitterCatalogHttpAdapter implements SitterCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(SitterCatalogHttpAdapter.class);

    private final RestClient restClient;

    public SitterCatalogHttpAdapter(@Value("${petvacay.services.identity.base-url}") String identityBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(identityBaseUrl).build();
    }

    @Override
    public Optional<SitterSummary> findSitter(UUID sitterId) {
        try {
            SitterSummaryResponse response = restClient.get()
                    .uri("/api/v1/internal/sitters/{id}/summary", sitterId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken())
                    .retrieve()
                    .body(SitterSummaryResponse.class);
            return Optional.ofNullable(response).map(this::toDomain);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("No se pudo consultar identity-service para el cuidador {}", sitterId, e);
            return Optional.empty();
        }
    }

    /**
     * Propaga el token del usuario que origino la peticion, en lugar de usar una
     * credencial propia del servicio.
     *
     * <p>Se eligio asi porque mantiene la trazabilidad de extremo a extremo (en los
     * logs de identity-service consta en nombre de quien se hizo la consulta) y
     * evita introducir un segundo mecanismo de credenciales para maquinas. El
     * limite conocido es que solo funciona dentro del contexto de una peticion de
     * usuario; si en el futuro un proceso en segundo plano necesitara consultar el
     * catalogo — por ejemplo el scheduler de expiracion — habria que anadir un
     * flujo client-credentials para ese caso.
     */
    private String currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException(
                "No hay un JWT en el contexto de seguridad: identity-service rechazaria la consulta.");
    }

    private SitterSummary toDomain(SitterSummaryResponse response) {
        return new SitterSummary(
                response.sitterId(),
                response.firstName(),
                response.verified(),
                new Money(response.pricePerNight(), Currency.getInstance(response.currency())),
                response.maxSimultaneousPets());
    }

    /** Espejo del DTO expuesto por {@code IdentityController.SitterSummaryResponse}. */
    private record SitterSummaryResponse(
            UUID sitterId,
            String firstName,
            boolean verified,
            BigDecimal pricePerNight,
            String currency,
            int maxSimultaneousPets) {
    }
}
