package com.anchor.migration.parityverify.matrix;

import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.core.YamlMatrixLoader;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.MatrixSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class BuiltinMatrices {

    private static final Map<String, String> CLASSPATH_MATRICES =
            Map.of("dukesbank-cmp-jpa", "matrices/dukesbank-cmp-jpa.yaml");

    private static final YamlMatrixLoader LOADER = new YamlMatrixLoader();

    private BuiltinMatrices() {}

    public static boolean exists(String matrixId) {
        return CLASSPATH_MATRICES.containsKey(matrixId);
    }

    public static BehavioralMatrixResult run(String matrixId, MatrixContext context) {
        String resource = CLASSPATH_MATRICES.get(matrixId);
        if (resource == null) {
            throw new IllegalArgumentException("Unknown behavioral matrix: " + matrixId);
        }
        return evaluateClasspath(resource, context);
    }

    public static BehavioralMatrixResult runFromFile(Path yamlPath, MatrixContext context)
            throws IOException {
        MatrixSpec spec = LOADER.load(yamlPath, context.patternCatalogRoot());
        return LOADER.evaluate(spec, context);
    }

    private static BehavioralMatrixResult evaluateClasspath(String resource, MatrixContext context) {
        try (InputStream is = BuiltinMatrices.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath matrix resource: " + resource);
            }
            MatrixSpec spec = LOADER.load(is, context.patternCatalogRoot());
            return LOADER.evaluate(spec, context);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load matrix resource: " + resource, ex);
        }
    }
}
