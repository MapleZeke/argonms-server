# Java Migration Guide (OpenRewrite)

This project uses the [OpenRewrite](https://docs.openrewrite.org/) Maven plugin to apply
automated Java migration recipes. The migrations must be run **sequentially**, one step at
a time, so that each recipe operates on code that already targets the previous Java version.

## Prerequisites

- Maven 3.6+ installed
- The **target** JDK version must be installed before running each step. For example,
  you need JDK 11 already installed and active (`JAVA_HOME` pointing to it) before
  running the "Java 8 → 11" migration command. The recipe uses the running JDK to
  analyse and rewrite sources, so it must match (or exceed) the target version.

## Sequential migration commands

Run these commands **in order**. After each step, review the diff, test your build, and
commit the changes before proceeding to the next step.

### Step 1 — Java 6 → 7

```bash
mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava7
```

### Step 2 — Java 7 → 8

```bash
mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava8
```

### Step 3 — Java 8 → 11

```bash
mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava11
```

### Step 4 — Java 11 → 17

```bash
mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava17
```

### Step 5 — Java 17 → 21

```bash
mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava21
```

## Dry-run (preview changes without applying them)

Replace `rewrite:run` with `rewrite:dryRun` in any command above to see what changes
would be made without touching source files:

```bash
mvn rewrite:dryRun -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava17
```

## Notes

- After the recipe rewrites the source files (and before you commit), update
  `maven.compiler.source` and `maven.compiler.target` in `pom.xml` to match the new
  target Java version. Update these properties **after** running `rewrite:run` but
  **before** committing, so the next build compiles against the correct version.
- The `rewrite-migrate-java` dependency is pre-configured in the `rewrite-maven-plugin`
  block in `pom.xml`, so no extra setup is required.
- Generated OpenRewrite data tables are written to `target/rewrite/` and are excluded from
  version control via `.gitignore`.
