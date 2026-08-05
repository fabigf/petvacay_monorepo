package com.petvacay.identity.application;

import com.petvacay.identity.domain.event.IdentityEvents;
import com.petvacay.identity.domain.model.GeoLocation;
import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.RefreshToken;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.ports.in.IdentityUseCases.LoginUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.ManagePetsUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.QueryUserUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.RegisterUserUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterOnboardingUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterVerificationUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.UserModerationUseCase;
import com.petvacay.identity.ports.out.IdentityPorts.AccessTokenIssuerPort;
import com.petvacay.identity.ports.out.IdentityPorts.IdentityEventPublisherPort;
import com.petvacay.identity.ports.out.IdentityPorts.PasswordHasherPort;
import com.petvacay.identity.ports.out.IdentityPorts.PetRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.SitterProfileRepositoryPort;
import com.petvacay.identity.ports.out.IdentityPorts.UserRepositoryPort;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.domain.DomainEvent;
import com.petvacay.shared.domain.Money;
import com.petvacay.shared.domain.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class IdentityApplicationService
        implements RegisterUserUseCase, LoginUseCase, QueryUserUseCase, ManagePetsUseCase,
        SitterOnboardingUseCase, SitterVerificationUseCase, UserModerationUseCase {

    private static final Logger log = LoggerFactory.getLogger(IdentityApplicationService.class);

    /**
     * Hash de descarte con el que comparar cuando el email no existe, para que el
     * coste en tiempo de un login fallido no delate si la cuenta esta registrada.
     * Es un hash BCrypt valido de una contrasena aleatoria que nadie conoce.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepositoryPort users;
    private final PetRepositoryPort pets;
    private final SitterProfileRepositoryPort sitters;
    private final PasswordHasherPort passwordHasher;
    private final AccessTokenIssuerPort tokenIssuer;
    private final RefreshTokenService refreshTokens;
    private final Duration refreshLifetime;
    private final IdentityEventPublisherPort eventPublisher;
    private final Clock clock;

    public IdentityApplicationService(UserRepositoryPort users,
                                      PetRepositoryPort pets,
                                      SitterProfileRepositoryPort sitters,
                                      PasswordHasherPort passwordHasher,
                                      AccessTokenIssuerPort tokenIssuer,
                                      RefreshTokenService refreshTokens,
                                      @Value("${petvacay.jwt.refresh-lifetime:P30D}") Duration refreshLifetime,
                                      IdentityEventPublisherPort eventPublisher,
                                      Clock clock) {
        this.users = users;
        this.pets = pets;
        this.sitters = sitters;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokens = refreshTokens;
        this.refreshLifetime = refreshLifetime;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserAccount register(RegisterUserCommand command) {
        if (users.existsByEmail(command.email().toLowerCase())) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_EMAIL_ALREADY_REGISTERED", "Email ya registrado",
                    "Ya existe una cuenta asociada a ese correo electrónico.");
        }

        Set<Role> roles = command.asSitter()
                ? EnumSet.of(Role.ROLE_CLIENT, Role.ROLE_SITTER)
                : EnumSet.of(Role.ROLE_CLIENT);

        UserAccount user = UserAccount.register(UUID.randomUUID(), command.email(),
                passwordHasher.hash(command.rawPassword()), command.firstName(), command.lastName(),
                command.birthDate(), roles, clock.instant());

        return users.save(user);
    }

    /**
     * Valida credenciales y emite un token de acceso.
     *
     * <p>El mensaje de error es deliberadamente identico tanto si el email no
     * existe como si la contrasena es incorrecta: distinguirlos permitiria
     * enumerar que direcciones estan registradas en la plataforma. Por el mismo
     * motivo se calcula el hash aunque el usuario no exista, para no filtrar la
     * respuesta por diferencia de tiempo.
     */
    @Override
    @Transactional
    public LoginResult login(String email, String rawPassword) {
        Optional<UserAccount> maybeUser = users.findByEmail(email.toLowerCase());

        boolean credentialsValid = maybeUser
                .map(user -> passwordHasher.matches(rawPassword, user.passwordHash()))
                .orElseGet(() -> {
                    passwordHasher.matches(rawPassword, DUMMY_HASH);
                    return false;
                });

        if (!credentialsValid) {
            throw new BusinessRuleViolationException("ERR_INVALID_CREDENTIALS", "Credenciales inválidas",
                    401, "El correo electrónico o la contraseña no son correctos.");
        }

        UserAccount user = maybeUser.orElseThrow();
        if (!user.isActive()) {
            throw BusinessRuleViolationException.conflict("ERR_ACCOUNT_DISABLED", "Cuenta desactivada",
                    "Esta cuenta ha sido desactivada por el equipo de administración.");
        }

        String refreshValue = refreshTokens.newTokenValue();
        refreshTokens.save(RefreshToken.issueNewFamily(user.id(), refreshTokens.hash(refreshValue),
                clock.instant(), clock.instant().plus(refreshLifetime)));

        return buildResult(user, refreshValue);
    }

    /**
     * Rota el token de refresco y emite un token de acceso nuevo.
     *
     * <p>La rotación es lo que acota el daño de un robo: cada token vale para un
     * único uso. Si el atacante lo usa primero, la víctima recibirá un error al
     * intentar refrescar; si lo usa la víctima, el del atacante queda inservible.
     * En ambos casos el token consumido vuelve a aparecer, y esa reaparición es
     * la señal que dispara la revocación de toda la familia.
     */
    @Override
    @Transactional
    public LoginResult refresh(String refreshTokenValue) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(refreshTokens.hash(refreshTokenValue))
                .orElseThrow(() -> invalidRefresh());

        if (stored.isReuse(now)) {
            // Dos partes tienen la misma credencial: se invalidan todas las
            // sesiones de esa familia y el usuario tendrá que volver a entrar.
            refreshTokens.revokeFamily(stored.familyId(), now);
            log.warn("Reuso de token de refresco detectado para el usuario {}: familia {} revocada",
                    stored.userId(), stored.familyId());
            throw invalidRefresh();
        }

        if (!stored.isUsable(now)) {
            throw invalidRefresh();
        }

        UserAccount user = users.findById(stored.userId()).orElseThrow(this::invalidRefresh);
        if (!user.isActive()) {
            refreshTokens.revokeAllForUser(user.id(), now);
            throw invalidRefresh();
        }

        stored.consume(now);
        refreshTokens.save(stored);

        String nextValue = refreshTokens.newTokenValue();
        refreshTokens.save(RefreshToken.rotate(user.id(), stored.familyId(),
                refreshTokens.hash(nextValue), now, now.plus(refreshLifetime)));

        return buildResult(user, nextValue);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenValue) {
        // Sin fallo si el token no existe: cerrar sesión debe ser idempotente y no
        // debe revelar si un token concreto era válido.
        refreshTokens.findByTokenHash(refreshTokens.hash(refreshTokenValue))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant()));
    }

    private LoginResult buildResult(UserAccount user, String refreshValue) {
        boolean sitterVerified = user.hasRole(Role.ROLE_SITTER)
                && sitters.findById(user.id()).map(SitterProfile::isSearchable).orElse(false);

        AccessTokenIssuerPort.IssuedToken token = tokenIssuer.issue(user, sitterVerified);
        return new LoginResult(user, sitterVerified, token.token(), token.tokenType(),
                token.expiresInSeconds(), refreshValue, refreshLifetime.toSeconds());
    }

    private BusinessRuleViolationException invalidRefresh() {
        return new BusinessRuleViolationException("ERR_INVALID_REFRESH_TOKEN", "Sesión no válida", 401,
                "Tu sesión ha caducado. Vuelve a iniciar sesión.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccount getById(UUID userId) {
        return requireUser(userId);
    }

    @Override
    @Transactional
    public Pet addPet(AddPetCommand command) {
        requireUser(command.ownerId());
        Pet pet = new Pet(UUID.randomUUID(), command.ownerId(), command.name(), command.species(),
                command.breed(), command.ageYears(), command.weightKg(), command.medicalNeeds(),
                command.dietaryRestrictions(), command.sociability());
        return pets.save(pet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pet> listPets(UUID ownerId) {
        return pets.findByOwnerId(ownerId);
    }

    @Override
    @Transactional
    public void removePet(UUID ownerId, UUID petId) {
        Pet pet = pets.findById(petId).orElseThrow(() -> new ResourceNotFoundException(
                "ERR_PET_NOT_FOUND", "No existe la mascota " + petId));
        if (!pet.ownerId().equals(ownerId)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_PET_NOT_OWNED", "Mascota ajena",
                    "No puedes eliminar una mascota que no pertenece a tu perfil.");
        }
        pets.deleteById(petId);
    }

    @Override
    @Transactional
    public SitterProfile createProfile(CreateSitterProfileCommand command) {
        UserAccount user = requireUser(command.sitterId());
        if (!user.hasRole(Role.ROLE_SITTER)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_NOT_A_SITTER", "Rol insuficiente",
                    "La cuenta no tiene el rol de cuidador y no puede publicar un perfil de servicios.");
        }
        if (sitters.findById(command.sitterId()).isPresent()) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_SITTER_PROFILE_EXISTS", "Perfil duplicado",
                    "Esta cuenta ya tiene un perfil de cuidador creado.");
        }

        SitterProfile profile = SitterProfile.create(command.sitterId(), command.description(),
                command.homeType(), new GeoLocation(command.latitude(), command.longitude()),
                toMoneyMap(command.servicePrices()), command.maxSimultaneousPets(),
                command.acceptedSpecies(), command.maxPetWeightKg(), command.photoUrl());

        return persist(profile);
    }

    @Override
    @Transactional
    public SitterProfile updateProfile(UpdateSitterProfileCommand command) {
        SitterProfile profile = requireSitter(command.sitterId());
        profile.updatelisting(command.description(), command.homeType(),
                new GeoLocation(command.latitude(), command.longitude()),
                toMoneyMap(command.servicePrices()), command.maxSimultaneousPets(),
                command.acceptedSpecies(), command.maxPetWeightKg(), command.photoUrl(), clock.instant());
        return persist(profile);
    }

    /** Todas las tarifas de un cuidador viajan en EUR, igual que el resto de la plataforma. */
    private static Map<ServiceType, Money> toMoneyMap(Map<ServiceType, BigDecimal> prices) {
        Map<ServiceType, Money> result = new EnumMap<>(ServiceType.class);
        if (prices != null) {
            prices.forEach((type, amount) -> result.put(type, new Money(amount, Money.EUR)));
        }
        return result;
    }

    @Override
    @Transactional
    public SitterProfile submitIdentityDocument(UUID sitterId, String documentReference) {
        SitterProfile profile = requireSitter(sitterId);
        profile.submitIdentityDocument(documentReference);
        return persist(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public SitterProfile getProfile(UUID sitterId) {
        return requireSitter(sitterId);
    }

    @Override
    @Transactional
    public SitterProfile approve(UUID sitterId) {
        SitterProfile profile = requireSitter(sitterId);
        profile.approve(clock.instant());
        return persist(profile);
    }

    @Override
    @Transactional
    public SitterProfile reject(UUID sitterId, String reason) {
        SitterProfile profile = requireSitter(sitterId);
        profile.reject(reason, clock.instant());
        return persist(profile);
    }

    /**
     * Expulsion de una cuenta. Revocar los tokens de refresco es lo que la hace
     * efectiva de verdad: sin ello el expulsado seguiria renovando su sesion
     * indefinidamente, porque {@code refresh} le devolveria un token nuevo antes
     * de que nadie mirara si su cuenta sigue activa.
     */
    @Override
    @Transactional
    public UserAccount deactivate(UUID userId) {
        UserAccount user = requireUser(userId);
        if (user.hasRole(Role.ROLE_ADMIN)) {
            throw BusinessRuleViolationException.conflict(
                    "ERR_CANNOT_DEACTIVATE_ADMIN", "Cuenta protegida",
                    "Una cuenta de administración no puede expulsarse desde el panel.");
        }

        user.deactivate();
        UserAccount saved = users.save(user);
        refreshTokens.revokeAllForUser(userId, clock.instant());
        log.info("Cuenta {} desactivada por un administrador; sesiones revocadas", userId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SitterProfile> listPendingVerification() {
        return sitters.findByVerificationStatusPending();
    }

    private SitterProfile persist(SitterProfile profile) {
        SitterProfile saved = sitters.save(profile);
        List<DomainEvent> events = profile.pullPendingEvents().stream()
                .map(event -> enrichForIntegration(event, saved))
                .filter(java.util.Objects::nonNull)
                .toList();
        eventPublisher.publish(events);
        return saved;
    }

    /**
     * Traduce los eventos "finos" que {@code SitterProfile} levanta
     * ({@code CuidadorVerificadoEvent}, {@code PerfilCuidadorActualizadoEvent}) al
     * evento de integracion {@code CuidadorIndexadoEvent}, que lleva el snapshot
     * completo que {@code search-service} necesita para reproyectar sin volver a
     * consultar este servicio (ver el javadoc de {@code IdentityEvents}).
     *
     * <p>Si el perfil no es indexable todavia (KYC pendiente o rechazado) el evento
     * se descarta: no tiene sentido publicar una actualizacion de un cuidador que
     * no debe aparecer en el buscador. Esto reproduce exactamente la comprobacion
     * {@code isSearchable()} que antes de la Fase 4 hacia el propio proyector.
     */
    private DomainEvent enrichForIntegration(DomainEvent event, SitterProfile profile) {
        boolean needsEnrichment = event instanceof IdentityEvents.CuidadorVerificadoEvent
                || event instanceof IdentityEvents.PerfilCuidadorActualizadoEvent;
        if (!needsEnrichment) {
            return event;
        }
        if (!profile.isSearchable()) {
            return null;
        }

        String firstName = users.findById(profile.sitterId()).map(UserAccount::firstName).orElse("Cuidador");
        Map<ServiceType, BigDecimal> servicePrices = new EnumMap<>(ServiceType.class);
        profile.servicePrices().forEach((type, money) -> servicePrices.put(type, money.amount()));

        return new IdentityEvents.CuidadorIndexadoEvent(
                UUID.randomUUID(), clock.instant(), profile.sitterId(), firstName, profile.homeType(),
                profile.location().latitude(), profile.location().longitude(),
                servicePrices, profile.minPrice().currency().getCurrencyCode(),
                profile.maxSimultaneousPets(), profile.acceptedSpecies(), profile.maxPetWeightKg(),
                profile.photoUrl());
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException(
                "ERR_USER_NOT_FOUND", "No existe el usuario " + userId));
    }

    private SitterProfile requireSitter(UUID sitterId) {
        return sitters.findById(sitterId).orElseThrow(() -> new ResourceNotFoundException(
                "ERR_SITTER_NOT_FOUND", "No existe el perfil de cuidador " + sitterId));
    }
}
