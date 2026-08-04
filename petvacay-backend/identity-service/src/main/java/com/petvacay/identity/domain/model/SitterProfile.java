package com.petvacay.identity.domain.model;

import com.petvacay.identity.domain.event.IdentityEvents;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Perfil comercial de un cuidador y su estado KYC.
 *
 * <p>Regla central (negocio.md seccion 4.1): mientras el perfil no este
 * {@code VERIFICADO} no se indexa en el buscador ni puede recibir solicitudes.
 *
 * <p>negocio.md seccion 2 dice explicitamente que el cuidador "define sus
 * propios servicios (Alojamiento nocturno, Guarderia de dia, Paseos por
 * horas)... configura de forma autonoma sus tarifas": por eso el precio no es
 * un unico campo, es un catalogo {@code servicePrices} — el cuidador ofrece
 * los servicios que quiere, cada uno con su propia tarifa, y puede no ofrecer
 * los otros dos.
 */
public class SitterProfile {

    private final UUID sitterId;
    private String description;
    private HomeType homeType;
    private GeoLocation location;
    private Map<ServiceType, Money> servicePrices;
    private int maxSimultaneousPets;
    private Set<Species> acceptedSpecies;
    private BigDecimal maxPetWeightKg;
    private String photoUrl;

    private VerificationStatus verificationStatus;
    private String identityDocumentReference;
    private String rejectionReason;
    private Instant verifiedAt;

    private final transient List<DomainEvent> pendingEvents = new ArrayList<>();

    private SitterProfile(UUID sitterId, String description, HomeType homeType, GeoLocation location,
                          Map<ServiceType, Money> servicePrices, int maxSimultaneousPets,
                          Set<Species> acceptedSpecies, BigDecimal maxPetWeightKg, String photoUrl,
                          VerificationStatus verificationStatus) {
        this.sitterId = Objects.requireNonNull(sitterId, "sitterId");
        this.description = description;
        this.homeType = Objects.requireNonNull(homeType, "homeType");
        this.location = Objects.requireNonNull(location, "location");
        this.acceptedSpecies = acceptedSpecies == null || acceptedSpecies.isEmpty()
                ? EnumSet.allOf(Species.class)
                : EnumSet.copyOf(acceptedSpecies);
        this.maxPetWeightKg = maxPetWeightKg;
        this.photoUrl = photoUrl;
        this.verificationStatus = verificationStatus;
        setServicePrices(servicePrices);
        setMaxSimultaneousPets(maxSimultaneousPets);
    }

    /** Alta de perfil: siempre nace pendiente de verificacion. */
    public static SitterProfile create(UUID sitterId, String description, HomeType homeType,
                                       GeoLocation location, Map<ServiceType, Money> servicePrices,
                                       int maxSimultaneousPets, Set<Species> acceptedSpecies,
                                       BigDecimal maxPetWeightKg, String photoUrl) {
        return new SitterProfile(sitterId, description, homeType, location, servicePrices,
                maxSimultaneousPets, acceptedSpecies, maxPetWeightKg, photoUrl,
                VerificationStatus.PENDIENTE_VERIFICACION);
    }

    public static SitterProfile rehydrate(UUID sitterId, String description, HomeType homeType,
                                          GeoLocation location, Map<ServiceType, Money> servicePrices,
                                          int maxSimultaneousPets, Set<Species> acceptedSpecies,
                                          BigDecimal maxPetWeightKg, String photoUrl,
                                          VerificationStatus verificationStatus,
                                          String identityDocumentReference, String rejectionReason,
                                          Instant verifiedAt) {
        SitterProfile profile = new SitterProfile(sitterId, description, homeType, location, servicePrices,
                maxSimultaneousPets, acceptedSpecies, maxPetWeightKg, photoUrl, verificationStatus);
        profile.identityDocumentReference = identityDocumentReference;
        profile.rejectionReason = rejectionReason;
        profile.verifiedAt = verifiedAt;
        return profile;
    }

    /** El candidato sube su DNI/NIE/Pasaporte; el procesado real es asincrono. */
    public void submitIdentityDocument(String documentReference) {
        if (verificationStatus == VerificationStatus.VERIFICADO) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_KYC_ALREADY_VERIFIED", "Perfil ya verificado",
                    "Este perfil ya ha superado la verificación de identidad.");
        }
        this.identityDocumentReference = Objects.requireNonNull(documentReference, "documentReference");
        this.verificationStatus = VerificationStatus.PENDIENTE_VERIFICACION;
        this.rejectionReason = null;
    }

    /** Aprobacion manual de un administrador: habilita la indexacion en el buscador. */
    public void approve(Instant now) {
        if (identityDocumentReference == null) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_KYC_NO_DOCUMENT", "Documentacion ausente",
                    "No se puede verificar un perfil que no ha aportado documento de identidad.");
        }
        this.verificationStatus = VerificationStatus.VERIFICADO;
        this.verifiedAt = now;
        this.rejectionReason = null;
        raise(new IdentityEvents.CuidadorVerificadoEvent(UUID.randomUUID(), now, sitterId));
    }

    public void reject(String reason, Instant now) {
        this.verificationStatus = VerificationStatus.RECHAZADO;
        this.rejectionReason = reason;
        raise(new IdentityEvents.CuidadorRechazadoEvent(UUID.randomUUID(), now, sitterId, reason));
    }

    /** Cambios de catalogo que el modelo de lectura debe reproyectar. */
    public void updatelisting(String description, HomeType homeType, GeoLocation location,
                              Map<ServiceType, Money> servicePrices, int maxSimultaneousPets,
                              Set<Species> acceptedSpecies, BigDecimal maxPetWeightKg, String photoUrl,
                              Instant now) {
        this.description = description;
        this.homeType = Objects.requireNonNull(homeType, "homeType");
        this.location = Objects.requireNonNull(location, "location");
        this.acceptedSpecies = acceptedSpecies == null || acceptedSpecies.isEmpty()
                ? EnumSet.allOf(Species.class)
                : EnumSet.copyOf(acceptedSpecies);
        this.maxPetWeightKg = maxPetWeightKg;
        this.photoUrl = photoUrl;
        setServicePrices(servicePrices);
        setMaxSimultaneousPets(maxSimultaneousPets);
        raise(new IdentityEvents.PerfilCuidadorActualizadoEvent(UUID.randomUUID(), now, sitterId));
    }

    private void setServicePrices(Map<ServiceType, Money> servicePrices) {
        if (servicePrices == null || servicePrices.isEmpty()) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_SITTER_NO_SERVICES", "Sin servicios ofrecidos",
                    "Debes ofrecer al menos un servicio (alojamiento, guardería o paseos) con su tarifa.");
        }
        servicePrices.forEach((type, price) -> {
            if (price == null || price.isNegative() || price.isZero()) {
                throw BusinessRuleViolationException.unprocessable(
                        "ERR_SITTER_INVALID_PRICE", "Tarifa inválida",
                        "La tarifa de " + type + " debe ser mayor que cero.");
            }
        });
        this.servicePrices = new EnumMap<>(servicePrices);
    }

    private void setMaxSimultaneousPets(int value) {
        if (value <= 0) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_SITTER_INVALID_CAPACITY", "Capacidad inválida",
                    "La capacidad máxima de mascotas simultáneas debe ser al menos 1.");
        }
        this.maxSimultaneousPets = value;
    }

    /** Un perfil solo es visible en el buscador si esta verificado. */
    public boolean isSearchable() {
        return verificationStatus == VerificationStatus.VERIFICADO;
    }

    public boolean accepts(Species species, BigDecimal weightKg) {
        if (!acceptedSpecies.contains(species)) {
            return false;
        }
        return maxPetWeightKg == null || weightKg.compareTo(maxPetWeightKg) <= 0;
    }

    public boolean offers(ServiceType serviceType) {
        return servicePrices.containsKey(serviceType);
    }

    public Money priceFor(ServiceType serviceType) {
        Money price = servicePrices.get(serviceType);
        if (price == null) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_SERVICE_NOT_OFFERED", "Servicio no ofrecido",
                    "Este cuidador no ofrece el servicio solicitado.");
        }
        return price;
    }

    /** La tarifa mas baja del catalogo: lo que el buscador muestra como "desde X". */
    public Money minPrice() {
        return servicePrices.values().stream()
                .min(Comparator.comparing(Money::amount))
                .orElseThrow();
    }

    private void raise(DomainEvent event) {
        pendingEvents.add(event);
    }

    public List<DomainEvent> pullPendingEvents() {
        List<DomainEvent> copy = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    public UUID sitterId() {
        return sitterId;
    }

    public String description() {
        return description;
    }

    public HomeType homeType() {
        return homeType;
    }

    public GeoLocation location() {
        return location;
    }

    public Map<ServiceType, Money> servicePrices() {
        return servicePrices;
    }

    public int maxSimultaneousPets() {
        return maxSimultaneousPets;
    }

    public Set<Species> acceptedSpecies() {
        return acceptedSpecies;
    }

    public BigDecimal maxPetWeightKg() {
        return maxPetWeightKg;
    }

    public String photoUrl() {
        return photoUrl;
    }

    public VerificationStatus verificationStatus() {
        return verificationStatus;
    }

    public String identityDocumentReference() {
        return identityDocumentReference;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }
}
