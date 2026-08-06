package com.petvacay.identity.ports.in;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.SitterProfile;
import com.petvacay.identity.domain.model.Sociability;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.domain.model.VerificationStatus;
import com.petvacay.shared.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Casos de uso de entrada del contexto de identidad. */
public final class IdentityUseCases {

    private IdentityUseCases() {
    }

    public interface RegisterUserUseCase {

        UserAccount register(RegisterUserCommand command);

        record RegisterUserCommand(
                String email,
                String rawPassword,
                String firstName,
                String lastName,
                LocalDate birthDate,
                boolean asSitter) {
        }
    }

    public interface QueryUserUseCase {

        UserAccount getById(UUID userId);
    }

    /** Autenticacion con email y contrasena a cambio de un token de acceso (Fase 5). */
    public interface LoginUseCase {

        LoginResult login(String email, String rawPassword);

        /**
         * Canjea un token de refresco por un token de acceso nuevo, rotando el de
         * refresco. Si el presentado ya se habia usado, revoca la familia entera
         * y falla: es la senal de que la credencial esta en manos de dos partes.
         */
        LoginResult refresh(String refreshToken);

        /** Cierra la sesion revocando el token de refresco presentado. */
        void logout(String refreshToken);

        record LoginResult(
                UserAccount user,
                boolean sitterVerified,
                String accessToken,
                String tokenType,
                long expiresInSeconds,
                String refreshToken,
                long refreshExpiresInSeconds) {
        }
    }

    public interface ManagePetsUseCase {

        Pet addPet(AddPetCommand command);

        List<Pet> listPets(UUID ownerId);

        /**
         * Fichas de un conjunto concreto de mascotas. Lo consume el contrato
         * interno que usa booking-service para enseñarle al cuidador a que
         * animal se compromete; la autorizacion la aplica alli, que es quien
         * sabe si quien pregunta es parte de esa reserva.
         */
        List<Pet> findByIds(List<UUID> petIds);

        void removePet(UUID ownerId, UUID petId);

        record AddPetCommand(
                UUID ownerId,
                String name,
                Species species,
                String breed,
                int ageYears,
                BigDecimal weightKg,
                String medicalNeeds,
                String dietaryRestrictions,
                Sociability sociability) {
        }
    }

    /** Onboarding y KYC del cuidador (negocio.md seccion 4.1). */
    public interface SitterOnboardingUseCase {

        SitterProfile createProfile(CreateSitterProfileCommand command);

        /**
         * Actualiza tarifas, capacidad o restricciones de un perfil ya creado.
         *
         * <p>Wired en la Fase 4: {@code SitterProfile.updatelisting()} y el evento
         * {@code PerfilCuidadorActualizadoEvent} llevaban existiendo desde Fase 1 sin
         * ningun caso de uso que los invocara. Se completa aqui en vez de dejarlos
         * como codigo muerto, aprovechando que la traduccion a evento de integracion
         * "gordo" (necesaria para que search-service pueda reproyectar sin volver a
         * consultar este servicio) ya se implementa para {@code approve()}.
         */
        SitterProfile updateProfile(UpdateSitterProfileCommand command);

        SitterProfile submitIdentityDocument(UUID sitterId, String documentReference);

        SitterProfile getProfile(UUID sitterId);

        record CreateSitterProfileCommand(
                UUID sitterId,
                String description,
                HomeType homeType,
                double latitude,
                double longitude,
                String address,
                String streetNumber,
                String floorDoor,
                Map<ServiceType, BigDecimal> servicePrices,
                int maxSimultaneousPets,
                Set<Species> acceptedSpecies,
                BigDecimal maxPetWeightKg,
                String photoUrl) {
        }

        record UpdateSitterProfileCommand(
                UUID sitterId,
                String description,
                HomeType homeType,
                double latitude,
                double longitude,
                String address,
                String streetNumber,
                String floorDoor,
                Map<ServiceType, BigDecimal> servicePrices,
                int maxSimultaneousPets,
                Set<Species> acceptedSpecies,
                BigDecimal maxPetWeightKg,
                String photoUrl) {
        }
    }

    /**
     * Expulsion de cuentas por el administrador (negocio.md 2.3: "puede expulsar
     * de manera fulminante a usuarios o cuidadores que infrinjan los terminos").
     */
    public interface UserModerationUseCase {

        /**
         * Cuentas de la plataforma, paginadas y filtrables por texto libre
         * (nombre, apellidos, email), por rol y por si estan activas.
         */
        Page<UserAccount> listUsers(UserQuery query);

        /**
         * @param role   rol exigido, o {@code null} para no filtrar por rol.
         * @param active {@code null} para incluir tanto activas como expulsadas.
         */
        record UserQuery(String text, Role role, Boolean active, int page, int size) {
        }

        /** Corrige los datos de contacto de una cuenta desde el back-office. */
        UserAccount updateContactDetails(UpdateUserCommand command);

        /**
         * Desactiva la cuenta y revoca sus tokens de refresco, de modo que la
         * sesion abierta no pueda renovarse. El token de acceso ya emitido sigue
         * siendo valido hasta que caduca (minutos): es el precio conocido de una
         * autenticacion sin estado, y revisarlo en cada peticion exigiria
         * consultar la base de datos de identidad desde los cuatro servicios.
         */
        UserAccount deactivate(UUID userId);

        /** Deshace una expulsion: el borrado es logico, no destructivo. */
        UserAccount reactivate(UUID userId);

        record UpdateUserCommand(UUID userId, String email, String firstName, String lastName) {
        }
    }

    /** Operaciones reservadas al administrador de la plataforma. */
    public interface SitterVerificationUseCase {

        SitterProfile approve(UUID sitterId);

        SitterProfile reject(UUID sitterId, String reason);

        List<SitterProfile> listPendingVerification();

        /**
         * Ficha completa de un cuidador para el back-office: su perfil publicado
         * junto a los datos de la cuenta que hay detras.
         *
         * <p>Van juntos porque el administrador que verifica un KYC necesita
         * exactamente eso: contrastar el documento subido con el nombre real de
         * la persona. Repartido en dos llamadas, la pantalla tendria que
         * recomponerlo y podria enseñar una a medias.
         */
        SitterDossier getDossier(UUID sitterId);

        /**
         * Cuidadores paginados, con filtro por estado de verificacion y por texto
         * libre sobre nombre, apellidos y email.
         */
        Page<SitterDossier> searchSitters(SitterQuery query);

        record SitterQuery(VerificationStatus status, String text, int page, int size) {
        }

        /** Perfil de cuidador y cuenta asociada, resueltos de una vez. */
        record SitterDossier(SitterProfile profile, UserAccount account) {
        }
    }
}
