package com.anchor.migration.parityverify.matrix;

import com.anchor.migration.parityverify.core.BehavioralMatrixRunner;
import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class BuiltinMatrices {

    private static final Map<String, Function<MatrixContext, BehavioralMatrixResult>> REGISTRY =
            new HashMap<>();

    static {
        REGISTRY.put("dukesbank-cmp-jpa", BehavioralMatrixRunner::dukesbankCmpJpa);
    }

    private BuiltinMatrices() {}

    public static boolean exists(String matrixId) {
        return REGISTRY.containsKey(matrixId);
    }

    public static BehavioralMatrixResult run(String matrixId, MatrixContext context) {
        Function<MatrixContext, BehavioralMatrixResult> runner = REGISTRY.get(matrixId);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown behavioral matrix: " + matrixId);
        }
        return runner.apply(context);
    }
}
