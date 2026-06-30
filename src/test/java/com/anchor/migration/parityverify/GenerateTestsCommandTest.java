package com.anchor.migration.parityverify;

import com.anchor.migration.parityverify.gen.TestStubGenerator;
import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.EntityKind;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.model.ParitySummary;
import com.anchor.migration.parityverify.report.ReportJsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenerateTestsCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void reportJsonParserReadsWrappedStructuralReport() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("added_count", 1);
        summary.put("removed_count", 0);
        summary.put("modified_count", 0);
        summary.put("unchanged_count", 2);
        summary.put("structural_parity", false);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity_kind", "java_field");
        change.put("change_kind", "added");
        change.put("stable_id", "com.example.Foo#count");
        change.put("before", null);
        change.put("after", Map.of("name", "count", "field_type", "int", "modifiers", "private"));
        change.put("note", null);

        Map<String, Object> structural = new LinkedHashMap<>();
        structural.put("tool_version", "0.2.0-SNAPSHOT");
        structural.put("summary", summary);
        structural.put("changes", List.of(change));

        ParityReport report = ReportJsonParser.parseStructural(structural);

        assertEquals(1, report.summary().addedCount());
        assertEquals(1, report.changes().size());
        assertEquals("com.example.Foo#count", report.changes().get(0).stableId());
    }

    @Test
    void testStubGeneratorWritesJUnitScaffold() throws Exception {
        ParityReport report =
                new ParityReport(
                        "0.2.0-SNAPSHOT",
                        "before.db",
                        "after.db",
                        "1",
                        "1",
                        new ParitySummary(1, 1, 1, 0, false),
                        List.of(
                                new ParityChange(
                                        EntityKind.JAVA_METHOD,
                                        ChangeKind.MODIFIED,
                                        "com.example.Foo#getCount()",
                                        Map.of("return_type", "int"),
                                        Map.of("return_type", "int"),
                                        "modifiers drift"),
                                new ParityChange(
                                        EntityKind.JAVA_FIELD,
                                        ChangeKind.ADDED,
                                        "com.example.Foo#count",
                                        null,
                                        Map.of("name", "count", "field_type", "int"),
                                        null),
                                new ParityChange(
                                        EntityKind.JAVA_METHOD,
                                        ChangeKind.REMOVED,
                                        "com.example.Foo#ejbActivate()",
                                        Map.of("name", "ejbActivate"),
                                        null,
                                        null)));

        TestStubGenerator.Config config = new TestStubGenerator.Config();
        config.packageName = "com.example.test";
        config.className = "FooParityTest";
        config.targetClass = "com.example.Foo";
        config.scopePrefix = "com.example.Foo";
        config.jpaMode = true;

        Path out = tempDir.resolve("FooParityTest.java");
        new TestStubGenerator().generateToFile(report, config, out);
        String source = Files.readString(out);

        assertTrue(source.contains("class FooParityTest"));
        assertTrue(source.contains("com.example.Foo"));
        assertTrue(source.contains("@Test"));
        assertTrue(source.contains("fieldPresent_count"));
        assertTrue(source.contains("ejbMethodRemoved_ejbActivate"));
    }
}
