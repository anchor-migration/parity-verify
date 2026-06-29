# parity-verify

Part of **[Anchor Migration](https://github.com/anchor-migration/migration-hub)** — structural parity reports comparing **before** and **after** `java-ast-ssot` exports.

> [ARCHITECTURE.md — verification stage](https://github.com/anchor-migration/migration-hub/blob/main/docs/ARCHITECTURE.md)  
> [ADR-007 — proof until parity-verify](https://github.com/anchor-migration/migration-hub/blob/main/docs/ADR-007-rewrite-recipes-session-and-cmp-jpa.md)

**Status:** `0.1.0-SNAPSHOT` Alpha — AST subtree diff via stable IDs; behavioral parity and HTML reports are planned.

## Scope (v0.1)

| In scope | Out of scope (planned) |
|----------|-------------------------|
| Diff `java_type`, `java_method`, `java_field` stable IDs | Behavioral / integration test execution |
| Optional diff of `code_schema_link` rows | AI-assisted test case generation |
| JSON parity report | HTML report viewer |

## CLI

```bash
# Compare two java-ast-ssot exports
java -jar target/parity-verify-0.1.0-SNAPSHOT.jar compare \
  --before-db metadata/dukesbank-code-before.db \
  --after-db metadata/dukesbank-code-after.db \
  -o parity-report.json

# Include crosswalk-linked DBs (optional)
java -jar target/parity-verify-0.1.0-SNAPSHOT.jar compare \
  --before-db metadata/before-code.db \
  --after-db metadata/after-code.db \
  --linked-before metadata/before-linked.db \
  --linked-after metadata/after-linked.db \
  -o parity-report.json \
  --fail-on-drift
```

`--fail-on-drift` exits with code **2** when any entity was **removed** or **modified** (additions alone do not fail).

## Typical pipeline

```text
legacy sources  --java-ast-ssot export-->  before-code.db
migrated sources --java-ast-ssot export-->  after-code.db
before-code.db + after-code.db --parity-verify compare--> parity-report.json
```

After stack rewrite, expect **additions** (Spring/JPA types) and **targeted removals** (EJB lifecycle methods). Review the JSON `changes` array; `structural_parity: true` means no removals or attribute drift on matched stable IDs.

## JSON report (v0.1)

```json
{
  "tool_version": "0.1.0-SNAPSHOT",
  "summary": {
    "added_count": 0,
    "removed_count": 1,
    "modified_count": 0,
    "unchanged_count": 12,
    "structural_parity": false
  },
  "changes": [
    {
      "entity_kind": "java_method",
      "change_kind": "removed",
      "stable_id": "com.example.AccountBean#ejbActivate()",
      "before": { "name": "ejbActivate", "return_type": "void" },
      "after": null
    }
  ]
}
```

## Build

```bash
mvn test package
```

Docker (no local JDK/Maven):

```bash
docker run --rm -v "$PWD:/app" -w /app maven:3.9-eclipse-temurin-17 mvn -B test package
```

## License

MIT
