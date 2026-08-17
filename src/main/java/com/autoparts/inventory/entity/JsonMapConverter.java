package com.autoparts.inventory.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) {
                return new HashMap<>();
            }
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
