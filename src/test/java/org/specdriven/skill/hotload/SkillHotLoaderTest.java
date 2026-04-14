package org.specdriven.skill.hotload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;
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
    private static final PermissionContext PERMISSION_CONTEXT =
            new PermissionContext("test-hot-loader", "hot-load", "test-requester");
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
        return new LealoneSkillHotLoader(
                compiler, cacheManager, activationEnabled, allowingProvider(), allowingSourceTrustPolicy());
    }

    private static SkillLoadResult load(
            SkillHotLoader loader,
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash) {
        return loader.load(skillName, entryClassName, javaSource, sourceHash, PERMISSION_CONTEXT);
    }

    private static SkillLoadResult replace(
            SkillHotLoader loader,
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash) {
        return loader.replace(skillName, entryClassName, javaSource, sourceHash, PERMISSION_CONTEXT);
    }

    private static void unload(SkillHotLoader loader, String skillName) {
        loader.unload(skillName, PERMISSION_CONTEXT);
    }

    private static PermissionProvider allowingProvider() {
        return decisionProvider(PermissionDecision.ALLOW);
    }

    private static SkillSourceTrustPolicy allowingSourceTrustPolicy() {
        return (skillName, sourceHash) -> true;
    }

    private static PermissionProvider decisionProvider(PermissionDecision decision) {
        return new PermissionProvider() {
            @Override
            public PermissionDecision check(Permission permission, PermissionContext context) {
                return decision;
            }

            @Override
            public void grant(Permission permission, PermissionContext context) {
            }

            @Override
            public void revoke(Permission permission, PermissionContext context) {
            }
        };
    }

    @Test
    void disabledLoaderRejectsLoadActivation() {
        SkillHotLoader loader = newLoader();

        SkillLoadResult result = load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

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

        SkillLoadResult result = load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

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
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        SkillLoadResult second = load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(second.success());
        assertFalse(second.diagnostics().isEmpty());
        assertEquals(original, loader.activeLoader("hello").orElseThrow(),
                "Original loader must remain unchanged after duplicate load");
    }

    @Test
    void loadWithInvalidSourceReturnsFalseWithDiagnosticsAndDoesNotRegister() {
        SkillHotLoader loader = newLoader(true);

        SkillLoadResult result = load(loader, "bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty());
        assertEquals(Optional.empty(), loader.activeLoader("bad"));
        assertFalse(loader.loadedSkillNames().contains("bad"));
    }

    @Test
    void disabledLoaderRejectsReplaceActivation() {
        SkillHotLoader loader = newLoader();

        SkillLoadResult result = replace(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash2");

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
        SkillHotLoader enabledLoader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), allowingSourceTrustPolicy());
        assertTrue(load(enabledLoader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1").success());

        SkillHotLoader disabledLoader = new LealoneSkillHotLoader(compiler, cacheManager, false);
        SkillLoadResult result = disabledLoader.load("hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(result.success());
        assertTrue(disabledLoader.activeLoader("hello").isEmpty());
    }

    @Test
    void replaceWithValidSourceSwapsActiveLoader() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        String newSource =
                "package demo; public class HelloSkill { public static String run() { return \"v2\"; } }";
        SkillLoadResult result = replace(loader, "hello", VALID_CLASS_NAME, newSource, "hash2");

        assertTrue(result.success());
        ClassLoader updated = loader.activeLoader("hello").orElseThrow();
        assertNotNull(updated);
        assertNotEquals(original, updated, "Active loader must be the new one after replace");
    }

    @Test
    void replaceWithInvalidSourcePreservesExistingActiveLoader() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");
        ClassLoader original = loader.activeLoader("hello").orElseThrow();

        SkillLoadResult result = replace(loader, "hello", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertEquals(original, loader.activeLoader("hello").orElseThrow(),
                "Original loader must survive a failed replace");
    }

    @Test
    void unloadRemovesSkillFromRegistry() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        unload(loader, "hello");

        assertEquals(Optional.empty(), loader.activeLoader("hello"));
        assertFalse(loader.loadedSkillNames().contains("hello"));
    }

    @Test
    void unloadOnAbsentSkillIsNoOp() {
        SkillHotLoader loader = newLoader();

        assertDoesNotThrow(() -> unload(loader, "nonexistent"));
    }

    @Test
    void twoSkillsWithSameEntryClassNameUseIndependentClassLoaders() throws Exception {
        SkillHotLoader loader = newLoader(true);
        String sourceA = "package demo; public class HelloSkill { public static String name() { return \"A\"; } }";
        String sourceB = "package demo; public class HelloSkill { public static String name() { return \"B\"; } }";

        load(loader, "skillA", VALID_CLASS_NAME, sourceA, "hashA");
        load(loader, "skillB", VALID_CLASS_NAME, sourceB, "hashB");

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
        SkillHotLoader loaderA = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), allowingSourceTrustPolicy());
        load(loaderA, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        // Second loader shares the same cache manager — should hit cache without recompiling
        SkillHotLoader loaderB = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), allowingSourceTrustPolicy());
        SkillLoadResult result = load(loaderB, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertTrue(result.success(), "Load must succeed via cache hit");
        assertTrue(loaderB.activeLoader("hello").isPresent());
    }

    @Test
    void loadedSkillNamesIsUnmodifiable() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertThrows(UnsupportedOperationException.class,
                () -> loader.loadedSkillNames().add("injected"));
    }

    @Test
    void compilationExceptionIsWrappedInHotLoaderException() {
        SkillSourceCompiler throwingCompiler = (entryClassName, javaSource, outputDir) -> {
            throw new SkillCompilationException("javac unavailable in test environment");
        };
        ClassCacheManager cacheManager = new LealoneClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                throwingCompiler, cacheManager, true, allowingProvider(), allowingSourceTrustPolicy());

        assertThrows(SkillHotLoaderException.class,
                () -> load(loader, "broken", VALID_CLASS_NAME, VALID_SOURCE, "hash-infra"));
        assertEquals(Optional.empty(), loader.activeLoader("broken"));
    }

    @Test
    void deniedLoadThrowsPermissionExceptionBeforeSideEffects() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, decisionProvider(PermissionDecision.DENY), allowingSourceTrustPolicy());

        SkillHotLoadPermissionException thrown = assertThrows(SkillHotLoadPermissionException.class,
                () -> load(loader, "denied", VALID_CLASS_NAME, VALID_SOURCE, "hash-denied"));

        assertTrue(thrown.getMessage().contains("denied"));
        assertTrue(thrown.getMessage().contains("skill.hotload.load"));
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(0, cacheManager.resolveClassDirCalls);
        assertEquals(0, cacheManager.loadCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("denied"));
        assertFalse(loader.failedSkillNames().contains("denied"));
    }

    @Test
    void confirmationRequiredReplacePreservesActiveLoaderBeforeSideEffects() {
        MutablePermissionProvider permissionProvider = new MutablePermissionProvider(PermissionDecision.ALLOW);
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, permissionProvider, allowingSourceTrustPolicy());
        assertTrue(load(loader, "confirm", VALID_CLASS_NAME, VALID_SOURCE, "hash1").success());
        ClassLoader original = loader.activeLoader("confirm").orElseThrow();

        compiler.compileCalls = 0;
        cacheManager.reset();
        permissionProvider.decision = PermissionDecision.CONFIRM;
        SkillHotLoadPermissionException thrown = assertThrows(SkillHotLoadPermissionException.class,
                () -> replace(loader, "confirm", VALID_CLASS_NAME, VALID_SOURCE, "hash2"));

        assertTrue(thrown.getMessage().contains("skill.hotload.replace"));
        assertTrue(thrown.getMessage().contains("confirmation"));
        assertEquals(original, loader.activeLoader("confirm").orElseThrow());
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(0, cacheManager.resolveClassDirCalls);
        assertEquals(0, cacheManager.loadCachedCalls);
    }

    @Test
    void deniedUnloadPreservesActiveLoader() {
        MutablePermissionProvider permissionProvider = new MutablePermissionProvider(PermissionDecision.ALLOW);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                new LealoneSkillSourceCompiler(), new LealoneClassCacheManager(tempDir),
                true, permissionProvider, allowingSourceTrustPolicy());
        assertTrue(load(loader, "unload-denied", VALID_CLASS_NAME, VALID_SOURCE, "hash1").success());
        ClassLoader original = loader.activeLoader("unload-denied").orElseThrow();

        permissionProvider.decision = PermissionDecision.DENY;
        SkillHotLoadPermissionException thrown = assertThrows(SkillHotLoadPermissionException.class,
                () -> unload(loader, "unload-denied"));

        assertTrue(thrown.getMessage().contains("skill.hotload.unload"));
        assertEquals(original, loader.activeLoader("unload-denied").orElseThrow());
        assertTrue(loader.loadedSkillNames().contains("unload-denied"));
    }

    @Test
    void enabledLoadWithoutPermissionContextFailsClosed() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), allowingSourceTrustPolicy());

        assertThrows(SkillHotLoadPermissionException.class,
                () -> loader.load("missing-context", VALID_CLASS_NAME, VALID_SOURCE, "hash1"));
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("missing-context"));
    }

    @Test
    void enabledLoadWithoutPermissionProviderFailsClosed() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(compiler, cacheManager, true);

        assertThrows(SkillHotLoadPermissionException.class,
                () -> load(loader, "missing-provider", VALID_CLASS_NAME, VALID_SOURCE, "hash1"));
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("missing-provider"));
    }

    @Test
    void untrustedLoadThrowsTrustExceptionBeforeSideEffects() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), (skillName, sourceHash) -> false);

        SkillHotLoadTrustException thrown = assertThrows(SkillHotLoadTrustException.class,
                () -> load(loader, "untrusted", VALID_CLASS_NAME, VALID_SOURCE, "hash-untrusted"));

        assertTrue(thrown.getMessage().contains("untrusted"));
        assertTrue(thrown.getMessage().contains("hash-untrusted"));
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(0, cacheManager.resolveClassDirCalls);
        assertEquals(0, cacheManager.loadCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("untrusted"));
        assertFalse(loader.failedSkillNames().contains("untrusted"));
    }

    @Test
    void untrustedReplacePreservesActiveLoaderBeforeSideEffects() {
        MutableSourceTrustPolicy trustPolicy = new MutableSourceTrustPolicy(true);
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, allowingProvider(), trustPolicy);
        assertTrue(load(loader, "replace-untrusted", VALID_CLASS_NAME, VALID_SOURCE, "hash1").success());
        ClassLoader original = loader.activeLoader("replace-untrusted").orElseThrow();

        compiler.compileCalls = 0;
        cacheManager.reset();
        trustPolicy.trusted = false;
        SkillHotLoadTrustException thrown = assertThrows(SkillHotLoadTrustException.class,
                () -> replace(loader, "replace-untrusted", VALID_CLASS_NAME, VALID_SOURCE, "hash2"));

        assertTrue(thrown.getMessage().contains("replace-untrusted"));
        assertTrue(thrown.getMessage().contains("hash2"));
        assertEquals(original, loader.activeLoader("replace-untrusted").orElseThrow());
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(0, cacheManager.resolveClassDirCalls);
        assertEquals(0, cacheManager.loadCachedCalls);
        assertFalse(loader.failedSkillNames().contains("replace-untrusted"));
    }

    @Test
    void enabledLoadWithoutTrustedSourcePolicyFailsClosed() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        SkillHotLoader loader = new LealoneSkillHotLoader(compiler, cacheManager, true, allowingProvider());

        SkillHotLoadTrustException thrown = assertThrows(SkillHotLoadTrustException.class,
                () -> load(loader, "missing-trust", VALID_CLASS_NAME, VALID_SOURCE, "hash1"));

        assertTrue(thrown.getMessage().contains("missing-trust"));
        assertTrue(thrown.getMessage().contains("hash1"));
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("missing-trust"));
    }

    @Test
    void deniedPermissionDoesNotConsultTrustedSourcePolicy() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingClassCacheManager cacheManager = new RecordingClassCacheManager(tempDir);
        RecordingSourceTrustPolicy trustPolicy = new RecordingSourceTrustPolicy(true);
        SkillHotLoader loader = new LealoneSkillHotLoader(
                compiler, cacheManager, true, decisionProvider(PermissionDecision.DENY), trustPolicy);

        assertThrows(SkillHotLoadPermissionException.class,
                () -> load(loader, "permission-first", VALID_CLASS_NAME, VALID_SOURCE, "hash1"));

        assertEquals(0, trustPolicy.calls);
        assertEquals(0, compiler.compileCalls);
        assertEquals(0, cacheManager.isCachedCalls);
        assertEquals(Optional.empty(), loader.activeLoader("permission-first"));
    }

    @Test
    void failedLoadTrackedInFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);

        SkillLoadResult result = load(loader, "bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertFalse(result.success());
        assertTrue(loader.failedSkillNames().contains("bad"));
        assertFalse(loader.loadedSkillNames().contains("bad"));
    }

    @Test
    void successfulReplaceRemovesFromFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "skill", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");
        assertTrue(loader.failedSkillNames().contains("skill"));

        SkillLoadResult result = replace(loader, "skill", VALID_CLASS_NAME, VALID_SOURCE, "hash-good");

        assertTrue(result.success());
        assertFalse(loader.failedSkillNames().contains("skill"));
    }

    @Test
    void unloadClearsFailedEntry() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "skill", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");
        assertTrue(loader.failedSkillNames().contains("skill"));

        unload(loader, "skill");

        assertFalse(loader.failedSkillNames().contains("skill"));
    }

    @Test
    void failureInSkillADoesNotAffectSkillB() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "skillB", VALID_CLASS_NAME, VALID_SOURCE, "hashB");
        assertTrue(loader.activeLoader("skillB").isPresent());

        load(loader, "skillA", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertTrue(loader.loadedSkillNames().contains("skillB"),
                "skillB must remain in loadedSkillNames after skillA failure");
        assertTrue(loader.activeLoader("skillB").isPresent(),
                "skillB active loader must survive skillA compilation failure");
    }

    @Test
    void failedSkillNamesIsUnmodifiable() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "bad", VALID_CLASS_NAME, INVALID_SOURCE, "hash-bad");

        assertThrows(UnsupportedOperationException.class,
                () -> loader.failedSkillNames().add("injected"));
    }

    @Test
    void duplicateRegistrationDoesNotCorruptFailedSkillNames() {
        SkillHotLoader loader = newLoader(true);
        load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        SkillLoadResult duplicate = load(loader, "hello", VALID_CLASS_NAME, VALID_SOURCE, "hash1");

        assertFalse(duplicate.success());
        assertFalse(loader.failedSkillNames().contains("hello"),
                "Duplicate-registration rejection must not mark a successfully loaded skill as failed");
        assertTrue(loader.activeLoader("hello").isPresent(),
                "Active loader must remain after duplicate registration attempt");
    }

    private static final class MutablePermissionProvider implements PermissionProvider {
        private PermissionDecision decision;

        private MutablePermissionProvider(PermissionDecision decision) {
            this.decision = decision;
        }

        @Override
        public PermissionDecision check(Permission permission, PermissionContext context) {
            return decision;
        }

        @Override
        public void grant(Permission permission, PermissionContext context) {
        }

        @Override
        public void revoke(Permission permission, PermissionContext context) {
        }
    }

    private static final class MutableSourceTrustPolicy implements SkillSourceTrustPolicy {
        private boolean trusted;

        private MutableSourceTrustPolicy(boolean trusted) {
            this.trusted = trusted;
        }

        @Override
        public boolean isTrusted(String skillName, String sourceHash) {
            return trusted;
        }
    }

    private static final class RecordingSourceTrustPolicy implements SkillSourceTrustPolicy {
        private final boolean trusted;
        private int calls;

        private RecordingSourceTrustPolicy(boolean trusted) {
            this.trusted = trusted;
        }

        @Override
        public boolean isTrusted(String skillName, String sourceHash) {
            calls++;
            return trusted;
        }
    }

    private static final class RecordingCompiler implements SkillSourceCompiler {
        private int compileCalls;

        @Override
        public org.specdriven.skill.compiler.SkillCompilationResult compile(
                String entryClassName,
                String javaSource,
                Path outputDir) {
            compileCalls++;
            return new org.specdriven.skill.compiler.SkillCompilationResult(true, entryClassName, java.util.List.of());
        }
    }

    private static final class RecordingClassCacheManager implements ClassCacheManager {
        private final Path classDir;
        private final ClassLoader classLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
        };
        private int isCachedCalls;
        private int resolveClassDirCalls;
        private int loadCachedCalls;

        private RecordingClassCacheManager(Path classDir) {
            this.classDir = classDir;
        }

        @Override
        public boolean isCached(String skillName, String sourceHash) {
            isCachedCalls++;
            return true;
        }

        @Override
        public Path resolveClassDir(String skillName, String sourceHash) {
            resolveClassDirCalls++;
            return classDir;
        }

        @Override
        public ClassLoader loadCached(String skillName, String sourceHash) {
            loadCachedCalls++;
            return classLoader;
        }

        @Override
        public void invalidate(String skillName, String sourceHash) {
        }

        private void reset() {
            isCachedCalls = 0;
            resolveClassDirCalls = 0;
            loadCachedCalls = 0;
        }
    }
}
