package com.petvacay.search.adapters.in.web;

import com.petvacay.identity.domain.model.Species;
import com.petvacay.search.ports.in.SearchSittersUseCase;
import com.petvacay.search.ports.in.SearchSittersUseCase.SitterSearchResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Lado de lectura del CQRS expuesto al modulo `search` de React
 * (especificaciones.md seccion 1.1).
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchSittersUseCase searchSitters;

    public SearchController(SearchSittersUseCase searchSitters) {
        this.searchSitters = searchSitters;
    }

    @GetMapping("/sitters")
    public List<SitterSearchResult> searchSitters(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Species petType,
            @RequestParam(required = false) Integer petCount) {

        return searchSitters.search(new SearchSittersUseCase.SearchQuery(
                lat, lng, radiusKm, startDate, endDate, petType, petCount));
    }
}
