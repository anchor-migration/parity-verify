package com.anchor.migration.parityverify.report;

import com.anchor.migration.parityverify.model.BehavioralCheck;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.VerificationReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VerificationReportJsonWriter {

    private final JsonReportWriter structuralWriter = new JsonReportWriter();

    public void write(Path output, VerificationReport report) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, toJson(report));
    }

    String toJson(VerificationReport report) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"tool_version\": ").append(q(report.toolVersion())).append(",\n");
        sb.append("  \"structural\": ");
        appendStructuralObject(sb, report);
        if (report.behavioral() != null) {
            sb.append(",\n  \"behavioral\": ");
            appendBehavioralObject(sb, report.behavioral());
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private void appendStructuralObject(StringBuilder sb, VerificationReport report) {
        String inner = structuralWriter.toJson(report.structural());
        sb.append(inner.trim().replace("\n", "\n  "));
    }

    private void appendBehavioralObject(StringBuilder sb, BehavioralMatrixResult behavioral) {
        sb.append("{\n");
        sb.append("    \"matrix_id\": ").append(q(behavioral.matrixId())).append(",\n");
        sb.append("    \"description\": ").append(q(behavioral.description())).append(",\n");
        sb.append("    \"all_passed\": ").append(behavioral.allPassed()).append(",\n");
        sb.append("    \"checks\": [\n");
        for (int i = 0; i < behavioral.checks().size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            appendCheck(sb, behavioral.checks().get(i));
        }
        if (!behavioral.checks().isEmpty()) {
            sb.append('\n');
        }
        sb.append("    ]\n  }");
    }

    private static void appendCheck(StringBuilder sb, BehavioralCheck check) {
        sb.append("      {\n");
        sb.append("        \"id\": ").append(q(check.id())).append(",\n");
        sb.append("        \"kind\": ").append(q(check.kind().wireName())).append(",\n");
        sb.append("        \"status\": ").append(q(check.status().wireName())).append(",\n");
        sb.append("        \"message\": ").append(q(check.message())).append(",\n");
        sb.append("        \"evidence\": ").append(q(check.evidence())).append("\n");
        sb.append("      }");
    }

    private static String q(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
