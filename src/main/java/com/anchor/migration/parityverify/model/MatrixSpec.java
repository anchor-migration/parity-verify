package com.anchor.migration.parityverify.model;

import java.util.List;

/**
 * Full parsed matrix YAML file.
 *
 * <p>YAML structure:
 * <pre>
 * id: my-matrix
 * description: "Human-readable description"
 * checks:
 *   - id: scope_guard
 *     kind: structural          # structural | crosswalk | behavioral
 *     rule: no_drift_outside
 *     scope_prefix: "com.example.Foo"
 *   - id: crosswalk_clean
 *     kind: crosswalk
 *     rule: zero_crosswalk_errors
 * </pre>
 */
public record MatrixSpec(String id, String description, List<MatrixCheckSpec> checks) {}
