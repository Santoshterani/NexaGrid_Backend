package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** AES-256-GCM encryption for 2FA secrets stored in the database. */
@Service
public class CryptoService {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties props) {
        byte[] k = Base64.getDecoder().decode(props.security().totpEncryptionKey());
        if (k.length != 32) throw new IllegalStateException("app.security.totp-encryption-key must be 32 bytes, base64 encoded");
        this.key = new SecretKeySpec(k, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, 12));
            return c.doFinal(data, 12, data.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
