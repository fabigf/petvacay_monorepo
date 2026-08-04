package com.petvacay.identity.config;

import com.petvacay.identity.domain.model.HomeType;
import com.petvacay.identity.domain.model.ServiceType;
import com.petvacay.identity.domain.model.Sociability;
import com.petvacay.identity.domain.model.Species;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.ports.in.IdentityUseCases.ManagePetsUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.RegisterUserUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterOnboardingUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.SitterVerificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Carga un conjunto de datos realista al arrancar en perfil dev, para que el
 * frontend trabaje contra respuestas reales del backend desde el primer minuto.
 *
 * <p>Los cuidadores se dan de alta recorriendo el flujo completo de onboarding
 * (perfil -> documento -> aprobacion), de modo que la proyeccion del buscador se
 * construye por eventos igual que en produccion, sin insertar filas a mano.
 *
 * <p>Desde la Fase 4 este seeder ya no vacia el indice de busqueda (no tiene
 * acceso al almacen de {@code search-service}, que es un proceso aparte): esa
 * responsabilidad la asume el propio {@code search-service} sobre su seeder.
 */
@Configuration
@ConditionalOnProperty(name = "petvacay.seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    @Bean
    ApplicationRunner seedDevData(RegisterUserUseCase registerUser,
                                  ManagePetsUseCase managePets,
                                  SitterOnboardingUseCase onboarding,
                                  SitterVerificationUseCase verification) {
        return args -> {
            UUID lucia = seedClient(registerUser, managePets);

            // Ofrece alojamiento y paseos, cada uno con su propia tarifa.
            seedSitter(registerUser, onboarding, verification, "carlos.ruiz@petvacay.test", "Carlos", "Ruiz",
                    "Casa con jardín vallado en Chamberí. 8 años cuidando perros de todos los tamaños.",
                    HomeType.HOUSE_GARDEN, 40.4378, -3.7038,
                    servicePrices(ServiceType.NIGHT_ACCOMMODATION, "25.00", ServiceType.WALKING, "12.00"),
                    3, EnumSet.of(Species.DOG, Species.CAT), "40.00", true);

            // Solo guarderia de dia: no todos los cuidadores ofrecen los tres servicios.
            seedSitter(registerUser, onboarding, verification, "marta.lopez@petvacay.test", "Marta", "Lopez",
                    "Apartamento tranquilo cerca del Retiro, ideal para gatos y perros pequeños.",
                    HomeType.APARTMENT, 40.4153, -3.6844,
                    servicePrices(ServiceType.DAY_CARE, "18.50"),
                    2, EnumSet.of(Species.CAT, Species.RABBIT, Species.DOG), "10.00", true);

            // Los tres servicios: el sitio grande da para todo.
            seedSitter(registerUser, onboarding, verification, "javier.moreno@petvacay.test", "Javier", "Moreno",
                    "Finca rural a 20 minutos del centro con 2000 m2 para correr.",
                    HomeType.RURAL_HOUSE, 40.5100, -3.6500,
                    servicePrices(ServiceType.NIGHT_ACCOMMODATION, "32.00",
                            ServiceType.DAY_CARE, "22.00", ServiceType.WALKING, "14.00"),
                    5, EnumSet.allOf(Species.class), null, true);

            // Cuidadora aun pendiente de KYC: no debe aparecer en el buscador
            seedSitter(registerUser, onboarding, verification, "elena.pending@petvacay.test", "Elena", "Vidal",
                    "Perfil recién creado, pendiente de verificación de identidad.",
                    HomeType.APARTMENT, 40.4200, -3.7000,
                    servicePrices(ServiceType.NIGHT_ACCOMMODATION, "20.00"),
                    2, EnumSet.of(Species.DOG), "15.00", false);

            log.info("Datos de desarrollo cargados. Cliente de pruebas (X-User-Id): {}", lucia);
        };
    }

    private static Map<ServiceType, BigDecimal> servicePrices(Object... typeAndPrice) {
        Map<ServiceType, BigDecimal> prices = new EnumMap<>(ServiceType.class);
        for (int i = 0; i < typeAndPrice.length; i += 2) {
            prices.put((ServiceType) typeAndPrice[i], new BigDecimal((String) typeAndPrice[i + 1]));
        }
        return prices;
    }

    private UUID seedClient(RegisterUserUseCase registerUser, ManagePetsUseCase managePets) {
        UserAccount client = registerUser.register(new RegisterUserUseCase.RegisterUserCommand(
                "lucia.garcia@petvacay.test", "password123", "Lucia", "Garcia",
                LocalDate.of(1992, 5, 14), false));

        managePets.addPet(new ManagePetsUseCase.AddPetCommand(client.id(), "Nala", Species.DOG,
                "Border Collie", 4, new BigDecimal("18.50"),
                "Medicación oral para la artrosis, 1 pastilla cada mañana",
                "Pienso sin cereales", Sociability.SOCIABLE));

        managePets.addPet(new ManagePetsUseCase.AddPetCommand(client.id(), "Simba", Species.CAT,
                "Común europeo", 2, new BigDecimal("4.20"), null,
                "Solo comida húmeda", Sociability.SELECTIVE));

        return client.id();
    }

    private void seedSitter(RegisterUserUseCase registerUser, SitterOnboardingUseCase onboarding,
                            SitterVerificationUseCase verification, String email, String firstName,
                            String lastName, String description, HomeType homeType, double lat, double lng,
                            Map<ServiceType, BigDecimal> servicePrices, int maxPets, Set<Species> species,
                            String maxWeight, boolean approve) {

        UserAccount sitter = registerUser.register(new RegisterUserUseCase.RegisterUserCommand(
                email, "password123", firstName, lastName, LocalDate.of(1988, 3, 22), true));

        onboarding.createProfile(new SitterOnboardingUseCase.CreateSitterProfileCommand(
                sitter.id(), description, homeType, lat, lng, servicePrices, maxPets,
                species, maxWeight == null ? null : new BigDecimal(maxWeight),
                "https://i.pravatar.cc/400?u=" + email));

        onboarding.submitIdentityDocument(sitter.id(), "s3://petvacay-kyc/" + sitter.id() + "/dni.jpg");

        if (approve) {
            verification.approve(sitter.id());
        }
        log.info("Cuidador de ejemplo {} ({}) -> {}", firstName, sitter.id(),
                approve ? "VERIFICADO" : "PENDIENTE_VERIFICACION");
    }
}
