package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import com.telecom.platform.common.Hashing;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Opaque, rotating refresh tokens. Only a SHA-256 of each token is stored. Each token is single-use;
 * presenting an already-used token revokes the whole family (theft detection).
 */
@Service
public class RefreshTokenService {
    public record Rotation(Pool pool, long principalId, String family, String token, boolean reuse) {}
    private record Row(long id, Pool pool, long principalId, String family, Timestamp expires, Timestamp used, Timestamp revoked) {}

    private final JdbcClient jdbc;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(JdbcClient jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public String newFamily() { return UUID.randomUUID().toString(); }

    public String issue(Pool pool, long principalId, String family, String userAgent, String ip) {
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO refresh_tokens(principal, principal_id, family_id, token_hash, expires_at, user_agent, ip_address, created_at)
                VALUES (:p, :pid, :f, :h, :x, :ua, :ip, :n)""")
                .param("p", pool.name()).param("pid", principalId).param("f", family).param("h", Hashing.sha256(token))
                .param("x", Timestamp.from(now.plusSeconds(props.jwt().refreshDays() * 86400L)))
                .param("ua", userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())))
                .param("ip", ip).param("n", Timestamp.from(now)).update();
        return token;
    }

    /** Returns null when the token is unknown, expired or revoked. */
    public Rotation rotate(String token, String userAgent, String ip) {
        Optional<Row> found = jdbc.sql("SELECT id, principal, principal_id, family_id, expires_at, used_at, revoked_at FROM refresh_tokens WHERE token_hash = :h")
                .param("h", Hashing.sha256(token))
                .query((rs, i) -> new Row(rs.getLong("id"), Pool.valueOf(rs.getString("principal")), rs.getLong("principal_id"),
                        rs.getString("family_id"), rs.getTimestamp("expires_at"), rs.getTimestamp("used_at"), rs.getTimestamp("revoked_at")))
                .optional();
        if (found.isEmpty()) return null;
        Row row = found.get();
        Instant now = Instant.now();
        if (row.revoked() != null || row.expires().toInstant().isBefore(now)) return null;
        int marked = jdbc.sql("UPDATE refresh_tokens SET used_at = :n WHERE id = :id AND used_at IS NULL")
                .param("n", Timestamp.from(now)).param("id", row.id()).update();
        if (row.used() != null || marked == 0) {
            revokeFamily(row.family());
            return new Rotation(row.pool(), row.principalId(), row.family(), null, true);
        }
        return new Rotation(row.pool(), row.principalId(), row.family(), issue(row.pool(), row.principalId(), row.family(), userAgent, ip), false);
    }

    public void revokeFamily(String family) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = :n WHERE family_id = :f AND revoked_at IS NULL")
                .param("n", Timestamp.from(Instant.now())).param("f", family).update();
    }

    public void revokeByToken(String token) {
        jdbc.sql("SELECT family_id FROM refresh_tokens WHERE token_hash = :h").param("h", Hashing.sha256(token))
                .query(String.class).optional().ifPresent(this::revokeFamily);
    }
}
