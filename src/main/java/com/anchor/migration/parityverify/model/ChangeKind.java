package com.anchor.migration.parityverify.model;

public enum ChangeKind {
    ADDED("added"),
    REMOVED("removed"),
    MODIFIED("modified");

    private final String wireName;

    ChangeKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
