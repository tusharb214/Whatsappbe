package com.sitegenius.whatsappbe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for secrets stored in the database.
 * The key comes from APP_ENCRYPTION_KEY (base64, 32 bytes).
 */
@Component
public class SecretCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    @Value("${APP_ENCRYPTION_KEY:}")
    private String keyBase64;

    private final SecureRandom random = new SecureRandom();

    public boolean isReady() {
        return key() != null;
    }

    private SecretKeySpec key() {
        try {
            if (keyBase64 == null || keyBase64.isBlank()) {
                return null;
            }
            byte[] raw = Base64.getDecoder().decode(keyBase64.trim());
            return raw.length == 32 ? new SecretKeySpec(raw, "AES") : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String encrypt(String plain) {
        SecretKeySpec key = key();
        if (key == null) {
            throw new IllegalStateException("Encryption key is not configured");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt value");
        }
    }

    public String decrypt(String encrypted) {
        SecretKeySpec key = key();
        if (key == null) {
            throw new IllegalStateException("Encryption key is not configured");
        }
        try {
            byte[] in = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(in, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt value");
        }
    }
}
