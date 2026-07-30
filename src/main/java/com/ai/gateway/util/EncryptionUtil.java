package com.ai.gateway.util;

import lombok.experimental.UtilityClass;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@UtilityClass
public class EncryptionUtil {

    /**
     * Replace with externalized configuration later.
     * Must be exactly 32 bytes for AES-256.
     */
    private static final String SECRET =
            "12345678901234567890123456789012";

    private static final SecretKeySpec KEY =
            new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    "AES"
            );

    public static String encrypt(String value) {

        try {

            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.ENCRYPT_MODE, KEY);

            byte[] encrypted = cipher.doFinal(
                    value.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception ex) {

            throw new RuntimeException("Encryption failed", ex);
        }

    }

    public static String decrypt(String encryptedValue) {

        try {

            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.DECRYPT_MODE, KEY);

            byte[] decrypted = cipher.doFinal(
                    Base64.getDecoder().decode(encryptedValue));

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception ex) {

            throw new RuntimeException("Decryption failed", ex);
        }

    }
}
