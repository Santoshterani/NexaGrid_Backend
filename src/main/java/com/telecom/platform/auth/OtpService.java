package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import com.telecom.platform.common.Hashing;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Email one-time codes. Only an HMAC of the code is stored, never the code itself. */
@Service
public class OtpService {
    static final int TTL_MINUTES = 10, MAX_ATTEMPTS = 5, MAX_REQUESTS_PER_15_MIN = 5;
    private record Row(long id, String hash, int attempts) {}

    private final JdbcClient jdbc;
    private final String pepper;
    private final SecureRandom random = new SecureRandom();

    public OtpService(JdbcClient jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.pepper = props.security().otpPepper();
    }

    private String hash(String email, String code) { return Hashing.hmacSha256(pepper, email + ":" + code); }

    public boolean tooManyRequests(String email, Pool pool) {
        Timestamp since = Timestamp.from(Instant.now().minusSeconds(15 * 60L));
        Long n = jdbc.sql("SELECT COUNT(*) FROM auth_otps WHERE email = :e AND principal = :p AND created_at > :s")
                .param("e", email).param("p", pool.name()).param("s", since).query(Long.class).single();
        return n >= MAX_REQUESTS_PER_15_MIN;
    }

    /** Invalidates earlier codes, creates a new one and returns it for delivery. */
    public String issue(String email, Pool pool) { return issue(email, pool, "LOGIN"); }

    public String issue(String email, Pool pool, String purpose) {
        Instant now = Instant.now();
        jdbc.sql("UPDATE auth_otps SET consumed_at = :n WHERE email = :e AND principal = :p AND consumed_at IS NULL")
                .param("n", Timestamp.from(now)).param("e", email).param("p", pool.name()).update();
        String code = String.format("%06d", random.nextInt(1_000_000));
        jdbc.sql("INSERT INTO auth_otps(email, principal, purpose, code_hash, expires_at, created_at) VALUES (:e, :p, :pu, :h, :x, :n)")
                .param("e", email).param("p", pool.name()).param("pu", purpose).param("h", hash(email, code))
                .param("x", Timestamp.from(now.plusSeconds(TTL_MINUTES * 60L))).param("n", Timestamp.from(now)).update();
        return code;
    }

    public boolean verify(String email, Pool pool, String code) { return verify(email, pool, code, "LOGIN"); }

    public boolean verify(String email, Pool pool, String code, String purpose) {
        Instant now = Instant.now();
        Optional<Row> row = jdbc.sql("""
                SELECT id, code_hash, attempts FROM auth_otps
                WHERE email = :e AND principal = :p AND purpose = :pu AND consumed_at IS NULL AND expires_at > :n
                ORDER BY id DESC LIMIT 1""")
                .param("e", email).param("p", pool.name()).param("pu", purpose).param("n", Timestamp.from(now))
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("code_hash"), rs.getInt("attempts"))).optional();
        if (row.isEmpty() || row.get().attempts() >= MAX_ATTEMPTS) return false;
        if (Hashing.constantTimeEquals(row.get().hash(), hash(email, code))) {
            jdbc.sql("UPDATE auth_otps SET consumed_at = :n WHERE id = :id").param("n", Timestamp.from(now)).param("id", row.get().id()).update();
            return true;
        }
        jdbc.sql("UPDATE auth_otps SET attempts = attempts + 1 WHERE id = :id").param("id", row.get().id()).update();
        return false;
    }
}
