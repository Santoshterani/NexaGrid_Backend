package com.telecom.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Security security, Dev dev) {
    public record Jwt(String secret, String issuer, int accessMinutes, int refreshDays) {}
    public record Security(String otpPepper, String totpEncryptionKey, boolean cookieSecure, String totpIssuer) {}
    public record Dev(boolean logOtp) {}
}
