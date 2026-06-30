package com.anchor.migration.parityverify.core;

import com.anchor.migration.parityverify.catalog.PatternCatalogLoader;
import com.anchor.migration.parityverify.model.BehavioralCheck;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.CheckKind;
import com.anchor.migration.parityverify.model.MatrixCheckSpec;
import com.anchor.migration.parityverify.model.MatrixSpec;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class YamlMatrixLoader {

    private final RuleEngine ruleEngine = new RuleEngine();
    private final PatternCatalogLoader patternCatalogLoader = new PatternCatalogLoader();

    public MatrixSpec load(Path yamlPath) throws IOException {
        return load(yamlPath, Optional.empty());
    }

    public MatrixSpec load(Path yamlPath, Optional<Path> catalogRoot) throws IOException {
        MatrixSpec spec;
        try (InputStream is = Files.newInputStream(yamlPath)) {
            spec = parse(is);
        }
        return resolvePattern(spec, catalogRoot);
    }

    public MatrixSpec load(InputStream is) {
        return load(is, Optional.empty());
    }

    public MatrixSpec load(InputStream is, Optional<Path> catalogRoot) {
        return resolvePattern(parse(is), catalogRoot);
    }

    public BehavioralMatrixResult evaluate(MatrixSpec spec, MatrixContext context) {
        List<BehavioralCheck> checks = new ArrayList<>();
        for (MatrixCheckSpec checkSpec : spec.checks()) {
            checks.add(ruleEngine.evaluate(checkSpec, context));
        }
        boolean allPassed = checks.stream().noneMatch(c ->
                c.status() == com.anchor.migration.parityverify.model.CheckStatus.FAIL);
        return new BehavioralMatrixResult(spec.id(), spec.description(), allPassed, checks);
    }

    MatrixSpec resolvePattern(MatrixSpec spec, Optional<Path> catalogRoot) {
        if (spec.patternId() == null || spec.patternId().isBlank()) {
            return spec;
        }
        try {
            if (catalogRoot.isPresent()) {
                return patternCatalogLoader.resolveMatrix(catalogRoot.get(), spec);
            }
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("patterns/" + spec.patternId() + ".yaml")) {
                if (is != null) {
                    MatrixSpec pattern = patternCatalogLoader.parsePatternResource(is, spec.patternId());
                    List<MatrixCheckSpec> merged =
                            PatternCatalogLoader.mergeChecks(pattern.checks(), spec.checks());
                    return new MatrixSpec(spec.id(), pickDescription(spec, pattern), spec.patternId(), merged);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve pattern_id=" + spec.patternId(), ex);
        }
        if (!spec.checks().isEmpty()) {
            return spec;
        }
        throw new IllegalArgumentException(
                "pattern_id=" + spec.patternId() + " requires --pattern-catalog or bundled pattern resource");
    }

    private static String pickDescription(MatrixSpec matrix, MatrixSpec pattern) {
        if (matrix.description() != null && !matrix.description().isBlank()) {
            return matrix.description();
        }
        return pattern.description();
    }

    @SuppressWarnings("unchecked")
    private static MatrixSpec parse(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);
        if (root == null) {
            throw new IllegalArgumentException("Empty or null YAML content");
        }

        String id = requireString(root, "id");
        String description = root.get("description") == null ? "" : root.get("description").toString();
        String patternId = root.get("pattern_id") == null ? null : root.get("pattern_id").toString();

        List<MatrixCheckSpec> checks = new ArrayList<>();
        List<Object> rawChecks = (List<Object>) root.get("checks");
        if (rawChecks != null) {
            for (Object rawCheck : rawChecks) {
                checks.add(parseCheckMap((Map<String, Object>) rawCheck));
            }
        }
        return new MatrixSpec(id, description, patternId, checks);
    }

    public MatrixCheckSpec parseCheckMap(Map<String, Object> m) {
        String id = requireString(m, "id");
        String kindStr = m.getOrDefault("kind", "structural").toString();
        CheckKind kind =
                switch (kindStr.toLowerCase()) {
                    case "crosswalk" -> CheckKind.CROSSWALK;
                    case "behavioral" -> CheckKind.BEHAVIORAL;
                    default -> CheckKind.STRUCTURAL;
                };
        String rule = requireString(m, "rule");
        String scopePrefix = m.get("scope_prefix") == null ? null : m.get("scope_prefix").toString();
        List<String> allowedMethodAttrs = stringList(m, "allowed_method_attrs");
        List<String> allowedTypeAttrs = stringList(m, "allowed_type_attrs");
        List<String> allowedLinkAttrs = stringList(m, "allowed_link_attrs");
        Integer count = intVal(m, "count");
        Integer min = intVal(m, "min");
        List<String> patterns = stringList(m, "patterns");
        String typeStableId = m.get("type_stable_id") == null ? null : m.get("type_stable_id").toString();
        String profileId = m.get("profile_id") == null ? null : m.get("profile_id").toString();

        return new MatrixCheckSpec(
                id,
                kind,
                rule,
                scopePrefix,
                allowedMethodAttrs,
                allowedTypeAttrs,
                allowedLinkAttrs,
                count,
                min,
                patterns,
                typeStableId,
                profileId);
    }

    private static String requireString(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof List<?> list) {
            return (List<String>) list;
        }
        return null;
    }

    private static Integer intVal(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(val.toString());
    }
}
