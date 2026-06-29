package com.example.fraud.schema;

public enum SchemaFieldType {
    KEYWORD, TEXT, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, IP, GEO_POINT;

    public String toEsType() {
        return name().toLowerCase();
    }
}
