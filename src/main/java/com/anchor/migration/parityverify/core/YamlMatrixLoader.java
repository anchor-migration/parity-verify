package com.anchor.migration.parityverify.core;

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

/**
 * Loads a {@link MatrixSpec} from a YAML file and evaluates it against a {@link MatrixContext}.
 *
 * <p>YAML format (see {@code examples/matrices/} for full examples):
 * <pre>
 * id: my-migration-matrix
 * description: "Description of what this matrix checks"
 * checks:
 *   - id: scope_guard
 *     kind: structural
 *     rule: no_drift_outside
 *     scope_prefix: "com.example.Foo"
 *   - id: crosswalk_clean
 *     kind: crosswalk
 *     rule: zero_crosswalk_errors
 * </pre>
 */
public final class YamlMatrixLoader {

    private final RuleEngine ruleEngine = new RuleEngine();

    /** Parse a matrix YAML file from disk. */
    public MatrixSpec load(Path yamlPath) throws IOException {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            return parse(is);
        }
    }

    /** Parse from an already-open stream (used by tests). */
    public MatrixSpec load(InputStream is) {
        return parse(is);
    }

    /** Evaluate a loaded {@link MatrixSpec} against the given context. */
    public BehavioralMatrixResult evaluate(MatrixSpec spec, MatrixContext context) {
        List<BehavioralCheck> checks = new ArrayList<>();
        for (MatrixCheckSpec checkSpec : spec.checks()) {
            checks.add(ruleEngine.evaluate(checkSpec, context));
        }
        boolean allPassed = checks.stream().noneMatch(c ->
                c.status() == com.anchor.migration.parityverify.model.CheckStatus.FAIL);
        return new BehavioralMatrixResult(spec.id(), spec.description(), allPassed, checks);
    }

    // ── YAML → MatrixSpec ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static MatrixSpec parse(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);
        if (root == null) throw new IllegalArgumentException("Empty or null YAML content");

        String id = requireString(root, "id");
        String description = (String) root.getOrDefault("description", "");

        List<Object> rawChecks = (List<Object>) root.get("checks");
        if (rawChecks == null || rawChecks.isEmpty()) {
            return new MatrixSpec(id, description, List.of());
        }

        List<MatrixCheckSpec> checks = new ArrayList<>();
        for (Object rawCheck : rawChecks) {
            checks.add(parseCheck((Map<String, Object>) rawCheck));
        }
        return new MatrixSpec(id, description, checks);
    }

    @SuppressWarnings("unchecked")
    private static MatrixCheckSpec parseCheck(Map<String, Object> m) {
        String id = requireString(m, "id");
        String kindStr = (String) m.getOrDefault("kind", "structural");
        CheckKind kind = switch (kindStr.toLowerCase()) {
            case "crosswalk" -> CheckKind.CROSSWALK;
            case "behavioral" -> CheckKind.BEHAVIORAL;
            default -> CheckKind.STRUCTURAL;
        };
        String rule = requireString(m, "rule");
        String scopePrefix = (String) m.get("scope_prefix");
        List<String> allowedMethodAttrs = stringList(m, "allowed_method_attrs");
        List<String> allowedTypeAttrs = stringList(m, "allowed_type_attrs");
        List<String> allowedLinkAttrs = stringList(m, "allowed_link_attrs");
        Integer count = intVal(m, "count");
        Integer min = intVal(m, "min");
        List<String> patterns = stringList(m, "patterns");
        String typeStableId = (String) m.get("type_stable_id");
        String profileId = (String) m.get("profile_id");

        return new MatrixCheckSpec(id, kind, rule, scopePrefix,
                allowedMethodAttrs, allowedTypeAttrs, allowedLinkAttrs,
                count, min, patterns, typeStableId, profileId);
    }

    private static String requireString(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required key: " + key);
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) return null;
        if (val instanceof List<?> list) {
            return (List<String>) list;
        }
        return null;
    }

    private static Integer intVal(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
