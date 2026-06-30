package com.anchor.migration.parityverify.report;

import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.EntityKind;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.model.ParitySummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportJsonParser {

    private static final String DEFAULT_VERSION = "0.2.0-SNAPSHOT";

    private ReportJsonParser() {}

    @SuppressWarnings("unchecked")
    public static ParityReport parseStructural(Map<String, Object> structural) {
        String toolVersion = stringOrDefault(structural.get("tool_version"), DEFAULT_VERSION);
        String beforeDb = stringOrNull(structural.get("before_db"));
        String afterDb = stringOrNull(structural.get("after_db"));
        String beforeRunId = stringOrNull(structural.get("before_export_run_id"));
        String afterRunId = stringOrNull(structural.get("after_export_run_id"));

        Map<String, Object> summaryMap = (Map<String, Object>) structural.get("summary");
        ParitySummary summary = parseSummary(summaryMap);

        List<ParityChange> changes = new ArrayList<>();
        List<Object> rawChanges = (List<Object>) structural.get("changes");
        if (rawChanges != null) {
            for (Object raw : rawChanges) {
                changes.add(parseChange((Map<String, Object>) raw));
            }
        }

        return new ParityReport(toolVersion, beforeDb, afterDb, beforeRunId, afterRunId, summary, changes);
    }

    @SuppressWarnings("unchecked")
    private static ParitySummary parseSummary(Map<String, Object> summaryMap) {
        if (summaryMap == null) {
            return new ParitySummary(0, 0, 0, 0, true);
        }
        return new ParitySummary(
                intVal(summaryMap.get("added_count")),
                intVal(summaryMap.get("removed_count")),
                intVal(summaryMap.get("modified_count")),
                intVal(summaryMap.get("unchanged_count")),
                boolVal(summaryMap.get("structural_parity")));
    }

    @SuppressWarnings("unchecked")
    private static ParityChange parseChange(Map<String, Object> changeMap) {
        EntityKind entityKind = parseEntityKind(stringOrNull(changeMap.get("entity_kind")));
        ChangeKind changeKind = parseChangeKind(stringOrNull(changeMap.get("change_kind")));
        String stableId = stringOrNull(changeMap.get("stable_id"));
        Map<String, String> before = parseAttrs((Map<String, Object>) changeMap.get("before"));
        Map<String, String> after = parseAttrs((Map<String, Object>) changeMap.get("after"));
        String note = stringOrNull(changeMap.get("note"));
        return new ParityChange(entityKind, changeKind, stableId, before, after, note);
    }

    private static Map<String, String> parseAttrs(Map<String, Object> attrs) {
        if (attrs == null) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            out.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        return out;
    }

    private static EntityKind parseEntityKind(String wireName) {
        if (wireName == null) {
            return EntityKind.JAVA_TYPE;
        }
        for (EntityKind kind : EntityKind.values()) {
            if (kind.wireName().equals(wireName)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown entity_kind: " + wireName);
    }

    private static ChangeKind parseChangeKind(String wireName) {
        if (wireName == null) {
            throw new IllegalArgumentException("Missing change_kind");
        }
        for (ChangeKind kind : ChangeKind.values()) {
            if (kind.wireName().equals(wireName)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown change_kind: " + wireName);
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private static boolean boolVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }
}
