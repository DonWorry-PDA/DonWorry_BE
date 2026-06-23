package com.sol.user.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
@Converter
public class IdNumberConverter implements AttributeConverter<String, String> {

    @Value("${encryption.secret}")
    private String secret;

    @Value("${encryption.salt}")
    private String salt;

    private TextEncryptor encryptor;

    @PostConstruct
    public void init() {
        this.encryptor = Encryptors.text(secret, salt);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return encryptor.decrypt(dbData);
    }
}
