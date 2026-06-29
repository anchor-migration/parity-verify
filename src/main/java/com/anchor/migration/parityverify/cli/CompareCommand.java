package com.anchor.migration.parityverify.cli;

import com.anchor.migration.parityverify.core.ParityDiffEngine;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.report.JsonReportWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "compare",
        description = "Compare before/after java-ast-ssot SQLite exports and emit a parity JSON report")
public final class CompareCommand implements Callable<Integer> {

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

    @Option(
            names = {"--fail-on-drift"},
            description = "Exit code 2 when removed or modified entities are present (default: false)")
    boolean failOnDrift;

    @Override
    public Integer call() throws Exception {
        ParityReport report = new ParityDiffEngine().compare(beforeDb, afterDb, linkedBeforeDb, linkedAfterDb);
        new JsonReportWriter().write(out, report);

        System.out.printf(
                "parity-verify: added=%d removed=%d modified=%d unchanged=%d structural_parity=%s%n",
                report.summary().addedCount(),
                report.summary().removedCount(),
                report.summary().modifiedCount(),
                report.summary().unchangedCount(),
                report.summary().structuralParity());
        System.out.println("Report: " + out.toAbsolutePath());

        if (failOnDrift && !report.summary().structuralParity()) {
            return 2;
        }
        return 0;
    }
}
