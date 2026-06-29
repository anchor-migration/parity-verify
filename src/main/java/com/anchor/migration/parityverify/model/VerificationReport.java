package com.anchor.migration.parityverify.model;

public record VerificationReport(
        String toolVersion, ParityReport structural, BehavioralMatrixResult behavioral) {}
