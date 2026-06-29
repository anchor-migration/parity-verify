package com.anchor.migration.parityverify;

import com.anchor.migration.parityverify.core.BehavioralMatrixRunner;
import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.core.ParityDiffEngine;
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

        createBeforeDb(beforeDb);
        createAfterDb(afterDb);
        createLinkedAfterDb(linkedAfter);
        Files.writeString(
                touchpoint,
                """
                @javax.persistence.Entity
                @javax.persistence.Table(name = "ACCOUNT")
                public class AccountBean {}
                """);

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, linkedAfter);
        MatrixContext context =
                new MatrixContext(beforeDb, afterDb, null, linkedAfter, parity, Optional.of(touchpoint));
        BehavioralMatrixResult result = BehavioralMatrixRunner.dukesbankCmpJpa(context);

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
        createBeforeDb(beforeDb);
        createAfterDb(afterDb);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + beforeDb);
                Statement st = conn.createStatement()) {
            st.execute(
                    """
                    INSERT INTO java_method VALUES (99, 1, 1, 'com.example.Other#drift()', 'drift', 'void', 'public')
                    """);
        }

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, null);
        BehavioralMatrixResult result =
                BehavioralMatrixRunner.dukesbankCmpJpa(
                        new MatrixContext(beforeDb, afterDb, null, null, parity, Optional.empty()));

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
        createBeforeDb(beforeDb);
        createAfterDb(afterDb);

        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, null);
        BehavioralMatrixResult behavioral = BehavioralMatrixRunner.dukesbankCmpJpa(
                new MatrixContext(beforeDb, afterDb, null, null, parity, Optional.empty()));
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

    private static void createBeforeDb(Path dbPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement st = conn.createStatement()) {
            createSchema(st);
            st.execute("INSERT INTO export_run VALUES (1, '/src', datetime('now'), 'test', 1, '')");
            st.execute("INSERT INTO source_file VALUES (1, 1, 'AccountBean.java', 'java')");
            st.execute(
                    """
                    INSERT INTO java_type VALUES (1, 1, 1, 'com.sun.ebank.ejb.account.AccountBean',
                        'com.sun.ebank.ejb.account', 'AccountBean', 'class', NULL, 'EntityBean')
                    """);
            insertScalarAccessors(st, "PUBLIC ABSTRACT");
            st.execute(
                    """
                    INSERT INTO java_method VALUES (20, 1, 1, 'com.sun.ebank.ejb.account.AccountBean#ejbActivate()',
                        'ejbActivate', 'void', 'PUBLIC')
                    """);
            st.execute(
                    """
                    INSERT INTO java_field VALUES (1, 1, 1, 'com.sun.ebank.ejb.account.AccountBean#context',
                        'context', 'EntityContext', 'PRIVATE')
                    """);
        }
    }

    private static void createAfterDb(Path dbPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement st = conn.createStatement()) {
            createSchema(st);
            st.execute("INSERT INTO export_run VALUES (1, '/src', datetime('now'), 'test', 1, '')");
            st.execute("INSERT INTO source_file VALUES (1, 1, 'AccountBean.java', 'java')");
            st.execute(
                    """
                    INSERT INTO java_type VALUES (1, 1, 1, 'com.sun.ebank.ejb.account.AccountBean',
                        'com.sun.ebank.ejb.account', 'AccountBean', 'class', NULL, '')
                    """);
            insertScalarAccessors(st, "PUBLIC");
            insertJpaFields(st);
        }
    }

    private static void createLinkedAfterDb(Path dbPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement st = conn.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE crosswalk_run (
                        id INTEGER PRIMARY KEY, linked_at TEXT, tool_version TEXT,
                        code_db_path TEXT, schema_db_path TEXT,
                        code_export_run_id INTEGER, schema_export_run_id INTEGER, db_schema TEXT)
                    """);
            st.execute(
                    """
                    CREATE TABLE code_schema_link (
                        id INTEGER PRIMARY KEY, crosswalk_run_id INTEGER, edge_kind TEXT,
                        source_stable_id TEXT, target_stable_id TEXT, mapping_role TEXT,
                        profile_id TEXT, binding_source TEXT, evidence_ref TEXT, confidence TEXT,
                        name_drift_class TEXT, type_relation_forward TEXT, type_relation_backward TEXT,
                        color_forward TEXT, color_backward TEXT, round_trip_class TEXT,
                        normalized_source TEXT, normalized_target TEXT)
                    """);
            st.execute(
                    """
                    CREATE TABLE crosswalk_issue (
                        id INTEGER PRIMARY KEY, crosswalk_run_id INTEGER,
                        severity TEXT, issue_code TEXT, message TEXT, context_ref TEXT)
                    """);
            st.execute(
                    "INSERT INTO crosswalk_run VALUES (1, datetime('now'), 'test', 'c', 's', 1, 1, 'dukesbank')");
            String[] columns = {
                "accountId", "balance", "beginBalance", "beginBalanceTimeStamp", "creditLine", "description", "type"
            };
            int id = 1;
            for (String column : columns) {
                st.execute(
                        """
                        INSERT INTO code_schema_link VALUES (%d, 1, 'field_maps_to_column',
                            'com.sun.ebank.ejb.account.AccountBean#%s', 'dukesbank.ACCOUNT.%s',
                            'persistent_entity', 'jpa', 'test', NULL, 'authoritative',
                            'none', 'unknown', 'unknown', 'green', 'green', 'safe', NULL, NULL)
                        """
                                .formatted(id++, column, column.toUpperCase()));
            }
            for (int i = 0; i < 31; i++) {
                st.execute(
                        """
                        INSERT INTO code_schema_link VALUES (%d, 1, 'type_maps_to_table',
                            'com.example.Entity%d', 'dukesbank.T%d', 'persistent_entity', 'javaee-ejb2-jboss',
                            'test', NULL, 'authoritative', 'none', 'unknown', 'unknown',
                            'green', 'green', 'safe', NULL, NULL)
                        """
                                .formatted(id++, i, i));
            }
        }
    }

    private static void createSchema(Statement st) throws Exception {
        st.execute(
                """
                CREATE TABLE export_run (
                    id INTEGER PRIMARY KEY, source_root TEXT, exported_at TEXT,
                    tool_version TEXT, java_file_count INTEGER, profiles TEXT)
                """);
        st.execute(
                """
                CREATE TABLE source_file (
                    id INTEGER PRIMARY KEY, export_run_id INTEGER, relative_path TEXT, file_kind TEXT)
                """);
        st.execute(
                """
                CREATE TABLE java_type (
                    id INTEGER PRIMARY KEY, export_run_id INTEGER, source_file_id INTEGER,
                    stable_id TEXT, package_name TEXT, simple_name TEXT, kind TEXT,
                    extends_type TEXT, implements_list TEXT)
                """);
        st.execute(
                """
                CREATE TABLE java_method (
                    id INTEGER PRIMARY KEY, export_run_id INTEGER, type_id INTEGER,
                    stable_id TEXT, name TEXT, return_type TEXT, modifiers TEXT)
                """);
        st.execute(
                """
                CREATE TABLE java_field (
                    id INTEGER PRIMARY KEY, export_run_id INTEGER, type_id INTEGER,
                    stable_id TEXT, name TEXT, field_type TEXT, modifiers TEXT)
                """);
    }

    private static void insertScalarAccessors(Statement st, String modifiers) throws Exception {
        String[][] accessors = {
            {"getAccountId()", "getAccountId", "String"},
            {"getBalance()", "getBalance", "BigDecimal"},
            {"getBeginBalance()", "getBeginBalance", "BigDecimal"},
            {"getBeginBalanceTimeStamp()", "getBeginBalanceTimeStamp", "java.util.Date"},
            {"getCreditLine()", "getCreditLine", "BigDecimal"},
            {"getDescription()", "getDescription", "String"},
            {"getType()", "getType", "String"},
            {"setAccountId(String)", "setAccountId", "void"},
            {"setBalance(BigDecimal)", "setBalance", "void"},
            {"setBeginBalance(BigDecimal)", "setBeginBalance", "void"},
            {"setBeginBalanceTimeStamp(java.util.Date)", "setBeginBalanceTimeStamp", "void"},
            {"setCreditLine(BigDecimal)", "setCreditLine", "void"},
            {"setDescription(String)", "setDescription", "void"},
            {"setType(String)", "setType", "void"}
        };
        int id = 2;
        for (String[] accessor : accessors) {
            st.execute(
                    """
                    INSERT INTO java_method VALUES (%d, 1, 1, 'com.sun.ebank.ejb.account.AccountBean#%s',
                        '%s', '%s', '%s')
                    """
                            .formatted(id++, accessor[0], accessor[1], accessor[2], modifiers));
        }
    }

    private static void insertJpaFields(Statement st) throws Exception {
        String[][] fields = {
            {"accountId", "String"},
            {"balance", "BigDecimal"},
            {"beginBalance", "BigDecimal"},
            {"beginBalanceTimeStamp", "java.util.Date"},
            {"creditLine", "BigDecimal"},
            {"description", "String"},
            {"type", "String"}
        };
        int id = 1;
        for (String[] field : fields) {
            st.execute(
                    """
                    INSERT INTO java_field VALUES (%d, 1, 1, 'com.sun.ebank.ejb.account.AccountBean#%s',
                        '%s', '%s', 'PRIVATE')
                    """
                            .formatted(id++, field[0], field[0], field[1]));
        }
    }
}
