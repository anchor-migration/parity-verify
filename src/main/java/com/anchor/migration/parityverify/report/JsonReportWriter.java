package com.anchor.migration.parityverify.report;

import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonReportWriter {

    public void write(Path output, ParityReport report) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, toJson(report));
    }

    String toJson(ParityReport report) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"tool_version\": ").append(q(report.toolVersion())).append(",\n");
        sb.append("  \"before_db\": ").append(q(report.beforeDb())).append(",\n");
        sb.append("  \"after_db\": ").append(q(report.afterDb())).append(",\n");
        sb.append("  \"before_export_run_id\": ").append(q(report.beforeExportRunId())).append(",\n");
        sb.append("  \"after_export_run_id\": ").append(q(report.afterExportRunId())).append(",\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"added_count\": ").append(report.summary().addedCount()).append(",\n");
        sb.append("    \"removed_count\": ").append(report.summary().removedCount()).append(",\n");
        sb.append("    \"modified_count\": ").append(report.summary().modifiedCount()).append(",\n");
        sb.append("    \"unchanged_count\": ").append(report.summary().unchangedCount()).append(",\n");
        sb.append("    \"structural_parity\": ").append(report.summary().structuralParity()).append("\n");
        sb.append("  },\n");
        sb.append("  \"changes\": [\n");
        for (int i = 0; i < report.changes().size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            appendChange(sb, report.changes().get(i));
        }
        if (!report.changes().isEmpty()) {
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendChange(StringBuilder sb, ParityChange change) {
        sb.append("    {\n");
        sb.append("      \"entity_kind\": ").append(q(change.entityKind().wireName())).append(",\n");
        sb.append("      \"change_kind\": ").append(q(change.changeKind().wireName())).append(",\n");
        sb.append("      \"stable_id\": ").append(q(change.stableId())).append(",\n");
        sb.append("      \"before\": ").append(attrs(change.before())).append(",\n");
        sb.append("      \"after\": ").append(attrs(change.after())).append(",\n");
        sb.append("      \"note\": ").append(q(change.note())).append("\n");
        sb.append("    }");
    }

    private static String attrs(Map<String, String> attrs) {
        if (attrs == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(q(entry.getKey())).append(": ").append(q(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String q(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
