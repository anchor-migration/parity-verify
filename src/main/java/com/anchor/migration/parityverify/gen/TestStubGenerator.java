package com.anchor.migration.parityverify.gen;

import com.anchor.migration.parityverify.model.ChangeKind;
import com.anchor.migration.parityverify.model.EntityKind;
import com.anchor.migration.parityverify.model.ParityChange;
import com.anchor.migration.parityverify.model.ParityReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates a JUnit 5 test scaffold from a structural parity report.
 *
 * <p>The generator reads the {@code changes} array of a {@link ParityReport} and produces:
 * <ul>
 *   <li>One {@code @Test} per <em>modified method</em> — asserts return type is preserved.</li>
 *   <li>One {@code @Test} per <em>added field</em> — asserts field exists via reflection and
 *       optionally checks JPA column annotation.</li>
 *   <li>One {@code @Test} per <em>removed method</em> — negative assertion that the method no
 *       longer exists on the class.</li>
 *   <li>One {@code @Test} checking class-level JPA annotations when {@code jpaMode=true}.</li>
 * </ul>
 *
 * <p>Generated tests are <em>stubs</em>: they compile and run but contain {@code // TODO} markers
 * where a human (or further AI pass) should fill in real behavioral assertions.
 */
public final class TestStubGenerator {

    public static final class Config {
        public String packageName = "com.example.migration.generated";
        public String className = "MigrationParityTest";
        public String targetClass = "com.example.MigratedClass";
        public String scopePrefix = null;
        public boolean jpaMode = true;
        public String framework = "junit5";
    }

    /**
     * Generate test source code and write it to {@code outputPath}.
     * If {@code outputPath} is null, returns the generated source as a string.
     */
    public String generate(ParityReport report, Config config) {
        List<ParityChange> changes = filtered(report.changes(), config.scopePrefix);
        StringBuilder sb = new StringBuilder(4096);

        appendHeader(sb, config);

        List<ParityChange> modifiedMethods = changes.stream()
                .filter(c -> c.changeKind() == ChangeKind.MODIFIED && c.entityKind() == EntityKind.JAVA_METHOD)
                .toList();
        List<ParityChange> addedFields = changes.stream()
                .filter(c -> c.changeKind() == ChangeKind.ADDED && c.entityKind() == EntityKind.JAVA_FIELD)
                .toList();
        List<ParityChange> removedMethods = changes.stream()
                .filter(c -> c.changeKind() == ChangeKind.REMOVED && c.entityKind() == EntityKind.JAVA_METHOD)
                .toList();
        List<ParityChange> modifiedTypes = changes.stream()
                .filter(c -> c.changeKind() == ChangeKind.MODIFIED && c.entityKind() == EntityKind.JAVA_TYPE)
                .toList();

        appendClassHeader(sb, config, changes.size(),
                modifiedMethods.size(), addedFields.size(), removedMethods.size());

        if (config.jpaMode && !modifiedTypes.isEmpty()) {
            appendJpaAnnotationTest(sb, config, modifiedTypes);
        }

        for (ParityChange c : modifiedMethods) {
            appendReturnTypeTest(sb, c);
        }

        for (ParityChange c : addedFields) {
            appendFieldPresenceTest(sb, c, config);
        }

        for (ParityChange c : removedMethods) {
            appendRemovedMethodTest(sb, c);
        }

        appendClassFooter(sb);
        return sb.toString();
    }

    public void generateToFile(ParityReport report, Config config, Path outputPath) throws IOException {
        String source = generate(report, config);
        Path parent = outputPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(outputPath, source);
    }

    // ── builders ──────────────────────────────────────────────────────────────

    private static void appendHeader(StringBuilder sb, Config config) {
        sb.append("package ").append(config.packageName).append(";\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import java.lang.reflect.Field;\n");
        sb.append("import java.lang.reflect.Method;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
    }

    private static void appendClassHeader(StringBuilder sb, Config config, int total,
            int modified, int added, int removed) {
        sb.append("/**\n");
        sb.append(" * AI-generated migration parity stubs.\n");
        sb.append(" * Generated from parity report: ").append(total).append(" changes in scope\n");
        sb.append(" * (modified_methods=").append(modified)
          .append(", added_fields=").append(added)
          .append(", removed_methods=").append(removed).append(")\n");
        sb.append(" *\n");
        sb.append(" * TODO: Replace scaffold assertions with real behavioral tests.\n");
        sb.append(" */\n");
        sb.append("class ").append(config.className).append(" {\n\n");
        sb.append("    private static final String TARGET_CLASS = \"").append(config.targetClass).append("\";\n\n");
    }

    private static void appendJpaAnnotationTest(StringBuilder sb, Config config,
            List<ParityChange> modifiedTypes) {
        sb.append("    @Test\n");
        sb.append("    void classHasJpaAnnotations() throws Exception {\n");
        sb.append("        Class<?> clazz = Class.forName(TARGET_CLASS);\n");
        sb.append("        // @Entity is required after CMP -> JPA migration\n");
        sb.append("        assertNotNull(\n");
        sb.append("            clazz.getAnnotation(javax.persistence.Entity.class),\n");
        sb.append("            \"@Entity annotation missing on \" + TARGET_CLASS);\n");
        sb.append("        // @Table should map to the legacy CMP table name\n");
        sb.append("        assertNotNull(\n");
        sb.append("            clazz.getAnnotation(javax.persistence.Table.class),\n");
        sb.append("            \"@Table annotation missing on \" + TARGET_CLASS);\n");
        sb.append("        // TODO: assert @Table(name=...) matches legacy jbosscmp-jdbc.xml table name\n");
        sb.append("    }\n\n");
    }

    private static void appendReturnTypeTest(StringBuilder sb, ParityChange c) {
        String name = safeMethodName(c.stableId());
        String beforeReturn = attr(c.before(), "return_type");
        String note = c.note() == null ? "" : "  // drift: " + c.note();
        sb.append("    @Test\n");
        sb.append("    void ").append("returnType_").append(name).append("() throws Exception {\n");
        sb.append("        // stable_id: ").append(c.stableId()).append("\n");
        if (!note.isEmpty()) sb.append("        //").append(note).append("\n");
        sb.append("        Class<?> clazz = Class.forName(TARGET_CLASS);\n");
        String javaReturn = javaTypeName(beforeReturn);
        sb.append("        // Expected return type before migration: ").append(beforeReturn).append("\n");
        sb.append("        // TODO: call the method on a real instance and assert the return value\n");
        sb.append("        boolean methodExists = java.util.Arrays.stream(clazz.getMethods())\n");
        sb.append("            .anyMatch(m -> m.getName().equals(\"").append(methodSimpleName(c.stableId())).append("\")\n");
        sb.append("                       && m.getReturnType().getName().equals(\"").append(javaReturn).append("\"));\n");
        sb.append("        assertTrue(methodExists,\n");
        sb.append("            \"Method ").append(methodSimpleName(c.stableId()))
          .append(" with return type ").append(beforeReturn).append(" not found\");\n");
        sb.append("    }\n\n");
    }

    private static void appendFieldPresenceTest(StringBuilder sb, ParityChange c, Config config) {
        String name = safeFieldName(c.stableId());
        String fieldType = attr(c.after(), "field_type");
        sb.append("    @Test\n");
        sb.append("    void ").append("fieldPresent_").append(name).append("() throws Exception {\n");
        sb.append("        // stable_id: ").append(c.stableId()).append("\n");
        sb.append("        Class<?> clazz = Class.forName(TARGET_CLASS);\n");
        sb.append("        Field field = clazz.getDeclaredField(\"").append(fieldSimpleName(c.stableId())).append("\");\n");
        sb.append("        assertNotNull(field, \"Field ").append(fieldSimpleName(c.stableId())).append(" missing\");\n");
        if (config.jpaMode) {
            sb.append("        // JPA @Column annotation expected on each scalar field\n");
            sb.append("        assertNotNull(\n");
            sb.append("            field.getAnnotation(javax.persistence.Column.class),\n");
            sb.append("            \"@Column missing on field ").append(fieldSimpleName(c.stableId())).append("\");\n");
        }
        sb.append("        // TODO: assert field type is ").append(fieldType).append("\n");
        sb.append("        assertEquals(\"").append(fieldType).append("\",\n");
        sb.append("            field.getGenericType().getTypeName(),\n");
        sb.append("            \"Field type mismatch for ").append(fieldSimpleName(c.stableId())).append("\");\n");
        sb.append("    }\n\n");
    }

    private static void appendRemovedMethodTest(StringBuilder sb, ParityChange c) {
        String name = safeMethodName(c.stableId());
        sb.append("    @Test\n");
        sb.append("    void ").append("ejbMethodRemoved_").append(name).append("() throws Exception {\n");
        sb.append("        // stable_id: ").append(c.stableId()).append("\n");
        sb.append("        // EJB lifecycle / CMR method expected to be absent after migration\n");
        sb.append("        Class<?> clazz = Class.forName(TARGET_CLASS);\n");
        sb.append("        boolean exists = java.util.Arrays.stream(clazz.getMethods())\n");
        sb.append("            .anyMatch(m -> m.getName().equals(\"").append(methodSimpleName(c.stableId())).append("\"));\n");
        sb.append("        assertFalse(exists,\n");
        sb.append("            \"EJB method ").append(methodSimpleName(c.stableId())).append(" should not exist after migration\");\n");
        sb.append("    }\n\n");
    }

    private static void appendClassFooter(StringBuilder sb) {
        sb.append("}\n");
    }

    // ── utilities ─────────────────────────────────────────────────────────────

    private static List<ParityChange> filtered(List<ParityChange> changes, String scopePrefix) {
        if (scopePrefix == null || scopePrefix.isBlank()) return changes;
        List<ParityChange> out = new ArrayList<>();
        for (ParityChange c : changes) {
            String id = c.stableId();
            if (id.startsWith(scopePrefix)) { out.add(c); continue; }
            if (id.contains("|")) {
                String[] parts = id.split("\\|", 3);
                if (parts.length >= 2 && parts[1].startsWith(scopePrefix)) out.add(c);
            }
        }
        return out;
    }

    private static String attr(Map<String, String> attrs, String key) {
        return attrs == null ? "?" : attrs.getOrDefault(key, "?");
    }

    /** Fully qualified class#method(sig) → safe Java identifier. */
    static String safeMethodName(String stableId) {
        String local = stableId.contains("#") ? stableId.substring(stableId.indexOf('#') + 1) : stableId;
        return local.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+$", "");
    }

    static String safeFieldName(String stableId) {
        String local = stableId.contains("#") ? stableId.substring(stableId.indexOf('#') + 1) : stableId;
        return local.replaceAll("[^A-Za-z0-9_]", "_");
    }

    static String methodSimpleName(String stableId) {
        String local = stableId.contains("#") ? stableId.substring(stableId.indexOf('#') + 1) : stableId;
        return local.contains("(") ? local.substring(0, local.indexOf('(')) : local;
    }

    static String fieldSimpleName(String stableId) {
        return stableId.contains("#") ? stableId.substring(stableId.indexOf('#') + 1) : stableId;
    }

    /** Map AST simple type names to fully-qualified names for reflection where needed. */
    private static String javaTypeName(String simpleName) {
        if (simpleName == null) return "java.lang.Object";
        return switch (simpleName) {
            case "void" -> "void";
            case "int" -> "int";
            case "long" -> "long";
            case "boolean" -> "boolean";
            case "double" -> "double";
            case "float" -> "float";
            case "String" -> "java.lang.String";
            case "BigDecimal" -> "java.math.BigDecimal";
            case "Collection" -> "java.util.Collection";
            case "Date" -> "java.util.Date";
            default -> simpleName.contains(".") ? simpleName : "java.lang." + simpleName;
        };
    }
}
