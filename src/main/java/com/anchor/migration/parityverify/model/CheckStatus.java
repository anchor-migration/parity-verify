package com.anchor.migration.parityverify.model;

public enum CheckStatus {
    PASS("pass"),
    FAIL("fail"),
    UNKNOWN("unknown"),
    SKIP("skip");

    private final String wireName;

    CheckStatus(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
