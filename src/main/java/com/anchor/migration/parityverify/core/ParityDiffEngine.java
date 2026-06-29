package com.anchor.migration.parityverify.core;

import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.EntityKind;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.model.ParitySummary;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ParityDiffEngine {

    private static final String TOOL_VERSION = "0.2.0-SNAPSHOT";

    private final AstSsotReader reader = new AstSsotReader();

    public ParityReport compare(
            Path beforeDb, Path afterDb, Path linkedBeforeDb, Path linkedAfterDb) throws Exception {
        try (Connection beforeConn = reader.open(beforeDb);
                Connection afterConn = reader.open(afterDb)) {
            AstSsotReader.ExportRunRef beforeRun = reader.latestExportRun(beforeConn);
            AstSsotReader.ExportRunRef afterRun = reader.latestExportRun(afterConn);

            List<ParityChange> changes = new ArrayList<>();
            int unchanged = 0;
            unchanged += diffEntity(
                    changes,
                    EntityKind.JAVA_TYPE,
                    reader.loadTypes(beforeConn, beforeRun.id()),
                    reader.loadTypes(afterConn, afterRun.id()));
            unchanged += diffEntity(
                    changes,
                    EntityKind.JAVA_METHOD,
                    reader.loadMethods(beforeConn, beforeRun.id()),
                    reader.loadMethods(afterConn, afterRun.id()));
            unchanged += diffEntity(
                    changes,
                    EntityKind.JAVA_FIELD,
                    reader.loadFields(beforeConn, beforeRun.id()),
                    reader.loadFields(afterConn, afterRun.id()));

            if (linkedBeforeDb != null && linkedAfterDb != null) {
                try (Connection linkedBefore = reader.open(linkedBeforeDb);
                        Connection linkedAfter = reader.open(linkedAfterDb)) {
                    unchanged +=
                            diffEntity(
                                    changes,
                                    EntityKind.CODE_SCHEMA_LINK,
                                    reader.loadCrosswalkLinks(linkedBefore),
                                    reader.loadCrosswalkLinks(linkedAfter));
                }
            }

            int added = count(changes, ChangeKind.ADDED);
            int removed = count(changes, ChangeKind.REMOVED);
            int modified = count(changes, ChangeKind.MODIFIED);
            boolean structuralParity = removed == 0 && modified == 0;

            ParitySummary summary = new ParitySummary(added, removed, modified, unchanged, structuralParity);
            return new ParityReport(
                    TOOL_VERSION,
                    beforeDb.toString(),
                    afterDb.toString(),
                    Integer.toString(beforeRun.id()),
                    Integer.toString(afterRun.id()),
                    summary,
                    changes);
        }
    }

    private static int diffEntity(
            List<ParityChange> changes,
            EntityKind kind,
            Map<String, Map<String, String>> before,
            Map<String, Map<String, String>> after) {
        Set<String> ids = new TreeSet<>();
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());

        int unchanged = 0;
        for (String id : ids) {
            Map<String, String> beforeAttrs = before.get(id);
            Map<String, String> afterAttrs = after.get(id);
            if (beforeAttrs == null) {
                changes.add(new ParityChange(kind, ChangeKind.ADDED, id, null, afterAttrs, null));
            } else if (afterAttrs == null) {
                changes.add(new ParityChange(kind, ChangeKind.REMOVED, id, beforeAttrs, null, null));
            } else if (attrsEqual(beforeAttrs, afterAttrs)) {
                unchanged++;
            } else {
                changes.add(
                        new ParityChange(
                                kind,
                                ChangeKind.MODIFIED,
                                id,
                                beforeAttrs,
                                afterAttrs,
                                describeAttrDiff(beforeAttrs, afterAttrs)));
            }
        }
        return unchanged;
    }

    private static boolean attrsEqual(Map<String, String> before, Map<String, String> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!Objects.equals(entry.getValue(), after.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static String describeAttrDiff(Map<String, String> before, Map<String, String> after) {
        List<String> parts = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                parts.add(key + ": " + before.get(key) + " -> " + after.get(key));
            }
        }
        return String.join("; ", parts);
    }

    private static int count(List<ParityChange> changes, ChangeKind kind) {
        int total = 0;
        for (ParityChange change : changes) {
            if (change.changeKind() == kind) {
                total++;
            }
        }
        return total;
    }
}
