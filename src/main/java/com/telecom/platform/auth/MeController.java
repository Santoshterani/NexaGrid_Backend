package com.telecom.platform.auth;

import com.telecom.platform.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    @GetMapping("/api/me")
    public AuthUser me(@AuthenticationPrincipal Jwt jwt) { return AuthUser.from(jwt); }
}
