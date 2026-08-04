package com.petvacay.booking.adapters.in.web;

import com.petvacay.booking.adapters.in.web.ReviewDtos.CreateReviewRequest;
import com.petvacay.booking.adapters.in.web.ReviewDtos.ReviewResponse;
import com.petvacay.booking.ports.in.ReviewUseCase;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dos prefijos distintos a proposito: crear/leer las resenas de UNA reserva es
 * una operacion de esa reserva ({@code /bookings/{id}/reviews}, ya cubierta por
 * la ruta {@code /api/v1/bookings/**} del Gateway); listar las resenas
 * PUBLICAS de un cuidador ({@code /reviews/sitters/{id}}) es una vitrina de
 * cara al buscador, sin relacion con una reserva concreta, y necesita su propia
 * ruta en el Gateway ({@code search-geocode} ya establecio el mismo patron: un
 * servicio puede registrar mas de un prefijo).
 */
@RestController
public class ReviewController {

    private final ReviewUseCase reviews;

    public ReviewController(ReviewUseCase reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/api/v1/bookings/{bookingId}/reviews")
    public ResponseEntity<ReviewResponse> leaveReview(@PathVariable UUID bookingId,
                                                       @AuthenticatedUser UUID authorId,
                                                       @Valid @RequestBody CreateReviewRequest request) {
        var review = reviews.leaveReview(bookingId, authorId, request.rating(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/reviews")
    public List<ReviewResponse> listForBooking(@PathVariable UUID bookingId, @AuthenticatedUser UUID actingUserId) {
        return reviews.listForBooking(bookingId, actingUserId).stream().map(ReviewResponse::from).toList();
    }

    /** Publico a proposito: un visitante debe poder ver las resenas antes de reservar. */
    @GetMapping("/api/v1/reviews/sitters/{sitterId}")
    public List<ReviewResponse> listForSitter(@PathVariable UUID sitterId) {
        return reviews.listForSitter(sitterId).stream().map(ReviewResponse::from).toList();
    }
}
