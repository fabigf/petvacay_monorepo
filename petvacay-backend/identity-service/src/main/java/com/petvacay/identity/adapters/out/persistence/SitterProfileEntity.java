package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.GeoLocation;
import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.identity.domain.model.VerificationStatus;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sitter_profiles")
public class SitterProfileEntity {

    @Id
    @Column(name = "sitter_id")
    private UUID sitterId;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "home_type", nullable = false)
    private HomeType homeType;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "price_per_night", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "max_simultaneous_pets", nullable = false)
    private int maxSimultaneousPets;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sitter_accepted_species", joinColumns = @JoinColumn(name = "sitter_id"))
    @Column(name = "species", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Species> acceptedSpecies = new HashSet<>();

    @Column(name = "max_pet_weight_kg", precision = 6, scale = 2)
    private BigDecimal maxPetWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "identity_document_reference")
    private String identityDocumentReference;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected SitterProfileEntity() {
    }

    public static SitterProfileEntity fromDomain(SitterProfile profile) {
        SitterProfileEntity entity = new SitterProfileEntity();
        entity.sitterId = profile.sitterId();
        entity.description = profile.description();
        entity.homeType = profile.homeType();
        entity.latitude = profile.location().latitude();
        entity.longitude = profile.location().longitude();
        entity.pricePerNight = profile.pricePerNight().amount();
        entity.currency = profile.pricePerNight().currency().getCurrencyCode();
        entity.maxSimultaneousPets = profile.maxSimultaneousPets();
        entity.acceptedSpecies = new HashSet<>(profile.acceptedSpecies());
        entity.maxPetWeightKg = profile.maxPetWeightKg();
        entity.verificationStatus = profile.verificationStatus();
        entity.identityDocumentReference = profile.identityDocumentReference();
        entity.rejectionReason = profile.rejectionReason();
        entity.verifiedAt = profile.verifiedAt();
        return entity;
    }

    public SitterProfile toDomain() {
        return SitterProfile.rehydrate(
                sitterId,
                description,
                homeType,
                new GeoLocation(latitude, longitude),
                new Money(pricePerNight, Currency.getInstance(currency)),
                maxSimultaneousPets,
                acceptedSpecies.isEmpty() ? EnumSet.allOf(Species.class) : EnumSet.copyOf(acceptedSpecies),
                maxPetWeightKg,
                verificationStatus,
                identityDocumentReference,
                rejectionReason,
                verifiedAt);
    }
}
