package com.petvacay.booking.adapters.out.identity;

import com.petvacay.booking.ports.out.ClientPetsPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta el contrato interno de identity-service para saber que mascotas son
 * de un usuario.
 *
 * <p>A diferencia de {@link SitterCatalogHttpAdapter}, aqui un fallo de red no
 * puede degradarse a "no se encontro nada": eso dejaria pasar la reserva sin
 * comprobar la propiedad, que es justo lo que este adaptador existe para
 * impedir. Ante cualquier error la operacion se rechaza.
 */
@Component
public class ClientPetsHttpAdapter implements ClientPetsPort {

    private static final Logger log = LoggerFactory.getLogger(ClientPetsHttpAdapter.class);

    private final RestClient restClient;

    public ClientPetsHttpAdapter(@Value("${petvacay.services.identity.base-url}") String identityBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(identityBaseUrl).build();
    }

    @Override
    public Set<UUID> petsOwnedBy(UUID ownerId) {
        try {
            List<UUID> petIds = restClient.get()
                    .uri("/api/v1/internal/users/{ownerId}/pet-ids", ownerId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + PropagatedUserToken.current())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {
                    });
            return petIds == null ? Set.of() : Set.copyOf(petIds);
        } catch (Exception e) {
            log.error("No se pudo consultar identity-service para las mascotas de {}", ownerId, e);
            throw new BusinessRuleViolationException("ERR_PET_OWNERSHIP_UNAVAILABLE",
                    "No se pudo validar la reserva", 503,
                    "No se han podido comprobar tus mascotas en este momento. Inténtalo de nuevo.");
        }
    }

    /**
     * A diferencia de {@link #petsOwnedBy}, aqui un fallo NO tumba la operacion:
     * las fichas son informacion complementaria del detalle de una reserva, y
     * quedarse sin ellas un momento es preferible a no poder abrir la reserva.
     */
    @Override
    public List<PetDetails> detailsOf(Collection<UUID> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            return List.of();
        }

        try {
            List<PetDetails> pets = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/pets")
                            .queryParam("ids", petIds.toArray())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + PropagatedUserToken.current())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PetDetails>>() {
                    });
            return pets == null ? List.of() : pets;
        } catch (Exception e) {
            log.error("No se pudieron consultar las fichas de las mascotas {}", petIds, e);
            return List.of();
        }
    }
}
