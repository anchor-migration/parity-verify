package com.anchor.migration.parityverify.model;

public record ParitySummary(
        int addedCount,
        int removedCount,
        int modifiedCount,
        int unchangedCount,
        boolean structuralParity) {}
