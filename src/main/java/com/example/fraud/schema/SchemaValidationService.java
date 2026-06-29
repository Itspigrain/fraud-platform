package com.example.fraud.schema;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaValidationService {

    public record Violation(String field, String reason) {}

    public void validateSchemaDefinition(List<SchemaFieldDefinition> fields) {
        Set<String> seen = new HashSet<>();
        for (SchemaFieldDefinition field : fields) {
            if (field.name() == null || field.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Field name must not be blank");
            }
            if (field.type() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Field type is required for field: " + field.name());
            }
            if (!seen.add(field.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate field name: " + field.name());
            }
        }
    }

    public List<Violation> validateAttributes(Map<String, Object> attributes, List<SchemaFieldDefinition> fields) {
        List<Violation> violations = new ArrayList<>();
        for (SchemaFieldDefinition field : fields) {
            Object value = attributes.get(field.name());
            if (value == null) {
                if (field.required()) {
                    violations.add(new Violation(field.name(), "required field missing"));
                }
                continue;
            }
            if (!isValidType(value, field.type())) {
                violations.add(new Violation(field.name(),
                    "expected type " + field.type().toEsType() + ", got " + value.getClass().getSimpleName()));
            }
        }
        return violations;
    }

    public Map<String, Object> stripUnknownFields(Map<String, Object> attributes, List<SchemaFieldDefinition> fields) {
        Set<String> knownNames = fields.stream()
            .map(SchemaFieldDefinition::name)
            .collect(Collectors.toSet());
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : attributes.entrySet()) {
            if (knownNames.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private boolean isValidType(Object value, SchemaFieldType type) {
        return switch (type) {
            case KEYWORD, TEXT -> value instanceof String;
            case INTEGER -> value instanceof Integer;
            case LONG -> value instanceof Integer || value instanceof Long;
            case DOUBLE -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> {
                if (value instanceof String s) {
                    try { Instant.parse(s); yield true; } catch (Exception e) { yield false; }
                }
                yield false;
            }
            case IP -> {
                if (value instanceof String s) {
                    try { InetAddress.getByName(s); yield true; } catch (Exception e) { yield false; }
                }
                yield false;
            }
            case GEO_POINT -> {
                if (value instanceof Map<?, ?> m) {
                    yield m.containsKey("lat") && m.containsKey("lon")
                        && m.get("lat") instanceof Number && m.get("lon") instanceof Number;
                }
                yield false;
            }
        };
    }
}
