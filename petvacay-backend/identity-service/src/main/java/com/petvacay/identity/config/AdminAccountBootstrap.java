package com.petvacay.identity.config;

import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.ports.out.IdentityPorts.PasswordHasherPort;
import com.petvacay.identity.ports.out.IdentityPorts.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Crea la cuenta de administrador inicial si todavia no existe.
 *
 * <p>Hace falta un camino explicito para esto porque {@code register()} concede
 * siempre {@code ROLE_CLIENT} (y opcionalmente {@code ROLE_SITTER}) y **nunca**
 * {@code ROLE_ADMIN}: si el endpoint publico de registro pudiera crear
 * administradores, cualquiera podria darse de alta como tal y aprobar sus propios
 * KYC. El bootstrap escribe por el puerto de repositorio, saltandose ese caso de
 * uso a proposito, porque es aprovisionamiento de infraestructura y no una accion
 * de usuario.
 *
 * <p>Las credenciales se toman de configuracion. Si se dejan las de por defecto
 * se registra un aviso en cada arranque: son publicas (estan en este repositorio)
 * y solo valen para desarrollo.
 */
@Configuration
@ConditionalOnProperty(name = "petvacay.admin.bootstrap.enabled", havingValue = "true")
public class AdminAccountBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final String DEFAULT_PASSWORD = "admin12345";

    @Bean
    ApplicationRunner bootstrapAdminAccount(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            Clock clock,
            @Value("${petvacay.admin.bootstrap.email:admin@petvacay.test}") String email,
            @Value("${petvacay.admin.bootstrap.password:" + DEFAULT_PASSWORD + "}") String password) {

        return args -> {
            if (users.existsByEmail(email.toLowerCase())) {
                return;
            }

            UserAccount admin = UserAccount.register(UUID.randomUUID(), email,
                    passwordHasher.hash(password), "Equipo", "PetVacay",
                    LocalDate.of(1980, 1, 1), EnumSet.of(Role.ROLE_ADMIN), clock.instant());
            users.save(admin);

            if (DEFAULT_PASSWORD.equals(password)) {
                log.warn("Administrador '{}' creado con la contraseña por defecto. "
                        + "Define petvacay.admin.bootstrap.password antes de exponer este servicio.", email);
            } else {
                log.info("Cuenta de administrador '{}' creada", email);
            }
        };
    }
}
