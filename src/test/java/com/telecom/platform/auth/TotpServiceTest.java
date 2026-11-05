package com.telecom.platform.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    private final TotpService totp = new TotpService();
    private final byte[] rfcSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // RFC 6238 appendix B (SHA-1), last six digits of the 8-digit values
        assertEquals("287082", totp.code(rfcSecret, 59 / 30));
        assertEquals("081804", totp.code(rfcSecret, 1111111109L / 30));
    }

    @Test
    void acceptsCurrentCodeButNotTwice() {
        Instant now = Instant.ofEpochSecond(1111111109L);
        long step = 1111111109L / 30;
        long matched = totp.verify(rfcSecret, "081804", 0, now);
        assertEquals(step, matched);
        assertEquals(-1, totp.verify(rfcSecret, "081804", matched, now), "a used code must be rejected");
    }

    @Test
    void rejectsWrongCode() {
        assertEquals(-1, totp.verify(rfcSecret, "000000", 0, Instant.ofEpochSecond(1111111109L)));
    }

    @Test
    void base32EncodesKnownValue() {
        assertEquals("MFRGGZDFMY", totp.base32("abcdef".getBytes(StandardCharsets.US_ASCII)));
    }
}
