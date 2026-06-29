package com.anchor.migration.parityverify.model;

import java.util.List;

public record BehavioralMatrixResult(String matrixId, String description, boolean allPassed, List<BehavioralCheck> checks) {}
