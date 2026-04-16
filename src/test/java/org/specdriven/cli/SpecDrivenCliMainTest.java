package org.specdriven.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.json.JsonReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpecDrivenCliMainTest {

    @TempDir
    Path tempDir;

    @Test
    void printsUsageForUnsupportedInvocation() {
        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir);

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Usage: spec-driven <command> [args]"));
        assertTrue(result.stderr().contains("Commands: propose, modify, apply, verify, verify-roadmap, roadmap-status, archive, cancel, init, run-maintenance, migrate, service-runtime, list"));
    }

    @Test
    void proposeScaffoldsChangeArtifacts() throws Exception {
        assertEquals(0, SpecDrivenCliMain.runForTest(tempDir, "init").exitCode());

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "propose", "demo-change");

        assertEquals(0, result.exitCode());
        Path changeDir = tempDir.resolve(".spec-driven/changes/demo-change");
        assertTrue(Files.isDirectory(changeDir));
        assertTrue(Files.exists(changeDir.resolve("proposal.md")));
        assertTrue(Files.exists(changeDir.resolve("design.md")));
        assertTrue(Files.exists(changeDir.resolve("tasks.md")));
        assertTrue(Files.exists(changeDir.resolve("questions.md")));
        assertTrue(Files.isDirectory(changeDir.resolve("specs")));
    }

    @Test
    void listShowsActiveAndArchivedChangesSeparately() throws Exception {
        assertEquals(0, SpecDrivenCliMain.runForTest(tempDir, "init").exitCode());
        assertEquals(0, SpecDrivenCliMain.runForTest(tempDir, "propose", "active-change").exitCode());
        Files.createDirectories(tempDir.resolve(".spec-driven/changes/archive/2026-04-09-old-change"));

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "list");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Active:"));
        assertTrue(result.stdout().contains("active-change"));
        assertTrue(result.stdout().contains("Archived:"));
        assertTrue(result.stdout().contains("2026-04-09-old-change"));
    }

    @Test
    void verifyRoadmapReturnsMachineReadableJson() throws Exception {
        setupValidRoadmap(tempDir, false);

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "verify-roadmap");

        assertEquals(0, result.exitCode(), result.stdout());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals(Boolean.TRUE, json.get("valid"));
        List<Object> milestones = JsonReader.getList(json, "milestones");
        assertEquals(1, milestones.size());
        assertEquals("m99-test.md", JsonReader.getString(json, "milestones.0.file"));
        assertEquals(1L, JsonReader.getLong(json, "milestones.0.doneCriteria"));
        assertEquals(1L, JsonReader.getLong(json, "milestones.0.plannedChanges"));
        assertEquals("proposed", JsonReader.getString(json, "milestones.0.status"));
    }

    @Test
    void roadmapStatusReportsDerivedStatuses() throws Exception {
        setupValidRoadmap(tempDir, true);

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "roadmap-status");

        assertEquals(0, result.exitCode(), result.stdout());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals(Boolean.TRUE, json.get("valid"));
        assertEquals("proposed", JsonReader.getString(json, "milestones.0.declaredStatus"));
        assertEquals("active", JsonReader.getString(json, "milestones.0.derivedStatus"));
        assertEquals("active", JsonReader.getString(json, "milestones.0.plannedChanges.0.state"));
        assertEquals("planned", JsonReader.getString(json, "milestones.0.plannedChanges.0.derivedStatus"));
    }

    @Test
    void verifyFailsWhenRequiredArtifactsAreMissing() throws Exception {
        Files.createDirectories(tempDir.resolve(".spec-driven/changes/bad-change/specs"));
        Files.writeString(tempDir.resolve(".spec-driven/changes/bad-change/proposal.md"), "# bad-change\n", StandardCharsets.UTF_8);

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "verify", "bad-change");

        assertEquals(1, result.exitCode());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals(Boolean.FALSE, json.get("valid"));
        List<Object> errors = JsonReader.getList(json, "errors");
        assertFalse(errors.isEmpty());
    }

    @Test
    void serviceRuntimeRequiresServicesSqlArgument() {
        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "service-runtime", "--exit-after-start");

        assertEquals(1, result.exitCode());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals("failed", JsonReader.getString(json, "status"));
        assertEquals("invalid_config", JsonReader.getString(json, "error"));
        assertTrue(JsonReader.getString(json, "message").contains("--services-sql"));
    }

    @Test
    void serviceRuntimeMissingServicesSqlReturnsStructuredFailure() {
        Path missing = tempDir.resolve("services.sql");

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir,
                "service-runtime",
                "--services-sql", missing.toString(),
                "--port", "0",
                "--jdbc-url", isolatedJdbcUrl(),
                "--exit-after-start");

        assertEquals(1, result.exitCode());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals("failed", JsonReader.getString(json, "status"));
        assertEquals("missing_input", JsonReader.getString(json, "error"));
        assertTrue(JsonReader.getString(json, "message").contains("services.sql"));
    }

    @Test
    void serviceRuntimeUnsupportedBootstrapInputReturnsStructuredFailure() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, "DROP TABLE unsupported;\n", StandardCharsets.UTF_8);

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir,
                "service-runtime",
                "--services-sql", servicesSql.toString(),
                "--port", "0",
                "--jdbc-url", isolatedJdbcUrl(),
                "--exit-after-start");

        assertEquals(1, result.exitCode());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals("failed", JsonReader.getString(json, "status"));
        assertEquals("bootstrap_error", JsonReader.getString(json, "error"));
        assertTrue(JsonReader.getString(json, "message").contains("Unsupported services.sql statement"));
    }

    @Test
    void serviceRuntimeExitAfterStartReturnsStructuredSuccess() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, "\n", StandardCharsets.UTF_8);

        SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir,
                "service-runtime",
                "--services-sql", servicesSql.toString(),
                "--host", "127.0.0.1",
                "--port", "0",
                "--jdbc-url", isolatedJdbcUrl(),
                "--exit-after-start");

        assertEquals(0, result.exitCode(), result.stdout());
        Map<String, Object> json = JsonReader.parseObject(result.stdout());
        assertEquals("started", JsonReader.getString(json, "status"));
        assertEquals(servicesSql.toAbsolutePath().normalize().toString(), JsonReader.getString(json, "servicesSql"));
        assertEquals("127.0.0.1", JsonReader.getString(json, "host"));
        assertTrue(JsonReader.getLong(json, "port") > 0);
        assertTrue(JsonReader.getString(json, "serviceBaseUrl").contains("/services"));
        assertTrue(JsonReader.getString(json, "healthUrl").contains("/api/v1/health"));
    }

    @Test
    void migrateInstallsNodeFreeWrapperIntoBundledSkills() throws Exception {
        Path bundledSkillsDir = tempDir.resolve("bundled-skills");
        Path skillDir = bundledSkillsDir.resolve("spec-driven-auto");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                # skill

                Run:
                `node {{SKILL_DIR}}/scripts/spec-driven.js apply demo`
                """, StandardCharsets.UTF_8);

        Files.createDirectories(tempDir.resolve(".claude/skills/openspec-old"));
        Files.createDirectories(tempDir.resolve(".claude/commands/openspec-old"));

        String previous = System.getProperty("specdriven.skills.dir");
        System.setProperty("specdriven.skills.dir", bundledSkillsDir.toString());
        try {
            SpecDrivenCliMain.Result result = SpecDrivenCliMain.runForTest(tempDir, "migrate");

            assertEquals(0, result.exitCode(), result.stderr());
            Path installedSkill = tempDir.resolve(".claude/skills/spec-driven-auto");
            assertTrue(Files.isDirectory(installedSkill));
            String skillContent = Files.readString(installedSkill.resolve("SKILL.md"), StandardCharsets.UTF_8);
            assertFalse(skillContent.contains("node "));
            assertTrue(skillContent.contains("/scripts/spec-driven apply demo"));
            Path wrapper = installedSkill.resolve("scripts/spec-driven");
            assertTrue(Files.isRegularFile(wrapper));
            String wrapperContent = Files.readString(wrapper, StandardCharsets.UTF_8);
            assertTrue(wrapperContent.contains("SPEC_DRIVEN_CLI_CLASSPATH"));
            assertTrue(wrapperContent.contains("org.specdriven.cli.SpecDrivenCliMain"));
        } finally {
            if (previous == null) {
                System.clearProperty("specdriven.skills.dir");
            } else {
                System.setProperty("specdriven.skills.dir", previous);
            }
        }
    }

    private void setupValidRoadmap(Path root, boolean createActiveChange) throws Exception {
        assertEquals(0, SpecDrivenCliMain.runForTest(root, "init").exitCode());
        if (createActiveChange) {
            Files.createDirectories(root.resolve(".spec-driven/changes/test-change"));
            Files.writeString(root.resolve(".spec-driven/changes/test-change/tasks.md"), "# Tasks: test-change\n\n## Implementation\n\n- [ ] pending\n", StandardCharsets.UTF_8);
        }
        String milestone = """
                # M99 - Test Milestone

                ## Goal

                Test goal

                ## In Scope

                - One thing

                ## Out of Scope

                - Another thing

                ## Done Criteria

                - One criterion

                ## Planned Changes

                - `test-change` - Declared: planned - Test summary

                ## Dependencies

                - None

                ## Risks

                - Minimal

                ## Status

                - Declared: proposed

                ## Notes

                - Note
                """;
        Files.writeString(root.resolve(".spec-driven/roadmap/milestones/m99-test.md"), milestone, StandardCharsets.UTF_8);
        String index = """
                # Roadmap Index

                ## Milestones
                - [m99-test.md](milestones/m99-test.md) - M99 - Test Milestone - proposed
                """;
        Files.writeString(root.resolve(".spec-driven/roadmap/INDEX.md"), index, StandardCharsets.UTF_8);
    }

    private String isolatedJdbcUrl() {
        return "jdbc:lealone:embed:runtime_" + Long.toHexString(System.nanoTime());
    }
}
