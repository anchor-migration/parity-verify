package com.anchor.migration.parityverify.report;

import com.anchor.migration.parityverify.model.BehavioralCheck;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.CheckStatus;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.model.VerificationReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class HtmlReportWriter {

    public void write(Path output, VerificationReport report) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, toHtml(report));
    }

    String toHtml(VerificationReport report) {
        ParityReport structural = report.structural();
        BehavioralMatrixResult behavioral = report.behavioral();
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>parity-verify report</title>\n");
        sb.append("<style>\n");
        sb.append(css());
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<header><h1>parity-verify report</h1>");
        sb.append("<p class=\"meta\">tool ").append(esc(report.toolVersion()));
        if (behavioral != null) {
            sb.append(" · matrix <code>").append(esc(behavioral.matrixId())).append("</code>");
        }
        sb.append("</p></header>\n");

        sb.append("<section class=\"cards\">\n");
        appendSummaryCard(sb, "Added", structural.summary().addedCount(), "added");
        appendSummaryCard(sb, "Removed", structural.summary().removedCount(), "removed");
        appendSummaryCard(sb, "Modified", structural.summary().modifiedCount(), "modified");
        appendSummaryCard(sb, "Unchanged", structural.summary().unchangedCount(), "unchanged");
        appendSummaryCard(
                sb,
                "Structural parity",
                structural.summary().structuralParity() ? "true" : "false",
                structural.summary().structuralParity() ? "pass" : "fail");
        if (behavioral != null) {
            appendSummaryCard(
                    sb,
                    "Behavioral matrix",
                    behavioral.allPassed() ? "PASS" : "FAIL",
                    behavioral.allPassed() ? "pass" : "fail");
        }
        sb.append("</section>\n");

        if (behavioral != null) {
            sb.append("<section><h2>Behavioral matrix</h2>\n");
            sb.append("<p>").append(esc(behavioral.description())).append("</p>\n");
            sb.append("<table><thead><tr><th>Check</th><th>Kind</th><th>Status</th><th>Message</th><th>Evidence</th></tr></thead><tbody>\n");
            for (BehavioralCheck check : behavioral.checks()) {
                sb.append("<tr class=\"status-").append(check.status().wireName()).append("\">");
                sb.append("<td><code>").append(esc(check.id())).append("</code></td>");
                sb.append("<td>").append(esc(check.kind().wireName())).append("</td>");
                sb.append("<td><span class=\"badge\">").append(esc(check.status().wireName())).append("</span></td>");
                sb.append("<td>").append(esc(check.message())).append("</td>");
                sb.append("<td>").append(check.evidence() == null ? "" : "<code>" + esc(check.evidence()) + "</code>");
                sb.append("</td></tr>\n");
            }
            sb.append("</tbody></table></section>\n");
        }

        sb.append("<section><h2>Structural changes</h2>\n");
        sb.append("<p>Before: <code>").append(esc(structural.beforeDb())).append("</code><br>");
        sb.append("After: <code>").append(esc(structural.afterDb())).append("</code></p>\n");
        Map<ChangeKind, Long> counts = countByChangeKind(structural);
        sb.append("<p class=\"filters\">");
        for (ChangeKind kind : ChangeKind.values()) {
            sb.append("<span class=\"chip\">")
                    .append(kind.wireName())
                    .append(": ")
                    .append(counts.getOrDefault(kind, 0L))
                    .append("</span> ");
        }
        sb.append("</p>\n");
        sb.append("<table><thead><tr><th>Entity</th><th>Change</th><th>Stable ID</th><th>Note</th></tr></thead><tbody>\n");
        for (ParityChange change : structural.changes()) {
            sb.append("<tr class=\"change-").append(change.changeKind().wireName()).append("\">");
            sb.append("<td>").append(esc(change.entityKind().wireName())).append("</td>");
            sb.append("<td>").append(esc(change.changeKind().wireName())).append("</td>");
            sb.append("<td><code>").append(esc(change.stableId())).append("</code></td>");
            sb.append("<td>").append(change.note() == null ? "" : esc(change.note())).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table></section>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static void appendSummaryCard(StringBuilder sb, String label, Object value, String tone) {
        sb.append("<div class=\"card tone-").append(tone).append("\">");
        sb.append("<div class=\"card-label\">").append(esc(label)).append("</div>");
        sb.append("<div class=\"card-value\">").append(esc(String.valueOf(value))).append("</div>");
        sb.append("</div>\n");
    }

    private static Map<ChangeKind, Long> countByChangeKind(ParityReport report) {
        Map<ChangeKind, Long> counts = new EnumMap<>(ChangeKind.class);
        for (ParityChange change : report.changes()) {
            counts.merge(change.changeKind(), 1L, Long::sum);
        }
        return counts;
    }

    private static String css() {
        return """
                body { font-family: Segoe UI, system-ui, sans-serif; margin: 0; background: #f4f6f8; color: #1f2933; }
                header { background: #102a43; color: #fff; padding: 1.25rem 1.5rem; }
                header h1 { margin: 0 0 .25rem; font-size: 1.5rem; }
                .meta { margin: 0; opacity: .85; }
                section { background: #fff; margin: 1rem 1.5rem; padding: 1rem 1.25rem; border-radius: .5rem;
                    box-shadow: 0 1px 3px rgba(0,0,0,.08); }
                .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: .75rem; }
                .card { background: #fff; border-radius: .5rem; padding: .9rem 1rem; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
                .card-label { font-size: .8rem; text-transform: uppercase; letter-spacing: .04em; color: #627d98; }
                .card-value { font-size: 1.4rem; font-weight: 600; margin-top: .25rem; }
                .tone-pass .card-value { color: #0f7b4d; }
                .tone-fail .card-value { color: #b42318; }
                .tone-added .card-value { color: #175cd3; }
                .tone-removed .card-value { color: #b42318; }
                .tone-modified .card-value { color: #b54708; }
                table { width: 100%; border-collapse: collapse; font-size: .92rem; }
                th, td { text-align: left; padding: .55rem .45rem; border-bottom: 1px solid #e4e7eb; vertical-align: top; }
                th { font-size: .78rem; text-transform: uppercase; letter-spacing: .04em; color: #627d98; }
                code { font-family: Consolas, monospace; font-size: .88em; }
                .badge { display: inline-block; padding: .15rem .45rem; border-radius: 999px; font-size: .75rem;
                    font-weight: 600; text-transform: uppercase; }
                .status-pass .badge { background: #d1fadf; color: #027a48; }
                .status-fail .badge { background: #fee4e2; color: #b42318; }
                .status-skip .badge, .status-unknown .badge { background: #eaecf0; color: #475467; }
                .change-added { background: #f5faff; }
                .change-removed { background: #fff5f5; }
                .change-modified { background: #fffaeb; }
                .chip { display: inline-block; background: #eef2f6; border-radius: 999px; padding: .2rem .55rem;
                    margin-right: .35rem; font-size: .82rem; }
                """;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
