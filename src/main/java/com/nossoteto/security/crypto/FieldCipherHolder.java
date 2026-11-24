package com.nossoteto.security.crypto;

public final class FieldCipherHolder {

    private static volatile AesGcmFieldCipher cipher;

    private FieldCipherHolder() {}

    public static void set(AesGcmFieldCipher value) {
        cipher = value;
    }

    public static AesGcmFieldCipher get() {
        AesGcmFieldCipher current = cipher;
        if (current == null) {
            throw new IllegalStateException("Field cipher not initialized (APP_CRYPTO_KEY)");
        }
        return current;
    }
}
