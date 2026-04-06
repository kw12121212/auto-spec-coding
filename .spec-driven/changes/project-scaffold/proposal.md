# project-scaffold

## What

Initialize the Maven project with standard directory layout, `pom.xml` with Lealone dependencies, and JUnit 5 test infrastructure. Produce a compilable, testable project skeleton that all subsequent changes build upon.

## Why

This is the foundational change for the entire project. Every milestone (M1–M16) depends on a working Maven build. Without this, no interfaces, tools, or services can be implemented.

## Scope

- Maven project structure (`src/main/java`, `src/test/java`, resource directories)
- `pom.xml` with:
  - JDK 25+ compilation target (VirtualThread support)
  - Lealone core dependencies (`lealone-common`, `lealone-net`)
  - JUnit 5 test dependency
  - Maven build plugins (compiler, surefire)
- Root package declaration (`org.specdriven.agent`)
- Verification that `mvn compile` and `mvn test` pass (empty skeleton)

## Unchanged Behavior

No existing code to preserve — this is the first change. No interfaces, tools, or runtime logic are introduced.
