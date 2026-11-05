package com.telecom.platform.dashboard;

import java.util.List;
import java.util.Map;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record OperatorRow(long id, String name, String status, long customers, long staff) {}
    public record PlatformDashboard(long operators, long activeOperators, long staffUsers, long customers,
                                    Map<String, Long> customersByStatus, List<OperatorRow> operatorRows) {}
    public record OperatorDashboard(String operatorName, long staffUsers, long customers, long pendingKyc,
                                    Map<String, Long> staffByRole, Map<String, Long> customersByStatus) {}
    public record CustomerDashboard(String displayName, String customerType, String tenantCode, String status,
                                    String kycStatus, String kycDeadline, long teamUsers) {}
}
