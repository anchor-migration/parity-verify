package com.anchor.migration.parityverify;

import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.core.ParityDiffEngine;
import com.anchor.migration.parityverify.matrix.BuiltinMatrices;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.CheckStatus;
import com.anchor.migration.parityverify.model.VerificationReport;
import com.anchor.migration.parityverify.report.HtmlReportWriter;
import com.anchor.migration.parityverify.report.VerificationReportJsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BehavioralMatrixRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void dukesbankMatrixPassesOnScopedAccountBeanDrift() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        Path linkedAfter = tempDir.resolve("linked-after.db");
        Path touchpoint = tempDir.resolve("AccountBean.java");

        MatrixFixtures.createBeforeDb(beforeDb);
        MatrixFixtures.createAfterDb(afterDb);
        MatrixFixtures.createLinkedAfterDb(linkedAfter);
        Files.writeString(
                touchpoint,
                """
                @javax.persistence.Entity
                @javax.persistence.Table(name = "ACCOUNT")
                public class AccountBean {}
                """);

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, linkedAfter);
        MatrixContext context =
                new MatrixContext(
                        beforeDb, afterDb, null, linkedAfter, parity, Optional.of(touchpoint), Optional.empty());
        BehavioralMatrixResult result = BuiltinMatrices.run("dukesbank-cmp-jpa", context);

        assertTrue(result.allPassed(), () -> result.checks().toString());
        assertEquals(8, result.checks().size());
        assertTrue(
                result.checks().stream()
                        .allMatch(c -> c.status() == CheckStatus.PASS || c.status() == CheckStatus.SKIP));
    }

    @Test
    void dukesbankMatrixFailsWhenModuleDriftsOutsideScope() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        MatrixFixtures.createBeforeDb(beforeDb);
        MatrixFixtures.createAfterDb(afterDb);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + beforeDb);
                Statement st = conn.createStatement()) {
            st.execute(
                    """
                    INSERT INTO java_method VALUES (99, 1, 1, 'com.example.Other#drift()', 'drift', 'void', 'public')
                    """);
        }

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, null);
        BehavioralMatrixResult result =
                BuiltinMatrices.run(
                        "dukesbank-cmp-jpa",
                        new MatrixContext(
                                beforeDb, afterDb, null, null, parity, Optional.empty(), Optional.empty()));

        assertFalse(result.allPassed());
        assertTrue(
                result.checks().stream()
                        .anyMatch(
                                c ->
                                        c.id().equals("module_quiescent")
                                                && c.status() == CheckStatus.FAIL));
    }

    @Test
    void writesCombinedJsonAndHtmlReports() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        MatrixFixtures.createBeforeDb(beforeDb);
        MatrixFixtures.createAfterDb(afterDb);

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, null);
        BehavioralMatrixResult behavioral =
                BuiltinMatrices.run(
                        "dukesbank-cmp-jpa",
                        new MatrixContext(
                                beforeDb, afterDb, null, null, parity, Optional.empty(), Optional.empty()));
        VerificationReport report = new VerificationReport("0.2.0-SNAPSHOT", parity, behavioral);

        Path jsonPath = tempDir.resolve("report.json");
        Path htmlPath = tempDir.resolve("report.html");
        new VerificationReportJsonWriter().write(jsonPath, report);
        new HtmlReportWriter().write(htmlPath, report);

        String json = Files.readString(jsonPath);
        String html = Files.readString(htmlPath);
        assertTrue(json.contains("\"behavioral\""));
        assertTrue(json.contains("\"matrix_id\": \"dukesbank-cmp-jpa\""));
        assertTrue(html.contains("Behavioral matrix"));
        assertTrue(html.contains("module_quiescent"));
    }
}
