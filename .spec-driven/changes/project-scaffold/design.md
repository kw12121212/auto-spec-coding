# Design: project-scaffold

## Approach

Create a standard single-module Maven project. Integrate Lealone as a **git submodule** pointing to `https://github.com/lealone/Lealone.git`, tracking the master branch (`8.0.0-SNAPSHOT`). Lealone is first built and installed to the local Maven repo, then our project references its modules as normal dependencies.

### Directory Layout

```
auto-spec-coding/
├── pom.xml
├── lealone/                    <-- git submodule (Lealone source)
│   ├── lealone-common/
│   ├── lealone-net/
│   └── ... (other Lealone modules)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/specdriven/agent/  (root package, empty for now)
│   │   └── resources/
│   └── test/
│       ├── java/
│       │   └── org/specdriven/agent/
│       └── resources/
└── .spec-driven/
```

## Key Decisions

1. **JDK 25+ target** — Required for VirtualThread support as noted in M1 dependencies. The compiler source/target will be set accordingly. Lealone targets JDK 21, which is compatible.
2. **Single-module Maven** — Start as a single module. Multi-module restructuring can happen later if warranted by M12–M14 interface layers.
3. **Lealone via git submodule** — Clone the latest Lealone source directly rather than using Maven Central artifacts. This gives access to the newest features (e.g. `lealone-agent`, `lealone-http`) and allows local patches if needed. Build flow: `cd lealone && mvn install -DskipTests` first, then build our project.
4. **Root package `org.specdriven.agent`** — Aligns with the project name (spec-driven coding agent). Can be revisited if needed.

## Alternatives Considered

- **Gradle** — Config calls for Maven explicitly; Gradle would add unnecessary deviation.
- **Multi-module from day one** — Premature; only warranted when SDK/RPC/HTTP layers diverge in dependencies.
- **Lealone from Maven Central** — User explicitly prefers git clone for latest version access.
