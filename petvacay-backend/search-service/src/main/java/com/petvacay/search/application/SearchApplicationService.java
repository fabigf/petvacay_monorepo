package com.petvacay.search.application;

import com.petvacay.search.domain.model.GeoDistance;
import com.petvacay.search.domain.model.SitterView;
import com.petvacay.search.ports.in.SearchSittersUseCase;
import com.petvacay.search.ports.out.SitterReadModelPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
public class SearchApplicationService implements SearchSittersUseCase {

    private final SitterReadModelPort readModel;

    public SearchApplicationService(SitterReadModelPort readModel) {
        this.readModel = readModel;
    }

    @Override
    public List<SitterSearchResult> search(SearchQuery query) {
        int petCount = query.effectivePetCount();

        // El acotado geografico lo resuelve el almacen; aqui solo quedan los
        // filtros de negocio y el calculo de la distancia que se muestra en la UI.
        return readModel.findNearby(query.lat(), query.lng(), query.effectiveRadiusKm()).stream()
                .filter(view -> query.petType() == null || view.accepts(query.petType()))
                .filter(view -> query.startDate() == null || query.endDate() == null
                        || view.hasCapacity(query.startDate(), query.endDate(), petCount))
                .map(view -> new ScoredView(view, distance(query, view)))
                // Se ordena por distancia penalizada, no por distancia real: cada
                // reserva confirmada que el cuidador haya cancelado le suma
                // kilometros ficticios (negocio.md 4.3, "penalizacion automatica en
                // su posicionamiento"). Lo que se muestra sigue siendo la distancia
                // de verdad.
                .sorted(Comparator.comparingDouble(ScoredView::rankingDistanceKm))
                .map(SearchApplicationService::toResult)
                .toList();
    }

    private static double distance(SearchQuery query, SitterView view) {
        return GeoDistance.kilometersBetween(query.lat(), query.lng(), view.latitude(), view.longitude());
    }

    private static SitterSearchResult toResult(ScoredView scored) {
        SitterView view = scored.view();
        return new SitterSearchResult(
                view.sitterId(),
                view.firstName(),
                view.rating(),
                view.reviewCount(),
                BigDecimal.valueOf(scored.distanceKm()).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                view.pricePerNight(),
                view.currency(),
                view.homeType(),
                view.photoUrl(),
                view.acceptedSpecies(),
                view.offeredServices(),
                view.maxSimultaneousPets());
    }

    private record ScoredView(SitterView view, double distanceKm) {

        double rankingDistanceKm() {
            return distanceKm + view.rankingPenaltyKm();
        }
    }
}
