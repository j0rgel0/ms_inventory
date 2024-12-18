package com.lox.inventoryservice.common.r2dbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class JsonNodeToJsonConverter implements Converter<JsonNode, Json> {

    private final ObjectMapper objectMapper;

    public JsonNodeToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(JsonNode source) {
        try {
            return Json.of(objectMapper.writeValueAsString(source));
        } catch (Exception e) {
            throw new RuntimeException("Error converting JsonNode to Json", e);
        }
    }
}

