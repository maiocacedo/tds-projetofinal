package com.nossoteto.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter();

    @BeforeEach
    void key() {
        FieldCipherHolder.set(new AesGcmFieldCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void nullsPassThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void writesEncryptedAndReadsBack() {
        String db = converter.convertToDatabaseColumn("bio secreta");
        assertTrue(db.startsWith("v1:"));
        assertEquals("bio secreta", converter.convertToEntityAttribute(db));
    }

    @Test
    void legacyPlaintextIsReadAsIs() {
        assertEquals("Nenhuma", converter.convertToEntityAttribute("Nenhuma"));
    }
}
