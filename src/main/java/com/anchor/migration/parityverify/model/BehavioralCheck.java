package com.anchor.migration.parityverify.model;

public record BehavioralCheck(
        String id,
        CheckKind kind,
        CheckStatus status,
        String message,
        String evidence) {}
