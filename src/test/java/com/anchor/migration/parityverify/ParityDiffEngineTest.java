package com.anchor.migration.parityverify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ParityDiffEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsRemovedMethodAsStructuralDrift() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        createCodeDb(beforeDb, true);
        createCodeDb(afterDb, false);

        var report =
                new com.anchor.migration.parityverify.core.ParityDiffEngine()
                        .compare(beforeDb, afterDb, null, null);

        assertEquals(0, report.summary().addedCount());
        assertEquals(1, report.summary().removedCount());
        assertEquals(0, report.summary().modifiedCount());
        assertFalse(report.summary().structuralParity());
        assertTrue(
                report.changes().stream()
                        .anyMatch(
                                c ->
                                        c.stableId().equals("com.example.Foo#bar()")
                                                && c.changeKind()
                                                        == com.anchor.migration.parityverify.model.ChangeKind
                                                                .REMOVED));
    }

    @Test
    void identicalSnapshotsHaveStructuralParity() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        createCodeDb(beforeDb, true);
        createCodeDb(afterDb, true);

        var report =
                new com.anchor.migration.parityverify.core.ParityDiffEngine()
                        .compare(beforeDb, afterDb, null, null);

        assertTrue(report.summary().structuralParity());
        assertEquals(0, report.summary().addedCount());
        assertEquals(0, report.summary().removedCount());
        assertEquals(0, report.summary().modifiedCount());
        assertEquals(3, report.summary().unchangedCount());
    }

    @Test
    void writesJsonReport() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        Path reportPath = tempDir.resolve("report.json");
        createCodeDb(beforeDb, true);
        createCodeDb(afterDb, false);

        var report =
                new com.anchor.migration.parityverify.core.ParityDiffEngine()
                        .compare(beforeDb, afterDb, null, null);
        new com.anchor.migration.parityverify.report.JsonReportWriter().write(reportPath, report);

        String json = java.nio.file.Files.readString(reportPath);
        assertTrue(json.contains("\"structural_parity\": false"));
        assertTrue(json.contains("\"entity_kind\": \"java_method\""));
        assertTrue(json.contains("com.example.Foo#bar()"));
    }

    private static void createCodeDb(Path dbPath, boolean includeBarMethod) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement st = conn.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE export_run (
                        id INTEGER PRIMARY KEY, source_root TEXT, exported_at TEXT,
                        tool_version TEXT, java_file_count INTEGER, profiles TEXT
                    )
                    """);
            st.execute(
                    """
                    CREATE TABLE source_file (
                        id INTEGER PRIMARY KEY, export_run_id INTEGER, relative_path TEXT, file_kind TEXT
                    )
                    """);
            st.execute(
                    """
                    CREATE TABLE java_type (
                        id INTEGER PRIMARY KEY, export_run_id INTEGER, source_file_id INTEGER,
                        stable_id TEXT, package_name TEXT, simple_name TEXT, kind TEXT,
                        extends_type TEXT, implements_list TEXT
                    )
                    """);
            st.execute(
                    """
                    CREATE TABLE java_method (
                        id INTEGER PRIMARY KEY, export_run_id INTEGER, type_id INTEGER,
                        stable_id TEXT, name TEXT, return_type TEXT, modifiers TEXT
                    )
                    """);
            st.execute(
                    """
                    CREATE TABLE java_field (
                        id INTEGER PRIMARY KEY, export_run_id INTEGER, type_id INTEGER,
                        stable_id TEXT, name TEXT, field_type TEXT, modifiers TEXT
                    )
                    """);
            st.execute(
                    "INSERT INTO export_run VALUES (1, '/src', datetime('now'), 'test', 1, '')");
            st.execute("INSERT INTO source_file VALUES (1, 1, 'com/example/Foo.java', 'java')");
            st.execute(
                    """
                    INSERT INTO java_type VALUES (1, 1, 1, 'com.example.Foo', 'com.example', 'Foo',
                        'class', NULL, NULL)
                    """);
            st.execute(
                    """
                    INSERT INTO java_field VALUES (1, 1, 1, 'com.example.Foo#count', 'count', 'int', 'private')
                    """);
            if (includeBarMethod) {
                st.execute(
                        """
                        INSERT INTO java_method VALUES (1, 1, 1, 'com.example.Foo#bar()', 'bar', 'void', 'public')
                        """);
            }
        }
    }
}
