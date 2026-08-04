package com.petvacay.booking.adapters.out.persistence;

import com.petvacay.booking.domain.model.Booking;
import com.petvacay.booking.domain.model.BookingStatus;
import com.petvacay.booking.domain.model.CancellationActor;
import com.petvacay.booking.domain.model.CancellationOutcome;
import com.petvacay.booking.domain.model.PricingBreakdown;
import com.petvacay.booking.domain.model.ServiceType;
import com.petvacay.shared.domain.DateRange;
import com.petvacay.shared.domain.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class BookingEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "sitter_id", nullable = false)
    private UUID sitterId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_pets", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "pet_id", nullable = false)
    @OrderColumn(name = "position")
    private List<UUID> petIds = new ArrayList<>();

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "price_per_night", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "client_service_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal clientServiceFee;

    @Column(name = "total_charged_to_client", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalChargedToClient;

    @Column(name = "sitter_commission", nullable = false, precision = 12, scale = 2)
    private BigDecimal sitterCommission;

    @Column(name = "sitter_payout", nullable = false, precision = 12, scale = 2)
    private BigDecimal sitterPayout;

    @Column(name = "platform_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformRevenue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancellationActor cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy")
    private CancellationOutcome.CancellationPolicyKind cancellationPolicy;

    @Column(name = "refund_to_client", precision = 12, scale = 2)
    private BigDecimal refundToClient;

    @Column(name = "cancellation_payout_to_sitter", precision = 12, scale = 2)
    private BigDecimal cancellationPayoutToSitter;

    @Column(name = "cancellation_platform_revenue", precision = 12, scale = 2)
    private BigDecimal cancellationPlatformRevenue;

    @Column(name = "dispute_reason", length = 2000)
    private String disputeReason;

    protected BookingEntity() {
    }

    public static BookingEntity fromDomain(Booking booking) {
        BookingEntity entity = new BookingEntity();
        entity.id = booking.id();
        entity.clientId = booking.clientId();
        entity.sitterId = booking.sitterId();
        entity.petIds = new ArrayList<>(booking.petIds());
        entity.startDate = booking.dates().start();
        entity.endDate = booking.dates().end();
        entity.serviceType = booking.serviceType();
        entity.currency = booking.pricePerNight().currency().getCurrencyCode();
        entity.pricePerNight = booking.pricePerNight().amount();

        PricingBreakdown pricing = booking.pricing();
        entity.basePrice = pricing.basePrice().amount();
        entity.clientServiceFee = pricing.clientServiceFee().amount();
        entity.totalChargedToClient = pricing.totalChargedToClient().amount();
        entity.sitterCommission = pricing.sitterCommission().amount();
        entity.sitterPayout = pricing.sitterPayout().amount();
        entity.platformRevenue = pricing.platformRevenue().amount();

        entity.status = booking.status();
        entity.requestedAt = booking.requestedAt();
        entity.confirmedAt = booking.confirmedAt();
        entity.closedAt = booking.closedAt();
        entity.cancelledBy = booking.cancelledBy();
        entity.disputeReason = booking.disputeReason();

        CancellationOutcome outcome = booking.cancellationOutcome();
        if (outcome != null) {
            entity.cancellationPolicy = outcome.policyApplied();
            entity.refundToClient = outcome.refundToClient().amount();
            entity.cancellationPayoutToSitter = outcome.payoutToSitter().amount();
            entity.cancellationPlatformRevenue = outcome.platformRevenue().amount();
        }
        return entity;
    }

    public Booking toDomain() {
        Currency cur = Currency.getInstance(currency);
        PricingBreakdown pricing = new PricingBreakdown(
                new Money(basePrice, cur),
                new Money(clientServiceFee, cur),
                new Money(totalChargedToClient, cur),
                new Money(sitterCommission, cur),
                new Money(sitterPayout, cur),
                new Money(platformRevenue, cur));

        CancellationOutcome outcome = cancellationPolicy == null ? null : new CancellationOutcome(
                cancellationPolicy,
                new Money(refundToClient, cur),
                new Money(cancellationPayoutToSitter, cur),
                new Money(cancellationPlatformRevenue, cur));

        return Booking.rehydrate(id, clientId, sitterId, List.copyOf(petIds),
                DateRange.of(startDate, endDate), serviceType, new Money(pricePerNight, cur),
                pricing, status, requestedAt, confirmedAt, closedAt, cancelledBy, outcome, disputeReason);
    }
}
