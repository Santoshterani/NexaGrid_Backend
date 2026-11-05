package com.telecom.platform.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    static final int MAX_MFA_FAILURES = 5;
    static final int LOCK_MINUTES = 15;

    private static final String STAFF_SQL = """
        SELECT u.id, u.email, u.name, u.role, u.status, u.operator_id, CAST(NULL AS UNSIGNED) AS customer_id,
               o.status AS op_status, u.totp_secret_enc, u.totp_enabled_at, u.totp_last_step, u.failed_mfa, u.locked_until,
               0 AS pd, 0 AS pp, 0 AS pb, 0 AS pu
        FROM users u LEFT JOIN operators o ON o.id = u.operator_id """;
    private static final String CUSTOMER_SQL = """
        SELECT cu.id, cu.email, cu.name, cu.role, cu.status, c.operator_id, cu.customer_id,
               CAST(NULL AS CHAR) AS op_status, cu.totp_secret_enc, cu.totp_enabled_at, cu.totp_last_step, cu.failed_mfa, cu.locked_until,
               cu.perm_devices AS pd, cu.perm_plans AS pp, cu.perm_billing AS pb, cu.perm_users AS pu
        FROM customer_users cu JOIN customers c ON c.id = cu.customer_id """;

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    private static String base(Pool p) { return p == Pool.STAFF ? STAFF_SQL : CUSTOMER_SQL; }

    public Optional<Account> findByEmail(Pool p, String email) {
        return jdbc.sql(base(p) + " WHERE " + p.alias + ".email = :e").param("e", email)
                .query((rs, i) -> map(rs, p)).optional();
    }

    public Optional<Account> findById(Pool p, long id) {
        return jdbc.sql(base(p) + " WHERE " + p.alias + ".id = :id").param("id", id)
                .query((rs, i) -> map(rs, p)).optional();
    }

    /** Stores a pending (not yet confirmed) 2FA secret. Refuses to overwrite an enabled one. */
    public void saveSecret(Pool p, long id, byte[] enc) {
        jdbc.sql("UPDATE " + p.table + " SET totp_secret_enc = :s WHERE id = :id AND totp_enabled_at IS NULL")
                .param("s", enc).param("id", id).update();
    }

    public void mfaSuccess(Pool p, long id, long step, boolean first) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("UPDATE " + p.table + " SET totp_last_step = :st, failed_mfa = 0, locked_until = NULL, last_login_at = :n"
                        + (first ? ", totp_enabled_at = :n, status = 'ACTIVE'" : "") + " WHERE id = :id")
                .param("st", step).param("n", now).param("id", id).update();
    }

    public void mfaFailure(Pool p, long id) {
        Timestamp lock = Timestamp.from(Instant.now().plusSeconds(LOCK_MINUTES * 60L));
        // locked_until is assigned first: MySQL evaluates SET clauses left to right
        jdbc.sql("UPDATE " + p.table + " SET locked_until = CASE WHEN failed_mfa + 1 >= :max THEN :lock ELSE locked_until END,"
                        + " failed_mfa = failed_mfa + 1 WHERE id = :id")
                .param("max", MAX_MFA_FAILURES).param("lock", lock).param("id", id).update();
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }

    private Account map(ResultSet rs, Pool p) throws SQLException {
        Set<String> perms = new HashSet<>();
        if (rs.getInt("pd") == 1) perms.add("DEVICES");
        if (rs.getInt("pp") == 1) perms.add("PLANS");
        if (rs.getInt("pb") == 1) perms.add("BILLING");
        if (rs.getInt("pu") == 1) perms.add("USERS");
        return new Account(rs.getLong("id"), p, rs.getString("email"), rs.getString("name"), rs.getString("role"),
                rs.getString("status"), nullableLong(rs, "operator_id"), nullableLong(rs, "customer_id"),
                rs.getString("op_status"), rs.getBytes("totp_secret_enc"), instant(rs.getTimestamp("totp_enabled_at")),
                rs.getLong("totp_last_step"), rs.getInt("failed_mfa"), instant(rs.getTimestamp("locked_until")), perms);
    }
}
