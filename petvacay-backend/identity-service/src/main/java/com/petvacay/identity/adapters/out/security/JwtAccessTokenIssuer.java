package com.petvacay.identity.adapters.out.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.UserAccount;
import com.petvacay.identity.ports.out.IdentityPorts.AccessTokenIssuerPort;
import com.petvacay.shared.security.JwtClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emite los JWT firmados con RS256 (Fase 5).
 *
 * <p>Es el unico punto de todo el sistema con acceso a la clave **privada**; el
 * resto de servicios solo tienen la publica y por tanto pueden verificar pero no
 * falsificar tokens. Firmar con un algoritmo asimetrico en vez de un secreto
 * compartido (HS256) es justamente lo que hace posible esa asimetria: con HS256,
 * cualquier servicio capaz de validar podria tambien emitir.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuerPort {

    private static final String ISSUER = "petvacay-identity-service";

    private final JwtEncoder encoder;
    private final Duration tokenLifetime;
    private final Clock clock;

    public JwtAccessTokenIssuer(
            @Value("${petvacay.jwt.private-key-location:classpath:keys/jwt-dev-private.pem}")
            Resource privateKeyLocation,
            @Value("${petvacay.jwt.token-lifetime:PT8H}") Duration tokenLifetime,
            Clock clock) throws Exception {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(readKeyPair(privateKeyLocation))));
        this.tokenLifetime = tokenLifetime;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(UserAccount user, boolean sitterVerified) {
        Instant now = clock.instant();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .claim(JwtClaims.ROLES, user.roles().stream().map(Role::name).toList())
                .claim(JwtClaims.FIRST_NAME, user.firstName());

        // Solo tiene sentido para cuidadores; para un cliente el claim sobra.
        if (user.hasRole(Role.ROLE_SITTER)) {
            claims.claim(JwtClaims.IS_VERIFIED, sitterVerified);
        }

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        return new IssuedToken(token, "Bearer", tokenLifetime.toSeconds());
    }

    /**
     * Construye el JWK a partir de un unico fichero PEM con la clave privada.
     *
     * <p>Una clave privada RSA en PKCS#8 es en realidad una {@link RSAPrivateCrtKey}:
     * lleva dentro el modulo y el exponente publico, asi que la clave publica se
     * deriva de ella y no hace falta configurar un segundo fichero en este
     * servicio. Se usa {@code RsaKeyConverters} (JDK puro) en lugar del parser PEM
     * de Nimbus, que exigiria anadir BouncyCastle.
     */
    private static RSAKey readKeyPair(Resource privateKeyLocation) throws Exception {
        try (InputStream in = privateKeyLocation.getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(in);

            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException(
                        "La clave privada no está en formato CRT y no permite derivar la pública.");
            }

            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));

            return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        }
    }
}
