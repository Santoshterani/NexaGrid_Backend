package com.telecom.platform.dev;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Sample data for local development only (profile "dev"). All users start INVITED and must enrol 2FA on first sign-in. */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {
    private final JdbcClient jdbc;

    public DevDataSeeder(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        if (jdbc.sql("SELECT COUNT(*) FROM operators").query(Long.class).single() > 0) return;
        long nb = operator("NB", "NetBridge Broadband", 6000);
        long sk = operator("SK", "SkyLink Telecom", 8000);
        staff(null, "Platform Admin", "super@platform.test", "SUPER_ADMIN");
        staff(nb, "Nisha Admin", "admin@netbridge.test", "OPERATOR_ADMIN");
        staff(nb, "Pravin Planner", "planner@netbridge.test", "NETWORK_PLANNER");
        staff(nb, "Esha Engineer", "engineer@netbridge.test", "NETWORK_ENGINEER");
        staff(sk, "Sky Admin", "admin@skylink.test", "OPERATOR_ADMIN");
        long anita = customer(nb, "CUST-1042", "INDIVIDUAL", "Anita Rao", "anita@example.com", null, null);
        long orbit = customer(nb, "CUST-0912", "BUSINESS", "Orbit Retail Pvt Ltd", "admin@orbit.test", "27AAACR5055K1Z5", "TEN-4821");
        customerUser(anita, "Anita Rao", "anita@example.com", "ACCOUNT_ADMIN", 1, 1, 1, 1);
        customerUser(orbit, "Orbit Admin", "admin@orbit.test", "ACCOUNT_ADMIN", 1, 1, 1, 1);
        customerUser(orbit, "Orbit Billing", "billing@orbit.test", "BILLING_MANAGER", 0, 1, 1, 0);
    }

    private long operator(String code, String name, int cap) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO operators(code, name, max_plan_price, contact_email) VALUES (:c, :n, :p, :e)")
                .param("c", code).param("n", name).param("p", cap).param("e", "ops@" + code.toLowerCase() + ".test").update(kh);
        return kh.getKey().longValue();
    }

    private void staff(Long op, String name, String email, String role) {
        jdbc.sql("INSERT INTO users(operator_id, name, email, role) VALUES (:o, :n, :e, :r)")
                .param("o", op).param("n", name).param("e", email).param("r", role).update();
    }

    private long customer(long op, String code, String type, String name, String email, String gstin, String tenant) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO customers(operator_id, customer_code, customer_type, display_name, contact_name, email, mobile,
                                      address_line, gstin, tenant_code, status, kyc_status)
                VALUES (:o, :c, :t, :n, :n, :e, '9876543210', 'Sample address, Bengaluru', :g, :tn, 'ACTIVE', 'APPROVED')""")
                .param("o", op).param("c", code).param("t", type).param("n", name).param("e", email)
                .param("g", gstin).param("tn", tenant).update(kh);
        return kh.getKey().longValue();
    }

    private void customerUser(long customer, String name, String email, String role, int d, int p, int b, int u) {
        jdbc.sql("""
                INSERT INTO customer_users(customer_id, name, email, role, perm_devices, perm_plans, perm_billing, perm_users)
                VALUES (:c, :n, :e, :r, :d, :p, :b, :u)""")
                .param("c", customer).param("n", name).param("e", email).param("r", role)
                .param("d", d).param("p", p).param("b", b).param("u", u).update();
    }
}
