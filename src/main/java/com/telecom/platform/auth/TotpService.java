package com.telecom.platform.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** RFC 6238 time-based one-time passwords (SHA-1, 6 digits, 30 second step). */
@Service
public class TotpService {
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    public byte[] newSecret() {
        byte[] s = new byte[20];
        random.nextBytes(s);
        return s;
    }

    public String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public String uri(String issuer, String email, byte[] secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(email) + "?secret=" + base32(secret)
                + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    public String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the matched time step, or -1. Accepts one step either side for clock drift,
     * and rejects any step at or before lastStep so a code cannot be used twice.
     */
    public long verify(byte[] secret, String code, long lastStep, Instant now) {
        long current = now.getEpochSecond() / 30;
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        for (long s = current - 1; s <= current + 1; s++) {
            if (s > lastStep && MessageDigest.isEqual(code(secret, s).getBytes(StandardCharsets.UTF_8), given)) return s;
        }
        return -1;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
