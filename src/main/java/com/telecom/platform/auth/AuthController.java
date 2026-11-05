package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import com.telecom.platform.auth.AuthDtos.*;
import com.telecom.platform.common.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final AppProperties props;

    public AuthController(AuthService service, AppProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/{pool}/otp/request")
    public ResponseEntity<Void> requestOtp(@PathVariable String pool, @Valid @RequestBody EmailReq body, HttpServletRequest req) {
        service.requestOtp(Pool.from(pool), body.email(), req.getRemoteAddr());
        return ResponseEntity.accepted().build(); // same answer for known and unknown emails
    }

    @PostMapping("/{pool}/otp/verify")
    public ChallengeResponse verifyOtp(@PathVariable String pool, @Valid @RequestBody VerifyReq body, HttpServletRequest req) {
        return service.verifyOtp(Pool.from(pool), body.email(), body.code(), req.getRemoteAddr());
    }

    @PostMapping("/mfa/setup")
    public SetupResponse setup(@Valid @RequestBody ChallengeReq body) {
        return service.mfaSetup(body.challengeToken());
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<AuthResponse> confirm(@Valid @RequestBody ConfirmReq body, HttpServletRequest req) {
        return respond(service.mfaConfirm(body.challengeToken(), body.code(), req.getHeader(HttpHeaders.USER_AGENT), req.getRemoteAddr()));
    }

    @PostMapping("/{pool}/refresh")
    public ResponseEntity<AuthResponse> refresh(@PathVariable String pool, HttpServletRequest req) {
        requireXhr(req);
        Pool p = Pool.from(pool);
        return respond(service.refresh(p, cookie(req, "rt_" + p.path()), req.getHeader(HttpHeaders.USER_AGENT), req.getRemoteAddr()));
    }

    @PostMapping("/{pool}/logout")
    public ResponseEntity<Void> logout(@PathVariable String pool, HttpServletRequest req) {
        requireXhr(req);
        Pool p = Pool.from(pool);
        service.logout(cookie(req, "rt_" + p.path()));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie(p, "", 0).toString()).build();
    }

    private ResponseEntity<AuthResponse> respond(AuthResult r) {
        long maxAge = Duration.ofDays(props.jwt().refreshDays()).toSeconds();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(r.pool(), r.refreshToken(), maxAge).toString()).body(r.body());
    }

    /** HttpOnly + SameSite=Strict, scoped to this pool's auth path so it is never sent to other APIs. */
    private ResponseCookie refreshCookie(Pool p, String value, long maxAgeSeconds) {
        return ResponseCookie.from("rt_" + p.path(), value).httpOnly(true).secure(props.security().cookieSecure())
                .sameSite("Strict").path("/api/auth/" + p.path()).maxAge(maxAgeSeconds).build();
    }

    /** Cross-site forms cannot set custom headers, so this blocks CSRF on the cookie-based endpoints. */
    private static void requireXhr(HttpServletRequest req) {
        if (!"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) throw ApiException.forbidden("Missing required header");
    }

    private static String cookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) if (c.getName().equals(name)) return c.getValue();
        return null;
    }
}
