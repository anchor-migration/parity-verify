package com.anchor.migration.parityverify.model;

public enum EntityKind {
    JAVA_TYPE("java_type"),
    JAVA_METHOD("java_method"),
    JAVA_FIELD("java_field"),
    CODE_SCHEMA_LINK("code_schema_link");

    private final String wireName;

    EntityKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
