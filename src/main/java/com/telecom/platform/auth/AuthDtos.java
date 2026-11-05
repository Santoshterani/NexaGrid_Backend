package com.telecom.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record EmailReq(@NotBlank @Email @Size(max = 190) String email) {}
    public record VerifyReq(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
    public record ChallengeReq(@NotBlank @Size(max = 2000) String challengeToken) {}
    public record ConfirmReq(@NotBlank @Size(max = 2000) String challengeToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record ChallengeResponse(String step, String challengeToken) {}
    public record SetupResponse(String secret, String otpauthUri) {}
    public record MeDto(long id, String pool, String name, String email, String role, Long operatorId, Long customerId, List<String> perms) {
        static MeDto from(Account a) {
            return new MeDto(a.id(), a.pool().name(), a.name(), a.email(), a.role(), a.operatorId(), a.customerId(), List.copyOf(a.perms()));
        }
    }
    public record AuthResponse(String accessToken, int expiresIn, MeDto user) {}
    public record AuthResult(AuthResponse body, String refreshToken, Pool pool) {}
}
