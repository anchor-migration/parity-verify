package com.anchor.migration.parityverify.model;

public enum CheckKind {
    STRUCTURAL("structural"),
    CROSSWALK("crosswalk"),
    BEHAVIORAL("behavioral");

    private final String wireName;

    CheckKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
