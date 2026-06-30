package com.anchor.migration.parityverify;

import com.anchor.migration.parityverify.core.MatrixContext;
import com.anchor.migration.parityverify.core.ParityDiffEngine;
import com.anchor.migration.parityverify.core.YamlMatrixLoader;
import com.anchor.migration.parityverify.matrix.BuiltinMatrices;
import com.anchor.migration.parityverify.model.BehavioralMatrixResult;
import com.anchor.migration.parityverify.model.CheckStatus;
import com.anchor.migration.parityverify.model.MatrixSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class YamlMatrixLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsDukesbankMatrixFromClasspath() {
        YamlMatrixLoader loader = new YamlMatrixLoader();
        try (var is = getClass().getClassLoader().getResourceAsStream("matrices/dukesbank-cmp-jpa.yaml")) {
            assertNotNull(is);
            MatrixSpec spec = loader.load(is);
            assertEquals("dukesbank-cmp-jpa", spec.id());
            assertEquals(8, spec.checks().size());
            assertEquals("module_quiescent", spec.checks().get(0).id());
            assertEquals("no_drift_outside", spec.checks().get(0).rule());
        }
    }

    @Test
    void yamlFileMatchesBuiltinOnAccountBeanFixture() throws Exception {
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
                new MatrixContext(beforeDb, afterDb, null, linkedAfter, parity, Optional.of(touchpoint));

        BehavioralMatrixResult builtin = BuiltinMatrices.run("dukesbank-cmp-jpa", context);
        Path yamlPath = Path.of("examples/matrices/dukesbank-cmp-jpa.yaml");
        BehavioralMatrixResult fromFile = BuiltinMatrices.runFromFile(yamlPath, context);

        assertTrue(builtin.allPassed());
        assertTrue(fromFile.allPassed());
        assertEquals(builtin.checks().size(), fromFile.checks().size());
        for (int i = 0; i < builtin.checks().size(); i++) {
            assertEquals(builtin.checks().get(i).id(), fromFile.checks().get(i).id());
            assertEquals(builtin.checks().get(i).status(), fromFile.checks().get(i).status());
        }
    }

    @Test
    void rejectsEmptyYaml() {
        YamlMatrixLoader loader = new YamlMatrixLoader();
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void scopedAttrAllowlistFailsOnUnexpectedAttribute() throws Exception {
        Path beforeDb = tempDir.resolve("before.db");
        Path afterDb = tempDir.resolve("after.db");
        MatrixFixtures.createBeforeDb(beforeDb);
        MatrixFixtures.createAfterDb(afterDb);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + afterDb);
                Statement st = conn.createStatement()) {
            st.execute(
                    """
                    UPDATE java_type SET implements_list = 'EntityBean'
                    WHERE stable_id = 'com.sun.ebank.ejb.account.AccountBean'
                    """);
            st.execute(
                    """
                    UPDATE java_type SET simple_name = 'AccountBeanRenamed'
                    WHERE stable_id = 'com.sun.ebank.ejb.account.AccountBean'
                    """);
        }

        String yaml =
                """
                id: test-matrix
                description: attr allowlist only
                checks:
                  - id: allowlist
                    kind: structural
                    rule: scoped_attr_allowlist
                    scope_prefix: com.sun.ebank.ejb.account.AccountBean
                    allowed_type_attrs: [implements_list]
                """;
        YamlMatrixLoader loader = new YamlMatrixLoader();
        MatrixSpec spec = loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        var parity = new ParityDiffEngine().compare(beforeDb, afterDb, null, null);
        BehavioralMatrixResult result =
                loader.evaluate(spec, new MatrixContext(beforeDb, afterDb, null, null, parity, Optional.empty()));

        assertFalse(result.allPassed());
        assertEquals(CheckStatus.FAIL, result.checks().get(0).status());
    }
}
