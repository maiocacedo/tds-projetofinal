package com.nossoteto.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : FieldCipherHolder.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Rows written before encryption have no prefix: read as legacy plaintext, re-encrypted on next save.
        return dbData.startsWith(AesGcmFieldCipher.PREFIX) ? FieldCipherHolder.get().decrypt(dbData) : dbData;
    }
}
