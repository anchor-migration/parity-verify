package com.anchor.migration.parityverify.core;

import com.anchor.migration.parityverify.model.BehavioralCheck;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.CheckKind;
import com.anchor.migration.parityverify.model.CheckStatus;
import com.anchor.migration.parityverify.model.EntityKind;
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

public final class BehavioralMatrixRunner {

    private static final String ACCOUNT_BEAN = "com.sun.ebank.ejb.account.AccountBean";
    private static final Set<String> ALLOWED_METHOD_ATTRS = Set.of("modifiers");
    private static final Set<String> ALLOWED_TYPE_ATTRS = Set.of("implements_list");
    private static final Set<String> ALLOWED_LINK_ATTRS = Set.of("profile_id");

    private final AstSsotReader reader = new AstSsotReader();

    public static BehavioralMatrixResult dukesbankCmpJpa(MatrixContext context) {
        return new BehavioralMatrixRunner().runDukesbankCmpJpa(context);
    }

    private BehavioralMatrixResult runDukesbankCmpJpa(MatrixContext context) {
        List<BehavioralCheck> checks = new ArrayList<>();
        ParityReport report = context.parityReport();

        checks.add(moduleQuiescent(report));
        checks.add(scopedDriftAllowlist(report));
        checks.add(scalarAccessorReturnTypes(report));
        checks.add(jpaFieldsAdded(report));
        checks.add(crosswalkClean(context));
        checks.add(crosswalkLinkCount(context));
        checks.add(jpaScalarLinks(context));
        checks.add(touchpointEntityAnnotation(context));

        boolean allPassed = checks.stream().allMatch(c -> c.status() == CheckStatus.PASS);
        return new BehavioralMatrixResult(
                "dukesbank-cmp-jpa",
                "Duke's Bank CmpScalarEntityToJpa on AccountBean — scoped structural + crosswalk + touchpoint checks",
                allPassed,
                checks);
    }

    private BehavioralCheck moduleQuiescent(ParityReport report) {
        List<String> unexpected = new ArrayList<>();
        for (ParityChange change : report.changes()) {
            if (change.changeKind() == ChangeKind.ADDED) {
                continue;
            }
            if (!inScope(change.stableId(), ACCOUNT_BEAN)) {
                unexpected.add(change.changeKind().wireName() + " " + change.stableId());
            }
        }
        if (unexpected.isEmpty()) {
            return pass(
                    CheckKind.STRUCTURAL,
                    "module_quiescent",
                    "No removed or modified entities outside AccountBean scope",
                    null);
        }
        return fail(
                CheckKind.STRUCTURAL,
                "module_quiescent",
                "Unexpected drift outside AccountBean scope",
                String.join("; ", unexpected));
    }

    private BehavioralCheck scopedDriftAllowlist(ParityReport report) {
        List<String> violations = new ArrayList<>();
        for (ParityChange change : report.changes()) {
            if (change.changeKind() != ChangeKind.MODIFIED || !inScope(change.stableId(), ACCOUNT_BEAN)) {
                continue;
            }
            Set<String> allowed =
                    switch (change.entityKind()) {
                        case JAVA_METHOD -> ALLOWED_METHOD_ATTRS;
                        case JAVA_TYPE -> ALLOWED_TYPE_ATTRS;
                        case CODE_SCHEMA_LINK -> ALLOWED_LINK_ATTRS;
                        default -> Set.of();
                    };
            if (allowed.isEmpty()) {
                violations.add(change.stableId() + " (modified " + change.entityKind().wireName() + ")");
                continue;
            }
            for (String key : unionKeys(change.before(), change.after())) {
                if (!allowed.contains(key)
                        && !Objects.equals(
                                change.before() == null ? null : change.before().get(key),
                                change.after() == null ? null : change.after().get(key))) {
                    violations.add(change.stableId() + " attr=" + key);
                }
            }
        }
        if (violations.isEmpty()) {
            return pass(
                    CheckKind.STRUCTURAL,
                    "scoped_drift_allowlist",
                    "In-scope modifications limited to expected attributes",
                    null);
        }
        return fail(
                CheckKind.STRUCTURAL,
                "scoped_drift_allowlist",
                "In-scope modification outside allowlist",
                String.join("; ", violations));
    }

    private BehavioralCheck scalarAccessorReturnTypes(ParityReport report) {
        List<String> violations = new ArrayList<>();
        for (ParityChange change : report.changes()) {
            if (change.changeKind() != ChangeKind.MODIFIED
                    || change.entityKind() != EntityKind.JAVA_METHOD
                    || !inScope(change.stableId(), ACCOUNT_BEAN)) {
                continue;
            }
            String beforeReturn = change.before() == null ? null : change.before().get("return_type");
            String afterReturn = change.after() == null ? null : change.after().get("return_type");
            if (!Objects.equals(beforeReturn, afterReturn)) {
                violations.add(change.stableId() + ": " + beforeReturn + " -> " + afterReturn);
            }
        }
        if (violations.isEmpty()) {
            return pass(
                    CheckKind.BEHAVIORAL,
                    "scalar_accessor_return_types",
                    "Scalar accessor return types unchanged",
                    null);
        }
        return fail(
                CheckKind.BEHAVIORAL,
                "scalar_accessor_return_types",
                "Scalar accessor return type drift",
                String.join("; ", violations));
    }

    private BehavioralCheck jpaFieldsAdded(ParityReport report) {
        long addedFields =
                report.changes().stream()
                        .filter(
                                c ->
                                        c.changeKind() == ChangeKind.ADDED
                                                && c.entityKind() == EntityKind.JAVA_FIELD
                                                && inScope(c.stableId(), ACCOUNT_BEAN))
                        .count();
        if (addedFields == 7) {
            return pass(
                    CheckKind.STRUCTURAL,
                    "jpa_fields_added",
                    "Seven JPA scalar fields added on AccountBean",
                    "count=7");
        }
        return fail(
                CheckKind.STRUCTURAL,
                "jpa_fields_added",
                "Expected 7 added scalar fields on AccountBean",
                "count=" + addedFields);
    }

    private BehavioralCheck crosswalkClean(MatrixContext context) {
        if (context.linkedAfterDb() == null) {
            return skip(CheckKind.CROSSWALK, "crosswalk_clean", "No --linked-after database provided");
        }
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int errors = reader.countCrosswalkIssueErrors(conn);
            if (errors == 0) {
                return pass(CheckKind.CROSSWALK, "crosswalk_clean", "Linked after DB has zero crosswalk errors", null);
            }
            return fail(
                    CheckKind.CROSSWALK,
                    "crosswalk_clean",
                    "Crosswalk errors present in linked after DB",
                    "error_count=" + errors);
        } catch (Exception ex) {
            return unknown(CheckKind.CROSSWALK, "crosswalk_clean", ex.getMessage());
        }
    }

    private BehavioralCheck crosswalkLinkCount(MatrixContext context) {
        if (context.linkedAfterDb() == null) {
            return skip(CheckKind.CROSSWALK, "crosswalk_link_count", "No --linked-after database provided");
        }
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int links = reader.countCrosswalkLinks(conn);
            if (links >= 38) {
                return pass(
                        CheckKind.CROSSWALK,
                        "crosswalk_link_count",
                        "Linked after DB has expected crosswalk link volume",
                        "link_count=" + links);
            }
            return fail(
                    CheckKind.CROSSWALK,
                    "crosswalk_link_count",
                    "Expected at least 38 crosswalk links after migration",
                    "link_count=" + links);
        } catch (Exception ex) {
            return unknown(CheckKind.CROSSWALK, "crosswalk_link_count", ex.getMessage());
        }
    }

    private BehavioralCheck jpaScalarLinks(MatrixContext context) {
        if (context.linkedAfterDb() == null) {
            return skip(CheckKind.CROSSWALK, "jpa_scalar_links", "No --linked-after database provided");
        }
        try (Connection conn = reader.open(context.linkedAfterDb())) {
            int links = reader.countFieldLinksForTypeProfile(conn, ACCOUNT_BEAN, "jpa");
            if (links >= 7) {
                return pass(
                        CheckKind.CROSSWALK,
                        "jpa_scalar_links",
                        "JPA profile field_maps_to_column links present for AccountBean scalars",
                        "jpa_field_links=" + links);
            }
            return fail(
                    CheckKind.CROSSWALK,
                    "jpa_scalar_links",
                    "Expected at least 7 JPA scalar column links",
                    "jpa_field_links=" + links);
        } catch (Exception ex) {
            return unknown(CheckKind.CROSSWALK, "jpa_scalar_links", ex.getMessage());
        }
    }

    private BehavioralCheck touchpointEntityAnnotation(MatrixContext context) {
        Path source = context.touchpointSourceFile().orElse(null);
        if (source == null) {
            return skip(
                    CheckKind.BEHAVIORAL,
                    "touchpoint_entity_annotation",
                    "No --touchpoint-source provided");
        }
        try {
            if (!Files.isRegularFile(source)) {
                return fail(
                        CheckKind.BEHAVIORAL,
                        "touchpoint_entity_annotation",
                        "Touchpoint source file not found",
                        source.toString());
            }
            String text = Files.readString(source);
            List<String> missing = new ArrayList<>();
            if (!text.contains("@javax.persistence.Entity")) {
                missing.add("@javax.persistence.Entity");
            }
            if (!text.contains("@javax.persistence.Table")) {
                missing.add("@javax.persistence.Table");
            }
            if (missing.isEmpty()) {
                return pass(
                        CheckKind.BEHAVIORAL,
                        "touchpoint_entity_annotation",
                        "Touchpoint source contains JPA entity annotations",
                        source.toString());
            }
            return fail(
                    CheckKind.BEHAVIORAL,
                    "touchpoint_entity_annotation",
                    "Touchpoint source missing JPA annotations",
                    "missing=" + String.join(",", missing) + " path=" + source);
        } catch (Exception ex) {
            return unknown(CheckKind.BEHAVIORAL, "touchpoint_entity_annotation", ex.getMessage());
        }
    }

    private static boolean inScope(String stableId, String typePrefix) {
        if (stableId.startsWith(typePrefix)) {
            return true;
        }
        if (stableId.contains("|")) {
            String[] parts = stableId.split("\\|", 3);
            if (parts.length >= 2) {
                String source = parts[1];
                return source.startsWith(typePrefix);
            }
        }
        return false;
    }

    private static Set<String> unionKeys(Map<String, String> before, Map<String, String> after) {
        java.util.TreeSet<String> keys = new java.util.TreeSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        return keys;
    }

    private static BehavioralCheck pass(CheckKind kind, String id, String message, String evidence) {
        return new BehavioralCheck(id, kind, CheckStatus.PASS, message, evidence);
    }

    private static BehavioralCheck fail(CheckKind kind, String id, String message, String evidence) {
        return new BehavioralCheck(id, kind, CheckStatus.FAIL, message, evidence);
    }

    private static BehavioralCheck skip(CheckKind kind, String id, String message) {
        return new BehavioralCheck(id, kind, CheckStatus.SKIP, message, null);
    }

    private static BehavioralCheck unknown(CheckKind kind, String id, String message) {
        return new BehavioralCheck(id, kind, CheckStatus.UNKNOWN, message, null);
    }
}
