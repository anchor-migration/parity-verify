package com.anchor.migration.parityverify.cli;

import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.core.ParityDiffEngine;
import com.anchor.migration.parityverify.matrix.BuiltinMatrices;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.model.VerificationReport;
import com.anchor.migration.parityverify.report.HtmlReportWriter;
import com.anchor.migration.parityverify.report.JsonReportWriter;
import com.anchor.migration.parityverify.report.VerificationReportJsonWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "compare",
        description =
                "Compare before/after java-ast-ssot SQLite exports; optional behavioral matrix and HTML report")
public final class CompareCommand implements Callable<Integer> {

    private static final String TOOL_VERSION = "0.2.0-SNAPSHOT";

    @Option(names = {"--before-db"}, required = true, description = "java-ast-ssot SQLite from legacy code")
    Path beforeDb;

    @Option(names = {"--after-db"}, required = true, description = "java-ast-ssot SQLite from migrated code")
    Path afterDb;

    @Option(names = {"--linked-before"}, description = "Optional crosswalk-linked SQLite from legacy export")
    Path linkedBeforeDb;

    @Option(names = {"--linked-after"}, description = "Optional crosswalk-linked SQLite from migrated export")
    Path linkedAfterDb;

    @Option(names = {"--out", "-o"}, required = true, description = "Output parity report JSON path")
    Path out;

    @Option(names = {"--html-out"}, description = "Optional HTML report path (requires --matrix)")
    Path htmlOut;

    @Option(
            names = {"--matrix"},
            description = "Built-in behavioral matrix id (e.g. dukesbank-cmp-jpa)")
    String matrix;

    @Option(
            names = {"--matrix-file"},
            description = "Path to a custom behavioral matrix YAML file")
    Path matrixFile;

    @Option(
            names = {"--pattern-catalog"},
            description = "Root of pattern-catalog repo (patterns/ subfolder); optional if pattern is bundled")
    Path patternCatalog;

    @Option(
            names = {"--touchpoint-source"},
            description = "Optional migrated source file for touchpoint behavioral checks")
    Path touchpointSource;

    @Option(
            names = {"--fail-on-drift"},
            description = "Exit code 2 when removed or modified entities are present (default: false)")
    boolean failOnDrift;

    @Option(
            names = {"--fail-on-matrix"},
            description = "Exit code 2 when any behavioral matrix check fails (default: false)")
    boolean failOnMatrix;

    @Override
    public Integer call() throws Exception {
        ParityReport report = new ParityDiffEngine().compare(beforeDb, afterDb, linkedBeforeDb, linkedAfterDb);
        if (matrix != null && matrixFile != null) {
            System.err.println("Specify either --matrix or --matrix-file, not both.");
            return 1;
        }
        BehavioralMatrixResult behavioral = null;
        if (matrix != null || matrixFile != null) {
            MatrixContext context = new MatrixContext(
                    beforeDb,
                    afterDb,
                    linkedBeforeDb,
                    linkedAfterDb,
                    report,
                    Optional.ofNullable(touchpointSource),
                    Optional.ofNullable(patternCatalog));
            if (matrix != null) {
                if (!BuiltinMatrices.exists(matrix)) {
                    System.err.println("Unknown built-in matrix: " + matrix);
                    System.err.println("Use --matrix-file to load a custom YAML matrix.");
                    return 1;
                }
                behavioral = BuiltinMatrices.run(matrix, context);
            } else {
                behavioral = BuiltinMatrices.runFromFile(matrixFile, context);
            }
        }

        if (behavioral != null) {
            VerificationReport verification =
                    new VerificationReport(TOOL_VERSION, report, behavioral);
            new VerificationReportJsonWriter().write(out, verification);
            if (htmlOut != null) {
                new HtmlReportWriter().write(htmlOut, verification);
            }
        } else {
            new JsonReportWriter().write(out, report);
            if (htmlOut != null) {
                throw new IllegalArgumentException("--html-out requires --matrix or --matrix-file");
            }
        }

        System.out.printf(
                "parity-verify: added=%d removed=%d modified=%d unchanged=%d structural_parity=%s%n",
                report.summary().addedCount(),
                report.summary().removedCount(),
                report.summary().modifiedCount(),
                report.summary().unchangedCount(),
                report.summary().structuralParity());
        System.out.println("Report: " + out.toAbsolutePath());
        if (htmlOut != null) {
            System.out.println("HTML: " + htmlOut.toAbsolutePath());
        }
        if (behavioral != null) {
            long failed =
                    behavioral.checks().stream()
                            .filter(c -> c.status() == com.anchor.migration.parityverify.model.CheckStatus.FAIL)
                            .count();
            System.out.printf(
                    "Matrix %s: all_passed=%s checks=%d failed=%d%n",
                    behavioral.matrixId(),
                    behavioral.allPassed(),
                    behavioral.checks().size(),
                    failed);
        }

        if (failOnDrift && !report.summary().structuralParity()) {
            return 2;
        }
        if (failOnMatrix && behavioral != null && !behavioral.allPassed()) {
            return 2;
        }
        return 0;
    }
}
