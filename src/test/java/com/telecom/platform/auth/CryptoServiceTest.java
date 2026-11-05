package com.telecom.platform.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.telecom.platform.AppProperties;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {
    private final CryptoService crypto = new CryptoService(new AppProperties(null,
            new AppProperties.Security("pepper", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", false, "T"),
            new AppProperties.Dev(false)));

    @Test
    void roundTrips() {
        byte[] secret = {1, 2, 3, 4, 5};
        assertArrayEquals(secret, crypto.decrypt(crypto.encrypt(secret)));
    }

    @Test
    void usesFreshNonceEveryTime() {
        byte[] secret = {9, 9, 9};
        assertFalse(java.util.Arrays.equals(crypto.encrypt(secret), crypto.encrypt(secret)));
    }

    @Test
    void detectsTampering() {
        byte[] enc = crypto.encrypt(new byte[] {1, 2, 3});
        enc[enc.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(enc));
    }
}
