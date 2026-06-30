package com.anchor.migration.parityverify.core;

import com.anchor.migration.parityverify.model.BehavioralCheck;
import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.CheckKind;
import com.anchor.migration.parityverify.model.CheckStatus;
import com.anchor.migration.parityverify.model.EntityKind;
import com.anchor.migration.parityverify.model.MatrixCheckSpec;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stateless evaluator for individual matrix check rules.
 */
final class RuleEngine {

    private final AstSsotReader reader = new AstSsotReader();

    BehavioralCheck evaluate(MatrixCheckSpec spec, MatrixContext context) {
        try {
            return switch (spec.rule()) {
                case "no_drift_outside" -> noDriftOutside(spec, context.parityReport());
                case "scoped_attr_allowlist" -> scopedAttrAllowlist(spec, context.parityReport());
                case "return_types_unchanged" -> returnTypesUnchanged(spec, context.parityReport());
                case "added_fields_count" -> addedFieldsCount(spec, context.parityReport());
                case "added_fields_min" -> addedFieldsMin(spec, context.parityReport());
                case "removed_methods_allowlist" -> removedMethodsAllowlist(spec, context.parityReport());
                case "zero_crosswalk_errors" -> zeroCrosswalkErrors(spec, context);
                case "min_link_count" -> minLinkCount(spec, context);
                case "min_field_links" -> minFieldLinks(spec, context);
                case "source_contains" -> sourceContains(spec, context, true);
                case "source_not_contains" -> sourceContains(spec, context, false);
                default -> unknown(spec, "Unknown rule: " + spec.rule());
            };
        } catch (Exception ex) {
            return new BehavioralCheck(spec.id(), spec.kind(), CheckStatus.UNKNOWN,
                    "Rule evaluation threw: " + ex.getMessage(), null);
        }
    }

    // ── rule implementations ──────────────────────────────────────────────────

    private BehavioralCheck noDriftOutside(MatrixCheckSpec spec, ParityReport report) {
        String prefix = requireScopePrefix(spec);
        List<String> unexpected = new ArrayList<>();
        for (ParityChange c : report.changes()) {
            if (c.changeKind() == ChangeKind.ADDED) continue;
            if (!inScope(c.stableId(), prefix)) {
                unexpected.add(c.changeKind().wireName() + " " + c.stableId());
            }
        }
        return unexpected.isEmpty()
                ? pass(spec, "No removed/modified entities outside scope " + prefix, null)
                : fail(spec, "Unexpected drift outside scope " + prefix, String.join("; ", unexpected));
    }

    private BehavioralCheck scopedAttrAllowlist(MatrixCheckSpec spec, ParityReport report) {
        String prefix = requireScopePrefix(spec);
        Set<String> allowedMethod = listToSet(spec.allowedMethodAttrs());
        Set<String> allowedType = listToSet(spec.allowedTypeAttrs());
        Set<String> allowedLink = listToSet(spec.allowedLinkAttrs());
        List<String> violations = new ArrayList<>();
        for (ParityChange c : report.changes()) {
            if (c.changeKind() != ChangeKind.MODIFIED || !inScope(c.stableId(), prefix)) continue;
            Set<String> allowed = switch (c.entityKind()) {
                case JAVA_METHOD -> allowedMethod;
                case JAVA_TYPE -> allowedType;
                case CODE_SCHEMA_LINK -> allowedLink;
                default -> Set.of();
            };
            for (String key : changedKeys(c.before(), c.after())) {
                if (!allowed.contains(key)) {
                    violations.add(c.stableId() + " attr=" + key);
                }
            }
        }
        return violations.isEmpty()
                ? pass(spec, "In-scope modifications limited to allowed attributes", null)
                : fail(spec, "In-scope modification outside allowlist", String.join("; ", violations));
    }

    private BehavioralCheck returnTypesUnchanged(MatrixCheckSpec spec, ParityReport report) {
        String prefix = requireScopePrefix(spec);
        List<String> drifted = new ArrayList<>();
        for (ParityChange c : report.changes()) {
            if (c.changeKind() != ChangeKind.MODIFIED
                    || c.entityKind() != EntityKind.JAVA_METHOD
                    || !inScope(c.stableId(), prefix)) continue;
            String before = c.before() == null ? null : c.before().get("return_type");
            String after = c.after() == null ? null : c.after().get("return_type");
            if (!Objects.equals(before, after)) {
                drifted.add(c.stableId() + ": " + before + " -> " + after);
            }
        }
        return drifted.isEmpty()
                ? pass(spec, "Scalar accessor return types unchanged", null)
                : fail(spec, "Return type drift", String.join("; ", drifted));
    }

    private BehavioralCheck addedFieldsCount(MatrixCheckSpec spec, ParityReport report) {
        if (spec.count() == null) return unknown(spec, "Missing required 'count' for added_fields_count rule");
        String prefix = requireScopePrefix(spec);
        long actual = report.changes().stream()
                .filter(c -> c.changeKind() == ChangeKind.ADDED
                        && c.entityKind() == EntityKind.JAVA_FIELD
                        && inScope(c.stableId(), prefix))
                .count();
        return actual == spec.count()
                ? pass(spec, "Exactly " + spec.count() + " fields added in scope", "count=" + actual)
                : fail(spec, "Expected exactly " + spec.count() + " added fields, got " + actual, "count=" + actual);
    }

    private BehavioralCheck addedFieldsMin(MatrixCheckSpec spec, ParityReport report) {
        if (spec.min() == null) return unknown(spec, "Missing required 'min' for added_fields_min rule");
        String prefix = requireScopePrefix(spec);
        long actual = report.changes().stream()
                .filter(c -> c.changeKind() == ChangeKind.ADDED
                        && c.entityKind() == EntityKind.JAVA_FIELD
                        && inScope(c.stableId(), prefix))
                .count();
        return actual >= spec.min()
                ? pass(spec, "At least " + spec.min() + " fields added in scope", "count=" + actual)
                : fail(spec, "Expected at least " + spec.min() + " added fields, got " + actual, "count=" + actual);
    }

    private BehavioralCheck removedMethodsAllowlist(MatrixCheckSpec spec, ParityReport report) {
        String prefix = requireScopePrefix(spec);
        List<String> allowedPatterns = spec.patterns() == null ? List.of() : spec.patterns();
        List<String> violations = new ArrayList<>();
        for (ParityChange c : report.changes()) {
            if (c.changeKind() != ChangeKind.REMOVED
                    || c.entityKind() != EntityKind.JAVA_METHOD
                    || !inScope(c.stableId(), prefix)) continue;
            boolean matched = allowedPatterns.stream()
                    .anyMatch(p -> c.stableId().contains(p));
            if (!matched) {
                violations.add(c.stableId());
            }
        }
        return violations.isEmpty()
                ? pass(spec, "All removed methods in scope match allowlist", null)
                : fail(spec, "Removed methods not in allowlist", String.join("; ", violations));
    }

    private BehavioralCheck zeroCrosswalkErrors(MatrixCheckSpec spec, MatrixContext context) {
        if (context.linkedAfterDb() == null) {
            return skip(spec, "No --linked-after provided");
        }
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int errors = reader.countCrosswalkIssueErrors(conn);
            return errors == 0
                    ? pass(spec, "Zero crosswalk errors in linked-after DB", null)
                    : fail(spec, "Crosswalk errors present", "error_count=" + errors);
        } catch (Exception ex) {
            return unknown(spec, ex.getMessage());
        }
    }

    private BehavioralCheck minLinkCount(MatrixCheckSpec spec, MatrixContext context) {
        if (spec.min() == null) return unknown(spec, "Missing required 'min' for min_link_count rule");
        if (context.linkedAfterDb() == null) return skip(spec, "No --linked-after provided");
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int links = reader.countCrosswalkLinks(conn);
            return links >= spec.min()
                    ? pass(spec, "At least " + spec.min() + " crosswalk links", "link_count=" + links)
                    : fail(spec, "Expected at least " + spec.min() + " links, got " + links, "link_count=" + links);
        } catch (Exception ex) {
            return unknown(spec, ex.getMessage());
        }
    }

    private BehavioralCheck minFieldLinks(MatrixCheckSpec spec, MatrixContext context) {
        if (spec.min() == null) return unknown(spec, "Missing required 'min' for min_field_links rule");
        if (spec.typeStableId() == null) return unknown(spec, "Missing required 'type_stable_id' for min_field_links rule");
        if (spec.profileId() == null) return unknown(spec, "Missing required 'profile_id' for min_field_links rule");
        if (context.linkedAfterDb() == null) return skip(spec, "No --linked-after provided");
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int links = reader.countFieldLinksForTypeProfile(conn, spec.typeStableId(), spec.profileId());
            return links >= spec.min()
                    ? pass(spec, "At least " + spec.min() + " field links for "
                            + spec.typeStableId() + "/" + spec.profileId(), "links=" + links)
                    : fail(spec, "Expected at least " + spec.min() + " field links, got " + links, "links=" + links);
        } catch (Exception ex) {
            return unknown(spec, ex.getMessage());
        }
    }

    private BehavioralCheck sourceContains(MatrixCheckSpec spec, MatrixContext context, boolean shouldContain) {
        Path source = context.touchpointSourceFile().orElse(null);
        if (source == null) return skip(spec, "No --touchpoint-source provided");
        List<String> pats = spec.patterns() == null ? List.of() : spec.patterns();
        if (pats.isEmpty()) return unknown(spec, "No patterns specified");
        try {
            if (!Files.isRegularFile(source)) {
                return fail(spec, "Touchpoint source file not found", source.toString());
            }
            String text = Files.readString(source);
            List<String> issues = new ArrayList<>();
            for (String p : pats) {
                boolean found = text.contains(p);
                if (shouldContain && !found) issues.add("missing: " + p);
                if (!shouldContain && found) issues.add("present: " + p);
            }
            return issues.isEmpty()
                    ? pass(spec, "Touchpoint source " + (shouldContain ? "contains" : "does not contain")
                            + " required patterns", source.toString())
                    : fail(spec, "Pattern mismatch in touchpoint source", String.join("; ", issues));
        } catch (Exception ex) {
            return unknown(spec, ex.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String requireScopePrefix(MatrixCheckSpec spec) {
        if (spec.scopePrefix() == null || spec.scopePrefix().isBlank()) {
            throw new IllegalArgumentException("Rule '" + spec.rule() + "' requires 'scope_prefix'");
        }
        return spec.scopePrefix();
    }

    static boolean inScope(String stableId, String prefix) {
        if (stableId.startsWith(prefix)) return true;
        if (stableId.contains("|")) {
            String[] parts = stableId.split("\\|", 3);
            return parts.length >= 2 && parts[1].startsWith(prefix);
        }
        return false;
    }

    private static Set<String> listToSet(List<String> list) {
        return list == null ? Set.of() : Set.copyOf(list);
    }

    private static Set<String> changedKeys(Map<String, String> before, Map<String, String> after) {
        TreeSet<String> keys = new TreeSet<>();
        if (before != null) keys.addAll(before.keySet());
        if (after != null) keys.addAll(after.keySet());
        keys.removeIf(k -> Objects.equals(
                before == null ? null : before.get(k),
                after == null ? null : after.get(k)));
        return keys;
    }

    private static BehavioralCheck pass(MatrixCheckSpec spec, String msg, String evidence) {
        return new BehavioralCheck(spec.id(), spec.kind(), CheckStatus.PASS, msg, evidence);
    }

    private static BehavioralCheck fail(MatrixCheckSpec spec, String msg, String evidence) {
        return new BehavioralCheck(spec.id(), spec.kind(), CheckStatus.FAIL, msg, evidence);
    }

    private static BehavioralCheck skip(MatrixCheckSpec spec, String msg) {
        return new BehavioralCheck(spec.id(), spec.kind(), CheckStatus.SKIP, msg, null);
    }

    private static BehavioralCheck unknown(MatrixCheckSpec spec, String msg) {
        return new BehavioralCheck(spec.id(), spec.kind(), CheckStatus.UNKNOWN, msg, null);
    }
}
