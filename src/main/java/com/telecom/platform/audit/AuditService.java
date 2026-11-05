package com.telecom.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.platform.auth.Account;
import com.telecom.platform.auth.Pool;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Writes append-only audit rows. Never put OTPs, tokens, secrets or card data in details. */
@Service
public class AuditService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void log(Account a, String action, Map<String, Object> details, String ip) {
        log(a.operatorId(), a.pool() == Pool.STAFF ? a.id() : null, a.pool() == Pool.CUSTOMER ? a.id() : null,
                action, "AUTH", String.valueOf(a.id()), details, ip);
    }

    public void log(Long operatorId, Long userId, Long customerUserId, String action, String entityType,
                    String entityId, Map<String, Object> details, String ip) {
        String json;
        try {
            json = mapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            json = "{}";
        }
        jdbc.sql("""
                INSERT INTO audit_logs(operator_id, user_id, customer_user_id, action, entity_type, entity_id, details, ip_address)
                VALUES (:op, :u, :cu, :a, :t, :id, CAST(:d AS JSON), :ip)""")
                .param("op", operatorId).param("u", userId).param("cu", customerUserId).param("a", action)
                .param("t", entityType).param("id", entityId).param("d", json).param("ip", ip).update();
    }
}
