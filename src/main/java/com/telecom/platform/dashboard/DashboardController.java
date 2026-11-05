package com.telecom.platform.dashboard;

import com.telecom.platform.dashboard.DashboardDtos.*;
import com.telecom.platform.security.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('POOL_STAFF') and hasRole('SUPER_ADMIN')")
    public PlatformDashboard platform() { return service.platform(); }

    @GetMapping("/operator")
    @PreAuthorize("hasAuthority('POOL_STAFF') and hasAnyRole('OPERATOR_ADMIN','NETWORK_PLANNER','NETWORK_ENGINEER')")
    public OperatorDashboard operator(@AuthenticationPrincipal Jwt jwt) {
        return service.operator(AuthUser.from(jwt).operatorId()); // from the token, never from a parameter
    }

    @GetMapping("/customer")
    @PreAuthorize("hasAuthority('POOL_CUSTOMER')")
    public CustomerDashboard customer(@AuthenticationPrincipal Jwt jwt) {
        return service.customer(AuthUser.from(jwt).customerId());
    }
}
