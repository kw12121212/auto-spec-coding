# skill-instructions-store

## What

Define a `SkillInstructionStore` interface and `FileSystemInstructionStore` implementation that support 3-level progressive loading of skill content: (1) metadata always in context via SQL PARAMETERS, (2) instruction body loaded lazily on trigger, (3) script resources loaded on demand. Extend `SkillSqlConverter` to embed a `skill_dir` path parameter instead of inlining the full instruction body.

## Why

The current `SkillSqlConverter` embeds instruction bodies inline in `PARAMETERS 'instructions' '...'`. Large bodies risk hitting SQL parser limits (identified as an M11 risk), and all content is loaded regardless of whether the skill is ever invoked. `skill-executor-plugin` needs a lazy-load contract to bind tools and run the agent loop without forcing full content into memory upfront.

## Scope

**In scope:**
- `SkillInstructionStore` interface: `loadInstructions(skillId, skillDir)` → String, `loadResource(skillId, skillDir, relativePath)` → String
- `FileSystemInstructionStore` implementation: reads SKILL.md body and sibling resource files from disk
- `SkillInstructionStoreException` checked-path exception
- Modify `SkillSqlConverter` to emit `'skill_dir' '<absolute_path>'` parameter and omit inline `'instructions'`
- Update `SkillAutoDiscovery` to pass the skill directory path to the converter
- Unit tests for store and updated converter behavior

**Out of scope:**
- Database-backed or in-memory instruction store
- Caching / TTL for loaded content
- `skill-executor-plugin` (consumes this store — separate change)
- `skill-cli-java`

## Unchanged Behavior

- `SkillMarkdownParser.parse()` contract must not change
- `SkillAutoDiscovery.discoverAndRegister()` must continue registering the same set of skills
- Existing SQL PARAMETERS fields (`skill_id`, `type`, `version`, `author`) must remain present and unchanged
