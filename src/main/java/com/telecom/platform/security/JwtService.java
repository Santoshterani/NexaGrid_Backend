package com.telecom.platform.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.telecom.platform.AppProperties;
import com.telecom.platform.auth.Account;
import com.telecom.platform.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/**
 * Issues two kinds of signed tokens: short-lived ACCESS tokens (typ=access) and
 * 5-minute MFA challenge tokens (typ=mfa). A challenge token is rejected by the API.
 */
@Service
public class JwtService {
    private final AppProperties props;
    private final SecretKeySpec key;
    private final JwtEncoder encoder;
    private final JwtDecoder anyDecoder;

    public JwtService(AppProperties props) {
        byte[] k = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (k.length < 32) throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        this.props = props;
        this.key = new SecretKeySpec(k, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.anyDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public String access(Account a) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder b = JwtClaimsSet.builder().issuer(props.jwt().issuer()).subject(String.valueOf(a.id()))
                .issuedAt(now).expiresAt(now.plusSeconds(props.jwt().accessMinutes() * 60L)).id(UUID.randomUUID().toString())
                .claim("typ", "access").claim("pool", a.pool().name()).claim("role", a.role())
                .claim("perms", List.copyOf(a.perms()));
        if (a.operatorId() != null) b.claim("op", a.operatorId());
        if (a.customerId() != null) b.claim("cid", a.customerId());
        return encode(b.build());
    }

    public String challenge(Account a, String step) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder().issuer(props.jwt().issuer()).subject(String.valueOf(a.id()))
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("typ", "mfa").claim("step", step)
                .claim("pool", a.pool().name()).build());
    }

    /** @param step "SETUP" or "VERIFY", or null to accept either */
    public Jwt parseChallenge(String token, String step) {
        try {
            Jwt j = anyDecoder.decode(token);
            boolean ok = "mfa".equals(j.getClaimAsString("typ")) && props.jwt().issuer().equals(j.getIssuer().toString())
                    && (step == null || step.equals(j.getClaimAsString("step")));
            if (!ok) throw ApiException.unauthorized("Invalid or expired challenge");
            return j;
        } catch (JwtException e) {
            throw ApiException.unauthorized("Invalid or expired challenge");
        }
    }

    /** Decoder used by the API: valid signature, issuer, expiry and typ=access. */
    public JwtDecoder accessDecoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> typ = jwt -> "access".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong token type", null));
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()), typ));
        return d;
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
