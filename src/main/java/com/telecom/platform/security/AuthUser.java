package com.telecom.platform.security;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, read ONLY from the verified token. Never take operator or customer ids from request parameters. */
public record AuthUser(long id, String pool, String role, Long operatorId, Long customerId, Set<String> perms) {

    public static AuthUser from(Jwt jwt) {
        List<String> p = jwt.getClaimAsStringList("perms");
        return new AuthUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("pool"), jwt.getClaimAsString("role"),
                num(jwt.getClaim("op")), num(jwt.getClaim("cid")), p == null ? Set.of() : Set.copyOf(p));
    }

    private static Long num(Object o) { return o instanceof Number n ? n.longValue() : null; }
}
