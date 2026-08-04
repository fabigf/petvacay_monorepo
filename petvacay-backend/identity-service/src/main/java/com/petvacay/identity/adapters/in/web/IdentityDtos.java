package com.petvacay.identity.adapters.in.web;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.Sociability;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.identity.ports.in.IdentityUseCases.LoginUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
            @NotNull @Positive(message = "La tarifa por noche debe ser mayor que cero") BigDecimal pricePerNight,
            @Min(value = 1, message = "La capacidad mínima es 1 mascota") int maxSimultaneousPets,
            Set<Species> acceptedSpecies,
            BigDecimal maxPetWeightKg) {
    }

    public record SubmitDocumentRequest(
            @NotBlank(message = "La referencia del documento es obligatoria") String documentReference) {
    }

    public record RejectSitterRequest(@NotBlank(message = "El motivo es obligatorio") String reason) {
    }

    public record SitterProfileResponse(
            UUID sitterId, String description, HomeType homeType, double latitude, double longitude,
            BigDecimal pricePerNight, String currency, int maxSimultaneousPets,
            Set<Species> acceptedSpecies, BigDecimal maxPetWeightKg,
            VerificationStatus verificationStatus, boolean searchable,
            String rejectionReason, Instant verifiedAt) {

        public static SitterProfileResponse from(SitterProfile profile) {
            return new SitterProfileResponse(
                    profile.sitterId(), profile.description(), profile.homeType(),
                    profile.location().latitude(), profile.location().longitude(),
                    profile.pricePerNight().amount(), profile.pricePerNight().currency().getCurrencyCode(),
                    profile.maxSimultaneousPets(), profile.acceptedSpecies(), profile.maxPetWeightKg(),
                    profile.verificationStatus(), profile.isSearchable(),
                    profile.rejectionReason(), profile.verifiedAt());
        }
    }
}
