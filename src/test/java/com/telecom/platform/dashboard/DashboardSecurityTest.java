package com.telecom.platform.dashboard;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telecom.platform.AppProperties;
import com.telecom.platform.dashboard.DashboardDtos.CustomerDashboard;
import com.telecom.platform.security.JwtService;
import com.telecom.platform.security.SecurityConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-123", "app.jwt.issuer=t", "app.jwt.access-minutes=15",
        "app.jwt.refresh-days=7", "app.security.otp-pepper=p", "app.security.cookie-secure=false", "app.security.totp-issuer=T",
        "app.security.totp-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "app.dev.log-otp=false"})
class DashboardSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DashboardService service;

    private static SimpleGrantedAuthority a(String s) { return new SimpleGrantedAuthority(s); }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/dashboard/operator")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReadStaffDashboards() throws Exception {
        var customer = jwt().authorities(a("ROLE_ACCOUNT_ADMIN"), a("POOL_CUSTOMER"));
        mvc.perform(get("/api/dashboard/operator").with(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/platform").with(customer)).andExpect(status().isForbidden());
    }

    @Test
    void operatorStaffCannotReadPlatformDashboard() throws Exception {
        var admin = jwt().authorities(a("ROLE_OPERATOR_ADMIN"), a("POOL_STAFF"));
        mvc.perform(get("/api/dashboard/platform").with(admin)).andExpect(status().isForbidden());
    }

    @Test
    void operatorDashboardUsesOperatorIdFromTokenOnly() throws Exception {
        var admin = jwt().jwt(j -> j.subject("5").claim("pool", "STAFF").claim("role", "OPERATOR_ADMIN").claim("op", 7L))
                .authorities(a("ROLE_OPERATOR_ADMIN"), a("POOL_STAFF"));
        mvc.perform(get("/api/dashboard/operator?operatorId=999").with(admin)).andExpect(status().isOk());
        verify(service).operator(7L); // the ?operatorId=999 parameter is ignored
    }

    @Test
    void customerDashboardUsesCustomerIdFromToken() throws Exception {
        when(service.customer(42L)).thenReturn(new CustomerDashboard("A", "INDIVIDUAL", null, "ACTIVE", "APPROVED", null, 1));
        var customer = jwt().jwt(j -> j.subject("3").claim("pool", "CUSTOMER").claim("role", "ACCOUNT_ADMIN").claim("cid", 42L))
                .authorities(a("ROLE_ACCOUNT_ADMIN"), a("POOL_CUSTOMER"));
        mvc.perform(get("/api/dashboard/customer?customerId=1").with(customer)).andExpect(status().isOk());
        verify(service).customer(42L);
    }
}
