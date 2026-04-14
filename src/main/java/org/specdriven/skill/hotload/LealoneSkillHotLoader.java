package org.specdriven.skill.hotload;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;
import org.specdriven.skill.compiler.ClassCacheException;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.SkillCompilationDiagnostic;
import org.specdriven.skill.compiler.SkillCompilationException;
import org.specdriven.skill.compiler.SkillCompilationResult;
import org.specdriven.skill.compiler.SkillSourceCompiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LealoneSkillHotLoader implements SkillHotLoader {

    private static final String HOT_LOADING_DISABLED_MESSAGE =
            "Hot-loading is disabled; explicit programmatic enablement is required";
    private static final String ACTION_LOAD = "skill.hotload.load";
    private static final String ACTION_REPLACE = "skill.hotload.replace";
    private static final String ACTION_UNLOAD = "skill.hotload.unload";

    private final SkillSourceCompiler compiler;
    private final ClassCacheManager cacheManager;
    private final PermissionProvider permissionProvider;
    private final SkillSourceTrustPolicy sourceTrustPolicy;
    private final boolean activationEnabled;
    private final ConcurrentHashMap<String, ActiveEntry> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkillLoadResult> failedRegistry = new ConcurrentHashMap<>();

    public LealoneSkillHotLoader(SkillSourceCompiler compiler, ClassCacheManager cacheManager) {
        this(compiler, cacheManager, false);
    }

    public LealoneSkillHotLoader(SkillSourceCompiler compiler, ClassCacheManager cacheManager, boolean activationEnabled) {
        this(compiler, cacheManager, activationEnabled, null);
    }

    public LealoneSkillHotLoader(
            SkillSourceCompiler compiler,
            ClassCacheManager cacheManager,
            boolean activationEnabled,
            PermissionProvider permissionProvider) {
        this(compiler, cacheManager, activationEnabled, permissionProvider, null);
    }

    public LealoneSkillHotLoader(
            SkillSourceCompiler compiler,
            ClassCacheManager cacheManager,
            boolean activationEnabled,
            PermissionProvider permissionProvider,
            SkillSourceTrustPolicy sourceTrustPolicy) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
        this.activationEnabled = activationEnabled;
        this.permissionProvider = permissionProvider;
        this.sourceTrustPolicy = sourceTrustPolicy;
    }

    @Override
    public boolean isActivationEnabled() {
        return activationEnabled;
    }

    @Override
    public SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash) {
        return load(skillName, entryClassName, javaSource, sourceHash, null);
    }

    @Override
    public SkillLoadResult load(
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash,
            PermissionContext permissionContext) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");

        if (!activationEnabled) {
            return disabledResult(entryClassName);
        }

        requirePermission(skillName, ACTION_LOAD, permissionContext, entryClassName, sourceHash);
        requireTrustedSource(skillName, sourceHash);

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
        return replace(skillName, entryClassName, javaSource, sourceHash, null);
    }

    @Override
    public SkillLoadResult replace(
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash,
            PermissionContext permissionContext) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");

        if (!activationEnabled) {
            return disabledResult(entryClassName);
        }

        requirePermission(skillName, ACTION_REPLACE, permissionContext, entryClassName, sourceHash);
        requireTrustedSource(skillName, sourceHash);

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
        unload(skillName, null);
    }

    @Override
    public void unload(String skillName, PermissionContext permissionContext) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        if (!activationEnabled) {
            return;
        }
        requirePermission(skillName, ACTION_UNLOAD, permissionContext, null, null);
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

    private static SkillLoadResult disabledResult(String entryClassName) {
        return new SkillLoadResult(false, entryClassName,
                List.of(new SkillCompilationDiagnostic(HOT_LOADING_DISABLED_MESSAGE, -1, -1)));
    }

    private void requirePermission(
            String skillName,
            String action,
            PermissionContext permissionContext,
            String entryClassName,
            String sourceHash) {
        if (permissionProvider == null || permissionContext == null) {
            throw permissionFailure(skillName, action, "missing permission provider or caller context");
        }
        Permission permission = new Permission(
                action,
                "skill:" + skillName,
                permissionConstraints(entryClassName, sourceHash));
        PermissionDecision decision = permissionProvider.check(permission, permissionContext);
        if (decision != PermissionDecision.ALLOW) {
            String reason = decision == PermissionDecision.CONFIRM
                    ? "explicit confirmation is required"
                    : "permission was denied";
            throw permissionFailure(skillName, action, reason);
        }
    }

    private static Map<String, String> permissionConstraints(String entryClassName, String sourceHash) {
        if (entryClassName == null && sourceHash == null) {
            return Map.of();
        }
        return Map.of("entryClassName", entryClassName, "sourceHash", sourceHash);
    }

    private static SkillHotLoadPermissionException permissionFailure(String skillName, String action, String reason) {
        return new SkillHotLoadPermissionException(
                "Hot-load permission rejected for skill '" + skillName + "' action '" + action + "': " + reason);
    }

    private void requireTrustedSource(String skillName, String sourceHash) {
        if (sourceTrustPolicy == null) {
            throw trustFailure(skillName, sourceHash, "missing trusted-source policy");
        }
        if (!sourceTrustPolicy.isTrusted(skillName, sourceHash)) {
            throw trustFailure(skillName, sourceHash, "source is not trusted");
        }
    }

    private static SkillHotLoadTrustException trustFailure(String skillName, String sourceHash, String reason) {
        return new SkillHotLoadTrustException(
                "Hot-load trusted-source rejected for skill '" + skillName
                        + "' source hash '" + sourceHash + "': " + reason);
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
