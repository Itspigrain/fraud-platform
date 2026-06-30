package com.example.fraud.rule;

import com.example.fraud.schema.SchemaFieldDefinition;
import com.example.fraud.schema.SchemaFieldType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuleValidationService {

    public record ValidationError(String field, String message) {}

    private static final Set<String> BUILTIN_FIELDS = Set.of("eventType");

    private static final Set<ConditionOperator> NUMERIC_OPERATORS = Set.of(
        ConditionOperator.GREATER_THAN,
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN_OR_EQUAL,
        ConditionOperator.LESS_THAN_OR_EQUAL
    );

    private static final Set<SchemaFieldType> NUMERIC_TYPES = Set.of(
        SchemaFieldType.INTEGER,
        SchemaFieldType.LONG,
        SchemaFieldType.DOUBLE
    );

    public List<ValidationError> validate(RuleRequest request, List<SchemaFieldDefinition> schemaFields) {
        if (request.ruleType() == null || request.ruleType() == RuleType.LLM_EVALUATOR) {
            return List.of();
        }

        if (request.ruleType() == RuleType.VELOCITY) {
            return validateVelocityRule(request, schemaFields);
        }

        return validateConditionRule(request, schemaFields);
    }

    private List<ValidationError> validateConditionRule(RuleRequest request, List<SchemaFieldDefinition> schemaFields) {
        List<ValidationError> errors = new ArrayList<>();

        if (request.conditions() == null || request.conditions().isEmpty()) {
            return errors;
        }

        Map<String, SchemaFieldDefinition> fieldMap = new HashMap<>();
        for (SchemaFieldDefinition f : schemaFields) {
            fieldMap.put(f.name(), f);
        }

        Map<String, String> equalsValues = new HashMap<>();

        for (RuleCondition condition : request.conditions()) {
            String field = condition.field();
            if (field == null || field.isBlank()) {
                errors.add(new ValidationError(field, "Condition field name is blank"));
                continue;
            }

            String attrKey = field.startsWith("attributes.") ? field.substring("attributes.".length()) : field;

            if (attrKey.startsWith("exports.")) {
                continue;
            }

            if (!BUILTIN_FIELDS.contains(attrKey) && !fieldMap.containsKey(attrKey)) {
                errors.add(new ValidationError(field, "Field '" + attrKey + "' is not defined in the schema"));
                continue;
            }

            SchemaFieldDefinition schemaDef = fieldMap.get(attrKey);

            if (schemaDef != null && NUMERIC_OPERATORS.contains(condition.operator())) {
                if (!NUMERIC_TYPES.contains(schemaDef.type())) {
                    errors.add(new ValidationError(field,
                        "Operator " + condition.operator() + " is not compatible with field type " + schemaDef.type()));
                }
            }

            if (schemaDef != null && NUMERIC_OPERATORS.contains(condition.operator())) {
                try {
                    Double.parseDouble(condition.value());
                } catch (NumberFormatException e) {
                    errors.add(new ValidationError(field,
                        "Value '" + condition.value() + "' is not a valid number for operator " + condition.operator()));
                }
            }

            if (condition.operator() == ConditionOperator.EQUALS) {
                String prev = equalsValues.put(attrKey, condition.value());
                if (prev != null && !prev.equals(condition.value())) {
                    errors.add(new ValidationError(field,
                        "Impossible condition: field '" + attrKey + "' cannot equal both '" + prev + "' and '" + condition.value() + "'"));
                }
            }
        }

        return errors;
    }

    private List<ValidationError> validateVelocityRule(RuleRequest request, List<SchemaFieldDefinition> schemaFields) {
        List<ValidationError> errors = new ArrayList<>();

        String groupBy = request.groupByField();
        if (groupBy == null || groupBy.isBlank()) {
            errors.add(new ValidationError("groupByField", "groupByField is required for VELOCITY rules"));
            return errors;
        }

        String attrKey = groupBy.startsWith("attributes.") ? groupBy.substring("attributes.".length()) : groupBy;

        if (!BUILTIN_FIELDS.contains(attrKey)) {
            boolean found = schemaFields.stream().anyMatch(f -> f.name().equals(attrKey));
            if (!found) {
                errors.add(new ValidationError("groupByField",
                    "groupByField '" + attrKey + "' is not defined in the schema"));
            }
        }

        return errors;
    }
}
