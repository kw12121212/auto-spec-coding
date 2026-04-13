package org.specdriven.skill.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSourceCompilerTest {

    @TempDir
    Path tempDir;

    @Test
    void successfulCompilationWritesClassOutput() throws Exception {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        Path outputDir = tempDir.resolve("compiled-output");

        SkillCompilationResult result = compiler.compile(
                "demo.HelloSkill",
                "package demo; public class HelloSkill { public static String run() { return \"ok\"; } }",
                outputDir);

        assertTrue(result.success());
        assertEquals("demo.HelloSkill", result.entryClassName());
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(Files.isRegularFile(outputDir.resolve("demo/HelloSkill.class")));
    }

    @Test
    void createsOutputDirectoryWhenMissing() {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        Path outputDir = tempDir.resolve("missing").resolve("nested");

        SkillCompilationResult result = compiler.compile(
                "demo.CreateDirSkill",
                "package demo; public class CreateDirSkill { }",
                outputDir);

        assertTrue(result.success());
        assertTrue(Files.isDirectory(outputDir));
    }

    @Test
    void invalidSourceReturnsDiagnostics() {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();

        SkillCompilationResult result = compiler.compile(
                "demo.BadSkill",
                "package demo; public class BadSkill { syntax error }",
                tempDir.resolve("invalid"));

        assertFalse(result.success());
        assertEquals("demo.BadSkill", result.entryClassName());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().get(0).message().contains("expected")
                || result.diagnostics().get(0).message().contains("error"));
        assertNotEquals(0L, result.diagnostics().get(0).lineNumber());
    }

    @Test
    void unavailableCompilerCapabilityFailsFast() {
        LealoneSkillSourceCompiler compiler = new LealoneSkillSourceCompiler((entryClassName, javaSource) -> {
            throw new RuntimeException("JDK Java compiler not available");
        });

        SkillCompilationException exception = assertThrows(SkillCompilationException.class,
                () -> compiler.compile("demo.MissingCompiler", "package demo; public class MissingCompiler {}", tempDir));

        assertTrue(exception.getMessage().contains("compiler capability is unavailable"));
    }

    @Test
    void diagnosticsListIsUnmodifiable() {
        SkillCompilationResult result = new SkillCompilationResult(true, "demo.Test", java.util.List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> result.diagnostics().add(new SkillCompilationDiagnostic("x", 1, 1)));
    }
}
