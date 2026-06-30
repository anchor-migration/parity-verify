package com.anchor.migration.parityverify.model;

import java.util.List;

/**
 * Parsed representation of a single check entry in a matrix YAML file.
 *
 * <p>Supported rules:
 * <ul>
 *   <li>{@code no_drift_outside} — no removed/modified stable IDs outside {@code scopePrefix}</li>
 *   <li>{@code scoped_attr_allowlist} — modifications only change listed attrs per entity kind</li>
 *   <li>{@code return_types_unchanged} — method return types unchanged for modified methods in scope</li>
 *   <li>{@code added_fields_count} — exactly {@code count} fields added in scope</li>
 *   <li>{@code added_fields_min} — at least {@code min} fields added in scope</li>
 *   <li>{@code removed_methods_allowlist} — all removed methods in scope match one of {@code patterns}</li>
 *   <li>{@code zero_crosswalk_errors} — zero crosswalk_issue rows in linked-after DB</li>
 *   <li>{@code min_link_count} — total crosswalk links &ge; {@code min}</li>
 *   <li>{@code min_field_links} — field_maps_to_column links for {@code typeStableId}/{@code profileId} &ge; {@code min}</li>
 *   <li>{@code source_contains} — touchpoint source file contains all {@code patterns}</li>
 *   <li>{@code source_not_contains} — touchpoint source file contains none of {@code patterns}</li>
 * </ul>
 */
public record MatrixCheckSpec(
        String id,
        CheckKind kind,
        String rule,
        String scopePrefix,
        List<String> allowedMethodAttrs,
        List<String> allowedTypeAttrs,
        List<String> allowedLinkAttrs,
        Integer count,
        Integer min,
        List<String> patterns,
        String typeStableId,
        String profileId) {}
