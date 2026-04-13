# Design: skill-source-compiler

## Approach

Introduce a new `org.specdriven.skill.compiler` package with a small, explicit contract around compilation only:

- `SkillSourceCompiler` for the compile entry point
- `SkillCompilationResult` for success/failure plus diagnostics
- `SkillCompilationDiagnostic` for compiler messages bound to source locations
- `SkillCompilationException` for infrastructure failures where a normal compilation result cannot be produced
- `LealoneSkillSourceCompiler` as the Lealone-backed implementation

The compiler contract should accept a caller-provided entry class name, source string, and output directory. Successful compilation writes `.class` output into that directory and returns a successful result. Invalid Java source returns a failed result with diagnostics. Missing runtime compiler capability or output-directory preparation failure throws `SkillCompilationException`.

This keeps class-output ownership with the caller so the later cache manager can decide where compiled artifacts live without forcing cache semantics into the first compiler change.

## Key Decisions

- Separate invalid-source failures from infrastructure failures. User source errors belong in `SkillCompilationResult.diagnostics`, while unavailable compiler capability or unusable output directories throw `SkillCompilationException`.
- Keep compilation limited to source string -> class output. Registration, activation, and class loading stay out of scope so the first M30 change remains a foundation instead of a partial hot-load system.
- Make the output directory caller-controlled. This avoids baking cache layout into the compiler contract before `class-cache-manager` is proposed and implemented.
- Treat Lealone dependency verification as a task, not as a built-in scope expansion. If verification shows the current snapshot is insufficient, the implementation should stop and request scope adjustment instead of silently growing the change.

## Alternatives Considered

- Return loaded `Class<?>` instances directly from the first compiler change. Rejected because it would couple compilation to class-loading lifecycle and overlap with `skill-hot-loader`.
- Bundle cache persistence into the compiler contract. Rejected because M30 already has a separate `class-cache-manager` planned change for that responsibility.
- Expand this proposal to include Lealone dependency upgrade work up front. Rejected because the current recommendation is to keep the change focused on the project-side contract and verify dependency capability first.
