package com.petvacay.identity.adapters.in.web;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.Sociability;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.identity.ports.in.IdentityUseCases.LoginUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterVerificationUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contratos JSON del contexto de identidad. */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record RegisterUserRequest(
            @NotBlank @Email(message = "El correo electrónico no es válido") String email,
            @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
            @NotBlank(message = "El nombre es obligatorio") String firstName,
            @NotBlank(message = "Los apellidos son obligatorios") String lastName,
            @NotNull @Past(message = "La fecha de nacimiento debe ser pasada") LocalDate birthDate,
            boolean asSitter) {
    }

    public record UserResponse(UUID userId, String email, String firstName, String lastName,
                               Set<Role> roles, boolean active) {

        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName(),
                    user.roles(), user.isActive());
        }
    }

    /**
     * Correccion de los datos de contacto desde el back-office. No incluye roles
     * ni fecha de nacimiento a proposito: cambiar eso no es corregir una errata,
     * es alterar quien es alguien y que puede hacer en la plataforma.
     */
    public record UpdateUserRequest(
            @NotBlank @Email(message = "El correo electrónico no es válido") String email,
            @NotBlank(message = "El nombre es obligatorio") String firstName,
            @NotBlank(message = "Los apellidos son obligatorios") String lastName) {
    }

    public record LoginRequest(
            @NotBlank @Email(message = "El correo electrónico no es válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    /**
     * Respuesta del login.
     *
     * <p>Contiene el token de ACCESO y el perfil del usuario (para evitar una
     * segunda llamada a {@code /users/me}). El token de REFRESCO no aparece aqui
     * a proposito: viaja en una cookie HttpOnly para que el JavaScript de la
     * pagina no pueda leerlo.
     */
    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserResponse user,
            boolean sitterVerified) {

        public static LoginResponse from(LoginUseCase.LoginResult result) {
            return new LoginResponse(result.accessToken(), result.tokenType(),
                    result.expiresInSeconds(), UserResponse.from(result.user()),
                    result.sitterVerified());
        }
    }

    public record AddPetRequest(
            @NotBlank(message = "El nombre de la mascota es obligatorio") String name,
            @NotNull(message = "La especie es obligatoria") Species species,
            String breed,
            @Min(value = 0, message = "La edad no puede ser negativa") int ageYears,
            @NotNull @Positive(message = "El peso debe ser mayor que cero") BigDecimal weightKg,
            String medicalNeeds,
            String dietaryRestrictions,
            @NotNull(message = "El nivel de sociabilidad es obligatorio") Sociability sociability) {
    }

    public record PetResponse(UUID petId, UUID ownerId, String name, Species species, String breed,
                              int ageYears, BigDecimal weightKg, String medicalNeeds,
                              String dietaryRestrictions, Sociability sociability,
                              boolean specialMedicalNeeds) {

        public static PetResponse from(Pet pet) {
            return new PetResponse(pet.id(), pet.ownerId(), pet.name(), pet.species(), pet.breed(),
                    pet.ageYears(), pet.weightKg(), pet.medicalNeeds(), pet.dietaryRestrictions(),
                    pet.sociability(), pet.hasSpecialMedicalNeeds());
        }
    }

    public record CreateSitterProfileRequest(
            @Size(max = 4000) String description,
            @NotNull(message = "El tipo de vivienda es obligatorio") HomeType homeType,
            double latitude,
            double longitude,
            // Direccion legible de la que salieron las coordenadas. Se guarda para
            // que el cuidador pueda releer y corregir lo que puso: unas coordenadas
            // sueltas no se pueden ni verificar ni editar a mano.
            @Size(max = 500) String address,
            // Los pide la aplicacion siempre (la planta, solo en apartamento) pero
            // la regla vive en SitterProfile, que es quien sabe el tipo de
            // vivienda; aqui solo se acota la longitud.
            @Size(max = 20) String streetNumber,
            @Size(max = 50) String floorDoor,
            @NotEmpty(message = "Debes ofrecer al menos un servicio con su tarifa")
            Map<ServiceType, @Positive(message = "La tarifa debe ser mayor que cero") BigDecimal> servicePrices,
            @Min(value = 1, message = "La capacidad mínima es 1 mascota") int maxSimultaneousPets,
            Set<Species> acceptedSpecies,
            BigDecimal maxPetWeightKg,
            @Size(max = 2000) String photoUrl) {
    }

    public record SubmitDocumentRequest(
            @NotBlank(message = "La referencia del documento es obligatoria") String documentReference) {
    }

    public record RejectSitterRequest(@NotBlank(message = "El motivo es obligatorio") String reason) {
    }

    /**
     * Perfil del cuidador.
     *
     * <p>{@code firstName} no vive en el agregado {@code SitterProfile} sino en
     * {@code UserAccount}, asi que solo viene relleno cuando quien construye la
     * respuesta tiene a mano la cuenta: en las vistas publicas del perfil, donde
     * hace falta poder decir de quien es. En las respuestas que devuelven las
     * operaciones sobre el propio perfil (alta, edicion, aprobacion) llega nulo,
     * porque quien las recibe ya sabe de quien esta hablando y no justifica una
     * consulta extra a la tabla de usuarios.
     */
    /**
     * Ficha completa de un cuidador para el back-office.
     *
     * <p>Es un DTO aparte y no el {@link SitterProfileResponse} de siempre porque
     * lleva datos que NO deben salir en la vista publica de un perfil: el correo
     * electronico, la referencia del documento de identidad subido y si la cuenta
     * sigue activa. Quien verifica un KYC necesita exactamente eso — contrastar
     * el documento con la persona — y cualquier visitante del buscador, no.
     */
    public record AdminSitterResponse(
            UUID sitterId,
            String firstName,
            String lastName,
            String email,
            LocalDate birthDate,
            boolean accountActive,
            Instant registeredAt,
            String description,
            HomeType homeType,
            double latitude,
            double longitude,
            String address,
            String streetNumber,
            String floorDoor,
            // Direccion de una linea, ya montada: el panel la enseña tal cual y
            // no tiene que saber en que orden se concatenan las tres partes.
            String fullAddress,
            Map<ServiceType, BigDecimal> servicePrices,
            String currency,
            int maxSimultaneousPets,
            Set<Species> acceptedSpecies,
            BigDecimal maxPetWeightKg,
            String photoUrl,
            VerificationStatus verificationStatus,
            String identityDocumentReference,
            boolean searchable,
            String rejectionReason,
            Instant verifiedAt) {

        public static AdminSitterResponse from(SitterVerificationUseCase.SitterDossier dossier) {
            SitterProfile profile = dossier.profile();
            UserAccount account = dossier.account();

            Map<ServiceType, BigDecimal> servicePrices = new EnumMap<>(ServiceType.class);
            profile.servicePrices().forEach((type, money) -> servicePrices.put(type, money.amount()));

            return new AdminSitterResponse(
                    profile.sitterId(),
                    account == null ? null : account.firstName(),
                    account == null ? null : account.lastName(),
                    account == null ? null : account.email(),
                    account == null ? null : account.birthDate(),
                    account != null && account.isActive(),
                    account == null ? null : account.createdAt(),
                    profile.description(), profile.homeType(),
                    profile.location().latitude(), profile.location().longitude(),
                    profile.location().address(), profile.location().streetNumber(),
                    profile.location().floorDoor(), profile.location().fullAddress(),
                    servicePrices, profile.minPrice().currency().getCurrencyCode(),
                    profile.maxSimultaneousPets(), profile.acceptedSpecies(), profile.maxPetWeightKg(),
                    profile.photoUrl(), profile.verificationStatus(),
                    profile.identityDocumentReference(), profile.isSearchable(),
                    profile.rejectionReason(), profile.verifiedAt());
        }
    }

    /** Envoltorio de paginacion; el total permite pintar el paginador. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages) {

        public static <D, R> PageResponse<R> from(com.petvacay.shared.domain.Page<D> page,
                                                  java.util.function.Function<D, R> mapper) {
            return new PageResponse<>(page.content().stream().map(mapper).toList(),
                    page.page(), page.size(), page.totalElements(), page.totalPages());
        }
    }

    /**
     * Perfil del cuidador.
     *
     * <p>La direccion ({@code address}, {@code streetNumber}, {@code floorDoor},
     * {@code fullAddress}) NO viaja en la variante publica — la que devuelve
     * {@code GET /sitters/{id}/profile}, alcanzable por cualquier cuenta
     * autenticada, no solo por quien ha reservado. Revelarla ahi permitiria a
     * cualquiera con una cuenta averiguar donde vive un cuidador sin haber
     * llegado siquiera a pagar (negocio.md 4.2, "Franja de entrega y
     * recogida"): la direccion exacta solo se entrega al cliente de una reserva
     * ya confirmada, a traves de {@code booking-service}
     * ({@code GET /bookings/{id}/handoff}).
     *
     * <p>Si se ve completa es porque la ha pedido su propio dueno (al crear,
     * editar o consultar {@code GET /sitters/profile}) o el equipo de
     * administracion, que ya tiene su propio DTO ({@link AdminSitterResponse}).
     */
    public record SitterProfileResponse(
            UUID sitterId, String firstName, String description, HomeType homeType,
            double latitude, double longitude, String address,
            String streetNumber, String floorDoor, String fullAddress,
            Map<ServiceType, BigDecimal> servicePrices, String currency, int maxSimultaneousPets,
            Set<Species> acceptedSpecies, BigDecimal maxPetWeightKg, String photoUrl,
            VerificationStatus verificationStatus, boolean searchable,
            String rejectionReason, Instant verifiedAt) {

        /** Vista del propio dueno del perfil: incluye la direccion completa. */
        public static SitterProfileResponse from(SitterProfile profile) {
            return build(profile, null, true);
        }

        /** Vista publica (buscador, ficha antes de reservar): sin direccion. */
        public static SitterProfileResponse from(SitterProfile profile, String firstName) {
            return build(profile, firstName, false);
        }

        private static SitterProfileResponse build(SitterProfile profile, String firstName,
                                                    boolean includeAddress) {
            Map<ServiceType, BigDecimal> servicePrices = new EnumMap<>(ServiceType.class);
            profile.servicePrices().forEach((type, money) -> servicePrices.put(type, money.amount()));

            return new SitterProfileResponse(
                    profile.sitterId(), firstName, profile.description(), profile.homeType(),
                    profile.location().latitude(), profile.location().longitude(),
                    includeAddress ? profile.location().address() : null,
                    includeAddress ? profile.location().streetNumber() : null,
                    includeAddress ? profile.location().floorDoor() : null,
                    includeAddress ? profile.location().fullAddress() : null,
                    servicePrices, profile.minPrice().currency().getCurrencyCode(),
                    profile.maxSimultaneousPets(), profile.acceptedSpecies(), profile.maxPetWeightKg(),
                    profile.photoUrl(), profile.verificationStatus(), profile.isSearchable(),
                    profile.rejectionReason(), profile.verifiedAt());
        }
    }
}
