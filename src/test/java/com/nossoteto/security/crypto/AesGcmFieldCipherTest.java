package com.nossoteto.security.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmFieldCipherTest {

    private final AesGcmFieldCipher cipher =
            new AesGcmFieldCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripWithVersionPrefix() {
        String enc = cipher.encrypt("Alergia a amendoim");
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains("amendoim"));
        assertEquals("Alergia a amendoim", cipher.decrypt(enc));
    }

    @Test
    void sameInputYieldsDifferentCiphertextBecauseIvIsRandom() {
        assertNotEquals(cipher.encrypt("x"), cipher.encrypt("x"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String enc = cipher.encrypt("segredo");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        AesGcmFieldCipher other = new AesGcmFieldCipher("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> other.decrypt(cipher.encrypt("segredo")));
    }

    @Test
    void keyMustBe32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmFieldCipher(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmFieldCipher(null));
    }

    @Test
    void truncatedPayloadIsRejected() {
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:AAAA"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:%%%"));
    }
}
