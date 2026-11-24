package com.nossoteto.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class FieldCipherConfig {

    @Bean
    public AesGcmFieldCipher fieldCipher(@Value("${app.crypto.key}") String base64Key) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) must be base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) must decode to 32 bytes");
        }
        AesGcmFieldCipher cipher = new AesGcmFieldCipher(key);
        FieldCipherHolder.set(cipher);
        return cipher;
    }
}
