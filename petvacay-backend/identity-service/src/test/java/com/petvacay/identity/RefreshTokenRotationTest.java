package com.petvacay.identity;

import com.petvacay.identity.ports.in.IdentityUseCases.LoginUseCase;
import com.petvacay.identity.ports.in.IdentityUseCases.RegisterUserUseCase;
import com.petvacay.shared.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija el comportamiento de los tokens de refresco: rotación, revocación real y
 * detección de reuso.
 *
 * <p>Es la parte de la seguridad con la lógica menos evidente, y la que un
 * cambio descuidado podría desactivar sin que nada más fallara: si la rotación
 * dejara de invalidar el token anterior, todo seguiría funcionando en apariencia
 * mientras se pierde silenciosamente la protección contra el robo de sesión.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "petvacay.seed.enabled=false",
        "petvacay.admin.bootstrap.enabled=false",
})
@DisplayName("identity-service: rotación de tokens de refresco")
class RefreshTokenRotationTest {

    @Autowired
    private RegisterUserUseCase registerUser;

    @Autowired
    private LoginUseCase login;

    private String email;

    @BeforeEach
    void setUp() {
        email = "refresh-" + UUID.randomUUID() + "@petvacay.test";
        registerUser.register(new RegisterUserUseCase.RegisterUserCommand(
                email, "password123", "Nuria", "Vega", LocalDate.of(1990, 1, 1), false));
    }

    @Test
    @DisplayName("el login entrega un token de acceso y otro de refresco distintos")
    void loginIssuesBothTokens() {
        LoginUseCase.LoginResult result = login.login(email, "password123");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(result.accessToken());
        assertThat(result.expiresInSeconds()).isLessThanOrEqualTo(result.refreshExpiresInSeconds());
    }

    @Test
    @DisplayName("refrescar entrega credenciales nuevas y rota el token de refresco")
    void refreshRotatesTheToken() {
        LoginUseCase.LoginResult first = login.login(email, "password123");
        LoginUseCase.LoginResult second = login.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("un token de refresco ya usado no vuelve a servir")
    void consumedTokenIsRejected() {
        LoginUseCase.LoginResult first = login.login(email, "password123");
        login.refresh(first.refreshToken());

        assertThatThrownBy(() -> login.refresh(first.refreshToken()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .isEqualTo("ERR_INVALID_REFRESH_TOKEN");
    }

    /**
     * El escenario que justifica toda la mecánica: si un token robado y el
     * legítimo se usan ambos, la reaparición del ya consumido delata el robo y
     * se corta la familia entera, no solo el token concreto.
     */
    @Test
    @DisplayName("reusar un token consumido revoca toda la familia, incluido el vigente")
    void reuseRevokesTheWholeFamily() {
        LoginUseCase.LoginResult first = login.login(email, "password123");
        LoginUseCase.LoginResult second = login.refresh(first.refreshToken());

        // El atacante presenta el token viejo: se detecta el reuso.
        assertThatThrownBy(() -> login.refresh(first.refreshToken()))
                .isInstanceOf(BusinessRuleViolationException.class);

        // Y el token legítimo, que era válido hasta ahora, tambien queda anulado.
        assertThatThrownBy(() -> login.refresh(second.refreshToken()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .isEqualTo("ERR_INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("cerrar sesión revoca el token: es revocación real, no solo borrado local")
    void logoutRevokesTheToken() {
        LoginUseCase.LoginResult session = login.login(email, "password123");
        login.logout(session.refreshToken());

        assertThatThrownBy(() -> login.refresh(session.refreshToken()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .isEqualTo("ERR_INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("un token de refresco inventado se rechaza")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> login.refresh("token-que-nadie-ha-emitido"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("las credenciales incorrectas no revelan si el email existe")
    void wrongCredentialsGiveTheSameError() {
        assertThatThrownBy(() -> login.login(email, "password-incorrecta"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .isEqualTo("ERR_INVALID_CREDENTIALS");

        assertThatThrownBy(() -> login.login("no-existe@petvacay.test", "password123"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .isEqualTo("ERR_INVALID_CREDENTIALS");
    }
}
