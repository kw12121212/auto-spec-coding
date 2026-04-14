package org.specdriven.skill.hotload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.LealoneClassCacheManager;
import org.specdriven.skill.compiler.LealoneSkillSourceCompiler;
import org.specdriven.skill.compiler.SkillCompilationException;
import org.specdriven.skill.compiler.SkillSourceCompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillHotLoaderTest {

    private static final String VALID_CLASS_NAME = "demo.HelloSkill";
    private static final String VALID_SOURCE =
            "package demo; public class HelloSkill { public static String run() { return \"ok\"; } }";
    private static final String INVALID_SOURCE =
            "package demo; public class HelloSkill { this is not valid java }";

    @TempDir
    Path tempDir;

    private SkillHotLoader newLoader() {
        return newLoader(false);
    }

    private SkillHotLoader newLoader(boolean activationEnabled) {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        ClassCacheManager cacheManager = new LealoneClassCacheManager(tempDir);
        return new LealoneSkillHotLoader(compiler, cacheManager, activationEnabled);
    }

    @Test
    void disabledLoaderRejectsLoadActivation() {
        SkillHotLoader loader = newLoader();

        SkillLoadResult result = loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(loader.isActivationEnabled());
        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().getFirst().message().contains("disabled"));
        assertTrue(loader.activeLoader("hello").isEmpty());
        assertFalse(loader.loadedSkillNames().contains("hello"));
        assertTrue(loader.failedSkillNames().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("hello").resolve("hash1")));
    }

    @Test
    void loadSucceedsAndRegistersActiveLoaderWhenEnabled() {
        SkillHotLoader loader = newLoader(true);

        SkillLoadResult result = loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertTrue(loader.isActivationEnabled());
        assertTrue(result.success());
        assertEquals(VALID_CLASS_NAME, result.entryClassName());
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(loader.activeLoader("hello").isPresent());
        assertTrue(loader.loadedSkillNames().contains("hello"));
    }

    @Test
    void loadRejectsDuplicateRegistration() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        SkillLoadResult second = loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(second.success());
        assertFalse(second.diagnostics().isEmpty());
        assertEquals(original, loader.activeLoader("hello").orElseThrow(),
                "Original loader must remain unchanged after duplicate load");
    }

    @Test
    void loadWithInvalidSourceReturnsFalseWithDiagnosticsAndDoesNotRegister() {
        SkillHotLoader loader = newLoader(true);

        SkillLoadResult result = loader.load("bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty());
        assertEquals(Optional.empty(), loader.activeLoader("bad"));
        assertFalse(loader.loadedSkillNames().contains("bad"));
    }

    @Test
    void disabledLoaderRejectsReplaceActivation() {
        SkillHotLoader loader = newLoader();

        SkillLoadResult result = loader.replace("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash2");

        assertFalse(result.success());
        assertTrue(result.diagnostics().getFirst().message().contains("disabled"));
        assertEquals(Optional.empty(), loader.activeLoader("hello"));
        assertTrue(loader.failedSkillNames().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("hello").resolve("hash2")));
    }

    @Test
    void disabledLoaderDoesNotActivateExistingCachedClasses() {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        ClassCacheManager cacheManager = new LealoneClassCacheManager(tempDir);
        SkillHotLoader enabledLoader = new LealoneSkillHotLoader(compiler, cacheManager, true);
        assertTrue(enabledLoader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1").success());

        SkillHotLoader disabledLoader = new LealoneSkillHotLoader(compiler, cacheManager, false);
        SkillLoadResult result = disabledLoader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(result.success());
        assertTrue(disabledLoader.activeLoader("hello").isEmpty());
    }

    @Test
    void replaceWithValidSourceSwapsActiveLoader() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        String newSource =
                "package demo; public class HelloSkill { public static String run() { return \"v2\"; } }";
        SkillLoadResult result = loader.replace("hello", VALID_CLASS_NAME, newSource, "hash2");

        assertTrue(result.success());
        ClassLoader updated = loader.activeLoader("hello").orElseThrow();
        assertNotNull(updated);
        assertNotEquals(original, updated, "Active loader must be the new one after replace");
    }

    @Test
    void replaceWithInvalidSourcePreservesExistingActiveLoader() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        SkillLoadResult result = loader.replace("hello", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertEquals(original, loader.activeLoader("hello").orElseThrow(),
                "Original loader must survive a failed replace");
    }

    @Test
    void unloadRemovesSkillFromRegistry() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        loader.unload("hello");

        assertEquals(Optional.empty(), loader.activeLoader("hello"));
        assertFalse(loader.loadedSkillNames().contains("hello"));
    }

    @Test
    void unloadOnAbsentSkillIsNoOp() {
        SkillHotLoader loader = newLoader();

        assertDoesNotThrow(() -> loader.unload("nonexistent"));
    }

    @Test
    void twoSkillsWithSameEntryClassNameUseIndependentClassLoaders() throws Exception {
        SkillHotLoader loader = newLoader(true);
        String sourceA = "package demo; public class HelloSkill { public static String name() { return \"A\"; } }";
        String sourceB = "package demo; public class HelloSkill { public static String name() { return \"B\"; } }";

        loader.load("skillA", VALID_CLASS_NAME, sourceA, "hashA");
        loader.load("skillB", VALID_CLASS_NAME, sourceB, "hashB");

        ClassLoader loaderA = loader.activeLoader("skillA").orElseThrow();
        ClassLoader loaderB = loader.activeLoader("skillB").orElseThrow();
        assertNotEquals(loaderA, loaderB, "Skills must use independent ClassLoader instances");

        Class<?> classA = loaderA.loadClass(VALID_CLASS_NAME);
        Class<?> classB = loaderB.loadClass(VALID_CLASS_NAME);
        assertNotEquals(classA, classB, "Loaded Class instances must differ across isolated loaders");
    }

    @Test
    void cacheHitOnLoadSkipsCompilationAndReturnsSuccess() {
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        ClassCacheManager cacheManager = new LealoneClassCacheManager(tempDir);

        // Prime the cache by loading once
        SkillHotLoader loaderA = new LealoneSkillHotLoader(compiler, cacheManager, true);
        loaderA.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        // Second loader shares the same cache manager — should hit cache without recompiling
        SkillHotLoader loaderB = new LealoneSkillHotLoader(compiler, cacheManager, true);
        SkillLoadResult result = loaderB.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertTrue(result.success(), "Load must succeed via cache hit");
        assertTrue(loaderB.activeLoader("hello").isPresent());
    }

    @Test
    void loadedSkillNamesIsUnmodifiable() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertThrows(UnsupportedOperationException.class,
                () -> loader.loadedSkillNames().add("injected"));
    }

    @Test
    void compilationExceptionIsWrappedInHotLoaderException() {
        SkillSourceCompiler throwingCompiler = (entryClassName, javaSource, outputDir) -> {
            throw new SkillCompilationException("javac unavailable in test environment");
        };
        ClassCacheManager cacheManager = new LealoneClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(throwingCompiler, cacheManager, true);

        assertThrows(SkillHotLoaderException.class,
                () -> loader.load("broken", VALID_CLASS_NAME, VALID_SOURCE, "hash-infra"));
        assertEquals(Optional.empty(), loader.activeLoader("broken"));
    }

    @Test
    void failedLoadTrackedInFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);

        SkillLoadResult result = loader.load("bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertTrue(loader.failedSkillNames().contains("bad"));
        assertFalse(loader.loadedSkillNames().contains("bad"));
    }

    @Test
    void successfulReplaceRemovesFromFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);
        loader.load("skill", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");
        assertTrue(loader.failedSkillNames().contains("skill"));

        SkillLoadResult result = loader.replace("skill", VALID_CLASS_NAME, VALID_SOURCE, "hash-good");

        assertTrue(result.success());
        assertFalse(loader.failedSkillNames().contains("skill"));
    }

    @Test
    void unloadClearsFailedEntry() {
        SkillHotLoader loader = newLoader(true);
        loader.load("skill", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");
        assertTrue(loader.failedSkillNames().contains("skill"));

        loader.unload("skill");

        assertFalse(loader.failedSkillNames().contains("skill"));
    }

    @Test
    void failureInSkillADoesNotAffectSkillB() {
        SkillHotLoader loader = newLoader(true);
        loader.load("skillB", VALID_CLASS_NAME, VALID_SOURCE, "hashB");
        assertTrue(loader.activeLoader("skillB").isPresent());

        loader.load("skillA", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertTrue(loader.loadedSkillNames().contains("skillB"),
                "skillB must remain in loadedSkillNames after skillA failure");
        assertTrue(loader.activeLoader("skillB").isPresent(),
                "skillB active loader must survive skillA compilation failure");
    }

    @Test
    void failedSkillNamesIsUnmodifiable() {
        SkillHotLoader loader = newLoader(true);
        loader.load("bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertThrows(UnsupportedOperationException.class,
                () -> loader.failedSkillNames().add("injected"));
    }

    @Test
    void duplicateRegistrationDoesNotCorruptFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);
        loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        SkillLoadResult duplicate = loader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(duplicate.success());
        assertFalse(loader.failedSkillNames().contains("hello"),
                "Duplicate-registration rejection must not mark a successfully loaded skill as failed");
        assertTrue(loader.activeLoader("hello").isPresent(),
                "Active loader must remain after duplicate registration attempt");
    }
}
