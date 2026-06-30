package com.anchor.migration.parityverify.catalog;

import com.anchor.migration.parityverify.core.YamlMatrixLoader;
import com.anchor.migration.parityverify.model.MatrixCheckSpec;
import com.anchor.migration.parityverify.model.MatrixSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads migration patterns from a pattern-catalog repository layout:
 * {@code {root}/patterns/{pattern-id}.yaml}.
 */
public final class PatternCatalogLoader {

    public MatrixSpec loadPattern(Path catalogRoot, String patternId) throws IOException {
        Path patternFile = catalogRoot.resolve("patterns").resolve(patternId + ".yaml");
        if (!Files.isRegularFile(patternFile)) {
            throw new IOException("Pattern not found: " + patternFile);
        }
        try (InputStream is = Files.newInputStream(patternFile)) {
            return parsePattern(is, patternId);
        }
    }

    public Optional<MatrixSpec> tryLoadPattern(Path catalogRoot, String patternId) {
        try {
            return Optional.of(loadPattern(catalogRoot, patternId));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public MatrixSpec resolveMatrix(Path catalogRoot, MatrixSpec matrixSpec) throws IOException {
        if (matrixSpec.patternId() == null || matrixSpec.patternId().isBlank()) {
            return matrixSpec;
        }
        MatrixSpec pattern = loadPattern(catalogRoot, matrixSpec.patternId());
        List<MatrixCheckSpec> merged = mergeChecks(pattern.checks(), matrixSpec.checks());
        return new MatrixSpec(
                matrixSpec.id(),
                matrixSpec.description() == null || matrixSpec.description().isBlank()
                        ? pattern.description()
                        : matrixSpec.description(),
                matrixSpec.patternId(),
                merged);
    }

    public static List<MatrixCheckSpec> mergeChecks(List<MatrixCheckSpec> base, List<MatrixCheckSpec> overrides) {
        Map<String, MatrixCheckSpec> merged = new LinkedHashMap<>();
        for (MatrixCheckSpec check : base) {
            merged.put(check.id(), check);
        }
        for (MatrixCheckSpec check : overrides) {
            merged.put(check.id(), check);
        }
        return new ArrayList<>(merged.values());
    }

    public MatrixSpec parsePatternResource(InputStream is, String expectedId) {
        return parsePattern(is, expectedId);
    }

    @SuppressWarnings("unchecked")
    private MatrixSpec parsePattern(InputStream is, String expectedId) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);
        if (root == null) {
            throw new IllegalArgumentException("Empty pattern YAML");
        }
        String id = root.get("id") == null ? expectedId : root.get("id").toString();
        String description = root.get("description") == null ? "" : root.get("description").toString();

        List<MatrixCheckSpec> checks = new ArrayList<>();
        Object verification = root.get("verification");
        if (verification instanceof Map<?, ?> verificationMap) {
            Object rawChecks = verificationMap.get("checks");
            if (rawChecks instanceof List<?> list) {
                for (Object raw : list) {
                    checks.add(YamlMatrixLoader.parseCheckMap((Map<String, Object>) raw));
                }
            }
        }
        Object topChecks = root.get("checks");
        if (topChecks instanceof List<?> list) {
            for (Object raw : list) {
                checks.add(YamlMatrixLoader.parseCheckMap((Map<String, Object>) raw));
            }
        }
        return new MatrixSpec(id, description, id, checks);
    }
}
