package com.lox.inventoryservice.common.r2dbc;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class JsonToJsonNodeConverter implements Converter<Json, JsonNode> {

    private final ObjectMapper objectMapper;

    public JsonToJsonNodeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode convert(Json source) {
        try {
            return objectMapper.readTree(source.asString());
        } catch (Exception e) {
            throw new RuntimeException("Error converting Json to JsonNode", e);
        }
    }
}
