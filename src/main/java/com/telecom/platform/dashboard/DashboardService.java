package com.telecom.platform.dashboard;

import com.telecom.platform.common.ApiException;
import com.telecom.platform.dashboard.DashboardDtos.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Every query is scoped by an id taken from the verified token, passed in by the controller. */
@Service
public class DashboardService {
    private final JdbcClient jdbc;

    public DashboardService(JdbcClient jdbc) { this.jdbc = jdbc; }

    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }

    private long count(String sql, long id) { return jdbc.sql(sql).param("id", id).query(Long.class).single(); }

    private Map<String, Long> groups(String sql, Long id) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (id != null) spec = spec.param("id", id);
        Map<String, Long> out = new LinkedHashMap<>();
        spec.query((rs, i) -> Map.entry(rs.getString("k"), rs.getLong("v"))).list().forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    public PlatformDashboard platform() {
        List<OperatorRow> rows = jdbc.sql("""
                SELECT o.id, o.name, o.status,
                       (SELECT COUNT(*) FROM customers c WHERE c.operator_id = o.id) AS customers,
                       (SELECT COUNT(*) FROM users u WHERE u.operator_id = o.id) AS staff
                FROM operators o ORDER BY o.name""")
                .query((rs, i) -> new OperatorRow(rs.getLong("id"), rs.getString("name"), rs.getString("status"),
                        rs.getLong("customers"), rs.getLong("staff"))).list();
        return new PlatformDashboard(count("SELECT COUNT(*) FROM operators"),
                count("SELECT COUNT(*) FROM operators WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM users WHERE operator_id IS NOT NULL"),
                count("SELECT COUNT(*) FROM customers"),
                groups("SELECT status AS k, COUNT(*) AS v FROM customers GROUP BY status", null), rows);
    }

    public OperatorDashboard operator(Long operatorId) {
        if (operatorId == null) throw ApiException.forbidden("No operator in token");
        String name = jdbc.sql("SELECT name FROM operators WHERE id = :id").param("id", operatorId)
                .query(String.class).optional().orElseThrow(() -> ApiException.notFound("Operator not found"));
        return new OperatorDashboard(name,
                count("SELECT COUNT(*) FROM users WHERE operator_id = :id", operatorId),
                count("SELECT COUNT(*) FROM customers WHERE operator_id = :id", operatorId),
                count("SELECT COUNT(*) FROM customers WHERE operator_id = :id AND kyc_status = 'PENDING'", operatorId),
                groups("SELECT role AS k, COUNT(*) AS v FROM users WHERE operator_id = :id GROUP BY role", operatorId),
                groups("SELECT status AS k, COUNT(*) AS v FROM customers WHERE operator_id = :id GROUP BY status", operatorId));
    }

    public CustomerDashboard customer(Long customerId) {
        if (customerId == null) throw ApiException.forbidden("No customer in token");
        return jdbc.sql("""
                SELECT c.display_name, c.customer_type, c.tenant_code, c.status, c.kyc_status, c.kyc_deadline,
                       (SELECT COUNT(*) FROM customer_users cu WHERE cu.customer_id = c.id) AS team
                FROM customers c WHERE c.id = :id""").param("id", customerId)
                .query((rs, i) -> new CustomerDashboard(rs.getString("display_name"), rs.getString("customer_type"),
                        rs.getString("tenant_code"), rs.getString("status"), rs.getString("kyc_status"),
                        rs.getString("kyc_deadline"), rs.getLong("team"))).optional()
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
    }
}
