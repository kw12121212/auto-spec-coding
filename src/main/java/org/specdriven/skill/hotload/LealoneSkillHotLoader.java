package org.specdriven.skill.hotload;

import org.specdriven.skill.compiler.ClassCacheException;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.SkillCompilationDiagnostic;
import org.specdriven.skill.compiler.SkillCompilationException;
import org.specdriven.skill.compiler.SkillCompilationResult;
import org.specdriven.skill.compiler.SkillSourceCompiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LealoneSkillHotLoader implements SkillHotLoader {

    private final SkillSourceCompiler compiler;
    private final ClassCacheManager cacheManager;
    private final ConcurrentHashMap<String, ActiveEntry> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkillLoadResult> failedRegistry = new ConcurrentHashMap<>();

    public LealoneSkillHotLoader(SkillSourceCompiler compiler, ClassCacheManager cacheManager) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
    }

    @Override
    public SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");

        if (registry.containsKey(skillName)) {
            return new SkillLoadResult(false, entryClassName,
                    List.of(new SkillCompilationDiagnostic(
                            "Skill '" + skillName + "' is already registered; use replace() to update it", -1, -1)));
        }

        LoadOutcome outcome = resolveLoader(skillName, entryClassName, javaSource, sourceHash);
        if (!outcome.success()) {
            SkillLoadResult failure = new SkillLoadResult(false, entryClassName, outcome.diagnostics());
            failedRegistry.put(skillName, failure);
            return failure;
        }

        registry.put(skillName, new ActiveEntry(outcome.classLoader(), entryClassName, sourceHash));
        failedRegistry.remove(skillName);
        return new SkillLoadResult(true, entryClassName, List.of());
    }

    @Override
    public SkillLoadResult replace(String skillName, String entryClassName, String javaSource, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");

        LoadOutcome outcome = resolveLoader(skillName, entryClassName, javaSource, sourceHash);
        if (!outcome.success()) {
            SkillLoadResult failure = new SkillLoadResult(false, entryClassName, outcome.diagnostics());
            failedRegistry.put(skillName, failure);
            return failure;
        }

        registry.put(skillName, new ActiveEntry(outcome.classLoader(), entryClassName, sourceHash));
        failedRegistry.remove(skillName);
        return new SkillLoadResult(true, entryClassName, List.of());
    }

    @Override
    public void unload(String skillName) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        registry.remove(skillName);
        failedRegistry.remove(skillName);
    }

    @Override
    public Optional<ClassLoader> activeLoader(String skillName) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        ActiveEntry entry = registry.get(skillName);
        return entry == null ? Optional.empty() : Optional.of(entry.classLoader());
    }

    @Override
    public Set<String> loadedSkillNames() {
        return Set.copyOf(registry.keySet());
    }

    @Override
    public Set<String> failedSkillNames() {
        return Set.copyOf(failedRegistry.keySet());
    }

    /**
     * Cache-first: returns a live ClassLoader on cache hit or successful compilation,
     * or a failure outcome with diagnostics. Never mutates the registry.
     * Throws {@link SkillHotLoaderException} for infrastructure failures (ClassLoader
     * construction failures, cache directory failures).
     */
    private LoadOutcome resolveLoader(String skillName, String entryClassName, String javaSource, String sourceHash) {
        try {
            if (cacheManager.isCached(skillName, sourceHash)) {
                ClassLoader loader = cacheManager.loadCached(skillName, sourceHash);
                return LoadOutcome.success(loader);
            }
            Path classDir = cacheManager.resolveClassDir(skillName, sourceHash);
            SkillCompilationResult result = compiler.compile(entryClassName, javaSource, classDir);
            if (!result.success()) {
                return LoadOutcome.failure(result.diagnostics());
            }
            ClassLoader loader = cacheManager.loadCached(skillName, sourceHash);
            return LoadOutcome.success(loader);
        } catch (SkillCompilationException e) {
            throw new SkillHotLoaderException(
                    "Compiler infrastructure failure for skill '" + skillName + "'", e);
        } catch (ClassCacheException e) {
            throw new SkillHotLoaderException(
                    "ClassLoader construction failed for skill '" + skillName + "'", e);
        }
    }

    private record ActiveEntry(ClassLoader classLoader, String entryClassName, String sourceHash) {
    }

    private record LoadOutcome(boolean success, ClassLoader classLoader, List<SkillCompilationDiagnostic> diagnostics) {

        static LoadOutcome success(ClassLoader classLoader) {
            return new LoadOutcome(true, classLoader, List.of());
        }

        static LoadOutcome failure(List<SkillCompilationDiagnostic> diagnostics) {
            return new LoadOutcome(false, null, diagnostics);
        }
    }
}
