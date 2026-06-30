# parity-verify

Part of **[Anchor Migration](https://github.com/anchor-migration/migration-hub)** — structural parity reports comparing **before** and **after** `java-ast-ssot` exports.

> [ARCHITECTURE.md — verification stage](https://github.com/anchor-migration/migration-hub/blob/main/docs/ARCHITECTURE.md)  
> [ADR-007 — proof until parity-verify](https://github.com/anchor-migration/migration-hub/blob/main/docs/ADR-007-rewrite-recipes-session-and-cmp-jpa.md)

**Status:** `0.2.0-SNAPSHOT` Beta — AST subtree diff, behavioral matrix checks, and HTML reports.

## Scope (v0.2)

| In scope | Out of scope (planned) |
|----------|-------------------------|
| Diff `java_type`, `java_method`, `java_field` stable IDs | External integration test runners |
| Optional diff of `code_schema_link` rows | Full behavioral test execution |
| JSON parity report | |
| **HTML parity report** (`--html-out`) | |
| **Behavioral matrix** (`--matrix` / `--matrix-file` + `pattern_id`) | |
| **generate-tests** — JUnit 5 stubs from parity JSON | |

## CLI

```bash
# Compare two java-ast-ssot exports
java -jar target/parity-verify-0.2.0-SNAPSHOT.jar compare \
  --before-db metadata/dukesbank-code-before.db \
  --after-db metadata/dukesbank-code-after.db \
  -o parity-report.json

# Structural diff + built-in matrix (loads YAML from classpath)
java -jar target/parity-verify-0.2.0-SNAPSHOT.jar compare \
  --before-db metadata/dukesbank-code-before.db \
  --after-db metadata/dukesbank-code-after.db \
  --linked-before metadata/dukesbank-linked-before.db \
  --linked-after metadata/dukesbank-linked-after.db \
  --matrix dukesbank-cmp-jpa \
  --touchpoint-source /work/src/.../AccountBean.java \
  -o parity-report.json \
  --html-out parity-report.html \
  --fail-on-matrix

# Same matrix from a custom YAML file on disk
java -jar target/parity-verify-0.2.0-SNAPSHOT.jar compare \
  --before-db metadata/dukesbank-code-before.db \
  --after-db metadata/dukesbank-code-after.db \
  --linked-after metadata/dukesbank-linked-after.db \
  --matrix-file examples/matrices/dukesbank-cmp-jpa.yaml \
  --touchpoint-source /work/src/.../AccountBean.java \
  -o parity-report.json

# Include crosswalk-linked DBs (optional)
java -jar target/parity-verify-0.2.0-SNAPSHOT.jar compare \
  --before-db metadata/before-code.db \
  --after-db metadata/after-code.db \
  --linked-before metadata/before-linked.db \
  --linked-after metadata/after-linked.db \
  -o parity-report.json \
  --fail-on-drift
```

`--fail-on-drift` exits with code **2** when any entity was **removed** or **modified** (additions alone do not fail).

`--fail-on-matrix` exits with code **2** when any behavioral matrix check has status `fail`.

## Typical pipeline

```text
legacy sources  --java-ast-ssot export-->  before-code.db
migrated sources --java-ast-ssot export-->  after-code.db
before-code.db + after-code.db --parity-verify compare--> parity-report.json
```

After stack rewrite, expect **additions** (Spring/JPA types) and **targeted removals** (EJB lifecycle methods). Review the JSON `changes` array; `structural_parity: true` means no removals or attribute drift on matched stable IDs.

## Behavioral matrix (`dukesbank-cmp-jpa`)

Scoped checks for Duke's Bank `CmpScalarEntityToJpa` on `AccountBean` — use `--matrix dukesbank-cmp-jpa` with linked DBs and optional `--touchpoint-source` (migrated `AccountBean.java`).

| Check | Kind | Pass criteria |
|-------|------|---------------|
| `module_quiescent` | structural | No removed/modified entities outside `AccountBean` |
| `scoped_drift_allowlist` | structural | In-scope mods limited to `modifiers`, `implements_list`, `profile_id` |
| `scalar_accessor_return_types` | behavioral | Getter/setter `return_type` unchanged |
| `jpa_fields_added` | structural | Exactly 7 added scalar fields on `AccountBean` |
| `crosswalk_clean` | crosswalk | Zero `crosswalk_issue` errors in `--linked-after` |
| `crosswalk_link_count` | crosswalk | ≥ 38 links in `--linked-after` |
| `jpa_scalar_links` | crosswalk | ≥ 7 JPA `field_maps_to_column` links for `AccountBean` |
| `touchpoint_entity_annotation` | behavioral | Touchpoint file contains `@javax.persistence.Entity` and `@Table` |

Combined JSON wraps structural + behavioral under `structural` / `behavioral` keys. HTML report (`--html-out`) includes summary cards, matrix table, and change list.

Use `--fail-on-matrix` in CI when behavioral checks should gate the pipeline (distinct from raw `--fail-on-drift`).

## Custom matrix YAML

Built-in matrix ids (e.g. `dukesbank-cmp-jpa`) load YAML from `src/main/resources/matrices/` and resolve `pattern_id` from bundled `src/main/resources/patterns/` or `--pattern-catalog`.

```yaml
id: dukesbank-cmp-jpa
pattern_id: cmp-scalar-entity-to-jpa-account-bean
description: "Checklist from pattern-catalog"
checks: []   # optional overrides merged by check id
```

### Rule reference

| `rule` | Required fields | Description |
|--------|-----------------|-------------|
| `no_drift_outside` | `scope_prefix` | No removed/modified stable IDs outside scope |
| `scoped_attr_allowlist` | `scope_prefix`, `allowed_*_attrs` | In-scope mods only change listed attributes |
| `return_types_unchanged` | `scope_prefix` | Modified methods keep `return_type` |
| `added_fields_count` | `scope_prefix`, `count` | Exactly N added fields in scope |
| `added_fields_min` | `scope_prefix`, `min` | At least N added fields in scope |
| `removed_methods_allowlist` | `scope_prefix`, `patterns` | Removed methods must match a substring pattern |
| `zero_crosswalk_errors` | — | Zero `crosswalk_issue` errors in `--linked-after` |
| `min_link_count` | `min` | Total crosswalk links ≥ min |
| `min_field_links` | `type_stable_id`, `profile_id`, `min` | JPA/EJB field column links for a type |
| `source_contains` | `patterns` | `--touchpoint-source` contains all patterns |
| `source_not_contains` | `patterns` | Touchpoint source contains none of the patterns |

Check `kind`: `structural`, `crosswalk`, or `behavioral` (metadata only; all rules use the same engine).

## generate-tests

Emit JUnit 5 reflection-based stubs from a parity JSON report (human or AI fills TODO assertions):

```bash
java -jar target/parity-verify-0.2.0-SNAPSHOT.jar generate-tests \
  --report metadata/dukesbank-parity-report.json \
  --target-class com.sun.ebank.ejb.account.AccountBean \
  --scope com.sun.ebank.ejb.account.AccountBean \
  --package com.sun.ebank.test.generated \
  --class-name AccountBeanParityTest \
  -o AccountBeanParityTest.java
```

Supports v0.1 structural-only and v0.2 wrapped `{ "structural": { ... } }` reports.

## JSON report

Without `--matrix`, output is the v0.1 structural shape. With `--matrix`, output wraps structural + behavioral:

```json
{
  "tool_version": "0.2.0-SNAPSHOT",
  "structural": {
    "summary": {
      "added_count": 7,
      "removed_count": 14,
      "modified_count": 23,
      "unchanged_count": 670,
      "structural_parity": false
    },
    "changes": []
  },
  "behavioral": {
    "matrix_id": "dukesbank-cmp-jpa",
    "all_passed": true,
    "checks": [
      { "id": "module_quiescent", "kind": "structural", "status": "pass", "message": "..." }
    ]
  }
}
```

Legacy structural-only example (no `--matrix`):

```json
{
  "tool_version": "0.2.0-SNAPSHOT",
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
