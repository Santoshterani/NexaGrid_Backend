package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import com.telecom.platform.audit.AuditService;
import com.telecom.platform.auth.AuthDtos.*;
import com.telecom.platform.common.ApiException;
import com.telecom.platform.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Sign-in = email OTP, then authenticator-app 2FA (TOTP). 2FA is mandatory:
 * first sign-in must enrol it. Responses never reveal whether an email exists.
 */
@Service
public class AuthService {
    private static final String BAD_CODE = "Invalid or expired code";

    private final AccountRepository accounts;
    private final OtpService otp;
    private final TotpService totp;
    private final CryptoService crypto;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final AuditService audit;
    private final EmailSender mail;
    private final AppProperties props;

    public AuthService(AccountRepository accounts, OtpService otp, TotpService totp, CryptoService crypto, JwtService jwt,
                       RefreshTokenService refresh, AuditService audit, EmailSender mail, AppProperties props) {
        this.accounts = accounts; this.otp = otp; this.totp = totp; this.crypto = crypto; this.jwt = jwt;
        this.refresh = refresh; this.audit = audit; this.mail = mail; this.props = props;
    }

    private static String norm(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    /** Always completes silently, whether or not the email is registered. */
    public void requestOtp(Pool pool, String rawEmail, String ip) {
        String email = norm(rawEmail);
        if (otp.tooManyRequests(email, pool)) return;
        accounts.findByEmail(pool, email).filter(Account::canLogin).ifPresent(a -> {
            String code = otp.issue(email, pool);
            mail.send(email, "Your sign-in code", "Your code is " + code + ". It expires in " + OtpService.TTL_MINUTES + " minutes.");
            audit.log(a, "OTP_REQUESTED", Map.of(), ip);
        });
    }

    public ChallengeResponse verifyOtp(Pool pool, String rawEmail, String code, String ip) {
        String email = norm(rawEmail);
        Account a = accounts.findByEmail(pool, email).filter(Account::canLogin).orElse(null);
        if (a != null && a.isLocked()) throw ApiException.tooMany("Too many failed attempts. Try again later.");
        boolean ok = otp.verify(email, pool, code);
        if (!ok || a == null) {
            if (a != null) audit.log(a, "OTP_FAILED", Map.of(), ip);
            throw ApiException.unauthorized(BAD_CODE);
        }
        String step = a.totpEnabled() ? "VERIFY" : "SETUP";
        return new ChallengeResponse(step, jwt.challenge(a, step));
    }

    public SetupResponse mfaSetup(String challengeToken) {
        Account a = load(jwt.parseChallenge(challengeToken, "SETUP"));
        if (a.totpEnabled()) throw ApiException.conflict("Two-factor authentication is already enabled");
        byte[] secret = totp.newSecret();
        accounts.saveSecret(a.pool(), a.id(), crypto.encrypt(secret));
        return new SetupResponse(totp.base32(secret), totp.uri(props.security().totpIssuer(), a.email(), secret));
    }

    public AuthResult mfaConfirm(String challengeToken, String code, String userAgent, String ip) {
        Account a = load(jwt.parseChallenge(challengeToken, null));
        if (a.isLocked()) throw ApiException.tooMany("Too many failed attempts. Try again later.");
        if (a.totpSecretEnc() == null) throw ApiException.badRequest("Set up two-factor authentication first");
        long step = totp.verify(crypto.decrypt(a.totpSecretEnc()), code, a.totpLastStep(), Instant.now());
        if (step < 0) {
            accounts.mfaFailure(a.pool(), a.id());
            audit.log(a, "MFA_FAILED", Map.of(), ip);
            throw ApiException.unauthorized("Invalid code");
        }
        boolean first = !a.totpEnabled();
        accounts.mfaSuccess(a.pool(), a.id(), step, first);
        if (first) audit.log(a, "MFA_ENABLED", Map.of(), ip);
        audit.log(a, "LOGIN_SUCCESS", Map.of(), ip);
        String family = refresh.newFamily();
        return session(a, refresh.issue(a.pool(), a.id(), family, userAgent, ip));
    }

    public AuthResult refresh(Pool pool, String token, String userAgent, String ip) {
        if (token == null) throw ApiException.unauthorized("Session expired");
        RefreshTokenService.Rotation r = refresh.rotate(token, userAgent, ip);
        if (r == null || r.pool() != pool) throw ApiException.unauthorized("Session expired");
        Account a = accounts.findById(r.pool(), r.principalId()).filter(Account::canLogin)
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        if (r.reuse()) {
            audit.log(a, "REFRESH_TOKEN_REUSE", Map.of(), ip);
            throw ApiException.unauthorized("Session expired");
        }
        return session(a, r.token());
    }

    public void logout(String token) {
        if (token != null) refresh.revokeByToken(token);
    }

    private Account load(Jwt challenge) {
        Pool pool = Pool.valueOf(challenge.getClaimAsString("pool"));
        return accounts.findById(pool, Long.parseLong(challenge.getSubject())).filter(Account::canLogin)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired challenge"));
    }

    private AuthResult session(Account a, String refreshToken) {
        return new AuthResult(new AuthResponse(jwt.access(a), props.jwt().accessMinutes() * 60, MeDto.from(a)), refreshToken, a.pool());
    }
}
