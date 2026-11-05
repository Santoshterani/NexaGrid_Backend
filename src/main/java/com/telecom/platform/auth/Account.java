package com.telecom.platform.auth;

import java.time.Instant;
import java.util.Set;

public record Account(long id, Pool pool, String email, String name, String role, String status,
                      Long operatorId, Long customerId, String operatorStatus,
                      byte[] totpSecretEnc, Instant totpEnabledAt, long totpLastStep,
                      int failedMfa, Instant lockedUntil, Set<String> perms) {

    public boolean totpEnabled() { return totpEnabledAt != null; }

    public boolean isLocked() { return lockedUntil != null && lockedUntil.isAfter(Instant.now()); }

    /** Disabled users and staff of a suspended operator cannot sign in. */
    public boolean canLogin() {
        return !"DISABLED".equals(status) && (operatorStatus == null || "ACTIVE".equals(operatorStatus));
    }
}
