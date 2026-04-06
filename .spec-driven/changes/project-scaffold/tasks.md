# Tasks: project-scaffold

## Implementation

- [x] Add Lealone as git submodule at `lealone/` from `https://github.com/lealone/Lealone.git`
- [x] Build and install Lealone to local Maven repo (`cd lealone && mvn install -DskipTests`)
- [x] Create `pom.xml` with groupId `org.specdriven.agent`, artifactId `auto-spec-coding`, JDK 25 compiler target
- [x] Add Lealone dependencies (`lealone-common`, `lealone-net`) referencing `8.0.0-SNAPSHOT` from local install
- [x] Add JUnit 5 dependency and Maven Surefire plugin
- [x] Create standard Maven directory structure (`src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`)
- [x] Create root package directory `org/specdriven/agent/` under both `src/main/java` and `src/test/java`

## Testing

- [x] Verify `mvn compile` succeeds on empty project
- [x] Verify `mvn test` succeeds (no tests yet, but framework is wired)
- [x] Verify `mvn dependency:resolve` resolves Lealone and JUnit 5 jars

## Verification

- [x] Confirm directory structure matches design layout
- [x] Confirm `pom.xml` contains all declared dependencies and plugins
- [x] Confirm no compile or test errors
