package com.petvacay.identity.adapters.in.web;

import com.petvacay.identity.adapters.in.web.IdentityDtos.AddPetRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.CreateSitterProfileRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.LoginRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.LoginResponse;
import com.petvacay.identity.adapters.in.web.IdentityDtos.PetResponse;
import com.petvacay.identity.adapters.in.web.IdentityDtos.RegisterUserRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.RejectSitterRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.SitterProfileResponse;
import com.petvacay.identity.adapters.in.web.IdentityDtos.SubmitDocumentRequest;
import com.petvacay.identity.adapters.in.web.IdentityDtos.UserResponse;
import com.petvacay.identity.ports.in.IdentityUseCases.LoginUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.ManagePetsUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.QueryUserUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.RegisterUserUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterOnboardingUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterVerificationUseCase;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import com.petvacay.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador REST del contexto de identidad: autenticacion, registro, mascotas y
 * onboarding KYC.
 *
 * <p>Desde la Fase 5, la identidad del solicitante la aporta
 * {@code @AuthenticatedUser}, que la extrae del {@code subject} de un JWT firmado;
 * sustituye a la cabecera {@code X-User-Id} que las Fases 1-4 usaban como
 * sustituto provisional y que el propio cliente podia falsificar a voluntad.
 */
@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;
    private final QueryUserUseCase queryUser;
    private final ManagePetsUseCase managePets;
    private final SitterOnboardingUseCase sitterOnboarding;
    private final SitterVerificationUseCase sitterVerification;
    private final RefreshTokenCookies refreshCookies;

    public IdentityController(RegisterUserUseCase registerUser,
                              LoginUseCase login,
                              QueryUserUseCase queryUser,
                              ManagePetsUseCase managePets,
                              SitterOnboardingUseCase sitterOnboarding,
                              SitterVerificationUseCase sitterVerification,
                              RefreshTokenCookies refreshCookies) {
        this.registerUser = registerUser;
        this.login = login;
        this.queryUser = queryUser;
        this.managePets = managePets;
        this.sitterOnboarding = sitterOnboarding;
        this.sitterVerification = sitterVerification;
        this.refreshCookies = refreshCookies;
    }

    /**
     * Autenticación.
     *
     * <p>Devuelve el token de acceso en el cuerpo — el interceptor de Axios lo
     * necesita para la cabecera {@code Authorization} — y el de refresco en una
     * cookie HttpOnly que el JavaScript de la página no puede leer.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(login.login(request.email(), request.password()));
    }

    /**
     * Renueva el token de acceso a partir de la cookie de refresco, rotándola.
     *
     * <p>No recibe nada en el cuerpo a propósito: el token viaja en la cookie, así
     * que el frontend no llega a manejarlo nunca.
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        String token = refreshCookies.read(request)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "ERR_INVALID_REFRESH_TOKEN", "Sesión no válida", 401,
                        "Tu sesión ha caducado. Vuelve a iniciar sesión."));
        return withRefreshCookie(login.refresh(token));
    }

    /** Cierra la sesión: revoca la familia de tokens y borra la cookie. */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        refreshCookies.read(request).ifPresent(login::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(LoginUseCase.LoginResult result) {
        ResponseCookie cookie = refreshCookies.issue(result.refreshToken(), result.refreshExpiresInSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(LoginResponse.from(result));
    }

    /**
     * Perfil del usuario de la sesion actual. En Fase 5 la identidad saldra del
     * JWT y esta cabecera desaparecera.
     */
    @GetMapping("/users/me")
    public UserResponse me(@AuthenticatedUser UUID userId) {
        return UserResponse.from(queryUser.getById(userId));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse response = UserResponse.from(registerUser.register(
                new RegisterUserUseCase.RegisterUserCommand(request.email(), request.password(),
                        request.firstName(), request.lastName(), request.birthDate(), request.asSitter())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/pets")
    public ResponseEntity<PetResponse> addPet(@AuthenticatedUser UUID ownerId,
                                              @Valid @RequestBody AddPetRequest request) {
        PetResponse response = PetResponse.from(managePets.addPet(
                new ManagePetsUseCase.AddPetCommand(ownerId, request.name(), request.species(),
                        request.breed(), request.ageYears(), request.weightKg(), request.medicalNeeds(),
                        request.dietaryRestrictions(), request.sociability())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pets")
    public List<PetResponse> listPets(@AuthenticatedUser UUID ownerId) {
        return managePets.listPets(ownerId).stream().map(PetResponse::from).toList();
    }

    @DeleteMapping("/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePet(@AuthenticatedUser UUID ownerId, @PathVariable UUID petId) {
        managePets.removePet(ownerId, petId);
    }

    @PostMapping("/sitters/profile")
    public ResponseEntity<SitterProfileResponse> createProfile(
            @AuthenticatedUser UUID sitterId,
            @Valid @RequestBody CreateSitterProfileRequest request) {

        SitterProfileResponse response = SitterProfileResponse.from(sitterOnboarding.createProfile(
                new SitterOnboardingUseCase.CreateSitterProfileCommand(sitterId, request.description(),
                        request.homeType(), request.latitude(), request.longitude(),
                        request.pricePerNight(), request.maxSimultaneousPets(),
                        request.acceptedSpecies(), request.maxPetWeightKg())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Actualiza tarifas, capacidad o restricciones de un perfil ya publicado.
     * Si el perfil esta verificado, la actualizacion reproyecta el buscador
     * (ver {@code IdentityApplicationService.enrichForIntegration}).
     */
    @PutMapping("/sitters/profile")
    public SitterProfileResponse updateProfile(@AuthenticatedUser UUID sitterId,
                                               @Valid @RequestBody CreateSitterProfileRequest request) {
        return SitterProfileResponse.from(sitterOnboarding.updateProfile(
                new SitterOnboardingUseCase.UpdateSitterProfileCommand(sitterId, request.description(),
                        request.homeType(), request.latitude(), request.longitude(),
                        request.pricePerNight(), request.maxSimultaneousPets(),
                        request.acceptedSpecies(), request.maxPetWeightKg())));
    }

    @GetMapping("/sitters/{sitterId}/profile")
    public SitterProfileResponse getProfile(@PathVariable UUID sitterId) {
        return SitterProfileResponse.from(sitterOnboarding.getProfile(sitterId));
    }

    /**
     * Contrato interno consumido por {@code booking-service} para tarificar y
     * validar una reserva (adaptador {@code SitterCatalogHttpAdapter}).
     *
     * <p>No es parte de la API publica del Gateway: expone solo los campos que
     * booking-service necesita, no el perfil completo. En una topologia con red
     * interna separada esta ruta viviria en un puerto o base path distinto para
     * que el Gateway nunca la exponga al navegador; aqui se mantiene bajo
     * {@code /internal} como convencion suficiente para el alcance del proyecto.
     */
    @GetMapping("/internal/sitters/{sitterId}/summary")
    public SitterSummaryResponse summary(@PathVariable UUID sitterId) {
        var profile = sitterOnboarding.getProfile(sitterId);
        String firstName = queryUser.getById(sitterId).firstName();
        return new SitterSummaryResponse(profile.sitterId(), firstName, profile.isSearchable(),
                profile.pricePerNight().amount(), profile.pricePerNight().currency().getCurrencyCode(),
                profile.maxSimultaneousPets());
    }

    public record SitterSummaryResponse(
            UUID sitterId,
            String firstName,
            boolean verified,
            java.math.BigDecimal pricePerNight,
            String currency,
            int maxSimultaneousPets) {
    }

    /**
     * Subida del documento de identidad. El fichero se almacena fuera de la BD y
     * aqui solo viaja su referencia; el procesado real es asincrono (negocio.md 4.1).
     */
    @PostMapping("/sitters/kyc/document")
    public SitterProfileResponse submitDocument(@AuthenticatedUser UUID sitterId,
                                                @Valid @RequestBody SubmitDocumentRequest request) {
        return SitterProfileResponse.from(
                sitterOnboarding.submitIdentityDocument(sitterId, request.documentReference()));
    }

    // ------------------------------------------------------------- administracion

    @GetMapping("/admin/sitters/pending")
    public List<SitterProfileResponse> listPending() {
        return sitterVerification.listPendingVerification().stream()
                .map(SitterProfileResponse::from).toList();
    }

    @PostMapping("/admin/sitters/{sitterId}/approve")
    public SitterProfileResponse approve(@PathVariable UUID sitterId) {
        return SitterProfileResponse.from(sitterVerification.approve(sitterId));
    }

    @PostMapping("/admin/sitters/{sitterId}/reject")
    public SitterProfileResponse reject(@PathVariable UUID sitterId,
                                        @Valid @RequestBody RejectSitterRequest request) {
        return SitterProfileResponse.from(sitterVerification.reject(sitterId, request.reason()));
    }
}
