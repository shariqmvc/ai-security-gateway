package com.ai.gateway.util;

import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private final SecretKeySpec secretKey;

    public EncryptionUtil(
            @Value("${security.encryption.aes-key}")
            String key) {

        this.secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8),
                "AES");
    }

    public String encrypt(String value) {

        try {

            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(
                    value.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception ex) {

            throw new RuntimeException("Encryption failed", ex);
        }
    }

    public String decrypt(String encryptedValue) {

        try {

            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(
                    Base64.getDecoder().decode(encryptedValue));

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception ex) {

            throw new RuntimeException("Decryption failed", ex);
        }
    }
}
