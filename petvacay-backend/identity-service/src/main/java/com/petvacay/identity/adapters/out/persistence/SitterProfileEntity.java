package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.GeoLocation;
import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    /** Direccion legible de la que salieron las coordenadas; ver GeoLocation. */
    @Column(length = 500)
    private String address;

    /** El geocodificador no conoce el portal ni el timbre: los aporta el cuidador. */
    @Column(name = "street_number", length = 20)
    private String streetNumber;

    @Column(name = "floor_door", length = 50)
    private String floorDoor;

    /**
     * Un cuidador define sus propios servicios y su propia tarifa para cada uno
     * (negocio.md seccion 2) — no una unica tarifa. Todo en EUR, como el resto
     * de la plataforma, asi que no hace falta columna de moneda por fila.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sitter_service_prices", joinColumns = @JoinColumn(name = "sitter_id"))
    @MapKeyColumn(name = "service_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private Map<ServiceType, BigDecimal> servicePrices = new EnumMap<>(ServiceType.class);

    @Column(name = "max_simultaneous_pets", nullable = false)
    private int maxSimultaneousPets;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sitter_accepted_species", joinColumns = @JoinColumn(name = "sitter_id"))
    @Column(name = "species", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Species> acceptedSpecies = new HashSet<>();

    @Column(name = "max_pet_weight_kg", precision = 6, scale = 2)
    private BigDecimal maxPetWeightKg;

    @Column(name = "photo_url", length = 2000)
    private String photoUrl;

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
        entity.address = profile.location().address();
        entity.streetNumber = profile.location().streetNumber();
        entity.floorDoor = profile.location().floorDoor();
        entity.servicePrices = new EnumMap<>(ServiceType.class);
        profile.servicePrices().forEach((type, money) -> entity.servicePrices.put(type, money.amount()));
        entity.maxSimultaneousPets = profile.maxSimultaneousPets();
        entity.acceptedSpecies = new HashSet<>(profile.acceptedSpecies());
        entity.maxPetWeightKg = profile.maxPetWeightKg();
        entity.photoUrl = profile.photoUrl();
        entity.verificationStatus = profile.verificationStatus();
        entity.identityDocumentReference = profile.identityDocumentReference();
        entity.rejectionReason = profile.rejectionReason();
        entity.verifiedAt = profile.verifiedAt();
        return entity;
    }

    public SitterProfile toDomain() {
        Map<ServiceType, Money> prices = new EnumMap<>(ServiceType.class);
        (servicePrices == null ? new HashMap<ServiceType, BigDecimal>() : servicePrices)
                .forEach((type, amount) -> prices.put(type, new Money(amount, Money.EUR)));

        return SitterProfile.rehydrate(
                sitterId,
                description,
                homeType,
                new GeoLocation(latitude, longitude, address, streetNumber, floorDoor),
                prices,
                maxSimultaneousPets,
                acceptedSpecies.isEmpty() ? EnumSet.allOf(Species.class) : EnumSet.copyOf(acceptedSpecies),
                maxPetWeightKg,
                photoUrl,
                verificationStatus,
                identityDocumentReference,
                rejectionReason,
                verifiedAt);
    }
}
