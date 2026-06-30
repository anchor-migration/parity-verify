package com.anchor.migration.parityverify.cli;

import com.anchor.migration.parityverify.gen.TestStubGenerator;
import com.anchor.migration.parityverify.model.ParityReport;
import com.anchor.migration.parityverify.report.ReportJsonParser;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "generate-tests",
        description = "Generate JUnit 5 test stubs from a structural parity JSON report")
public final class GenerateTestsCommand implements Callable<Integer> {

    @Option(names = {"--report", "-r"}, required = true,
            description = "Parity JSON report produced by the compare command")
    Path reportPath;

    @Option(names = {"--out", "-o"}, required = true,
            description = "Output .java file path for generated test stubs")
    Path out;

    @Option(names = {"--target-class"}, required = true,
            description = "Fully-qualified class name of the migrated class under test")
    String targetClass;

    @Option(names = {"--package"},
            description = "Java package for the generated test class (default: com.example.migration.generated)")
    String packageName = "com.example.migration.generated";

    @Option(names = {"--class-name"},
            description = "Simple name for the generated test class (default: MigrationParityTest)")
    String className = "MigrationParityTest";

    @Option(names = {"--scope"},
            description = "Stable-ID prefix filter — only generate tests for changes in this scope")
    String scopePrefix;

    @Option(names = {"--no-jpa"}, negatable = false,
            description = "Omit JPA annotation assertions (default: false, i.e. JPA mode on)")
    boolean noJpa;

    @Override
    public Integer call() throws Exception {
        String json = Files.readString(reportPath);
        ParityReport report = parseReport(json);

        TestStubGenerator.Config config = new TestStubGenerator.Config();
        config.packageName = packageName;
        config.className = className;
        config.targetClass = targetClass;
        config.scopePrefix = scopePrefix;
        config.jpaMode = !noJpa;

        TestStubGenerator gen = new TestStubGenerator();
        gen.generateToFile(report, config, out);

        System.out.println("Generated " + out.toAbsolutePath());
        return 0;
    }

    /**
     * Parse parity report JSON, supporting both v0.1 structural-only and v0.2 wrapped formats.
     */
    @SuppressWarnings("unchecked")
    private static ParityReport parseReport(String json) throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(json.replace(": null", ": ~"));

        Map<String, Object> structural;
        if (root.containsKey("structural")) {
            structural = (Map<String, Object>) root.get("structural");
        } else {
            structural = root;
        }

        return ReportJsonParser.parseStructural(structural);
    }
}
