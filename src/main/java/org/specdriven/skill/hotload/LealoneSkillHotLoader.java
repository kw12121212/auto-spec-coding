package org.specdriven.skill.hotload;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
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
import java.util.LinkedHashMap;
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
    private static final String AUDIT_SOURCE = "skill-hot-loader";
    private static final String OPERATION_LOAD = "load";
    private static final String OPERATION_REPLACE = "replace";
    private static final String OPERATION_UNLOAD = "unload";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";
    private static final String RESULT_REJECTED = "rejected";
    private static final String RESULT_NOOP = "noop";
    private static final String FAILURE_DISABLED = "activation-disabled";
    private static final String FAILURE_PERMISSION_REJECTED = "permission-rejected";
    private static final String FAILURE_TRUST_REJECTED = "trust-rejected";
    private static final String FAILURE_DUPLICATE_REGISTRATION = "duplicate-registration";
    private static final String FAILURE_COMPILE_DIAGNOSTICS = "compile-diagnostics";
    private static final String FAILURE_INFRASTRUCTURE = "infrastructure-failure";
    private static final String PHASE_CACHE_HIT = "cache-hit";
    private static final String PHASE_COMPILED = "compiled";

    private final SkillSourceCompiler compiler;
    private final ClassCacheManager cacheManager;
    private final PermissionProvider permissionProvider;
    private final SkillSourceTrustPolicy sourceTrustPolicy;
    private final EventBus auditEventBus;
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
        this(compiler, cacheManager, activationEnabled, permissionProvider, sourceTrustPolicy, null);
    }

    public LealoneSkillHotLoader(
            SkillSourceCompiler compiler,
            ClassCacheManager cacheManager,
            boolean activationEnabled,
            PermissionProvider permissionProvider,
            SkillSourceTrustPolicy sourceTrustPolicy,
            EventBus auditEventBus) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
        this.activationEnabled = activationEnabled;
        this.permissionProvider = permissionProvider;
        this.sourceTrustPolicy = sourceTrustPolicy;
        this.auditEventBus = auditEventBus;
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
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_DISABLED, null);
            return disabledResult(entryClassName);
        }

        try {
            requirePermission(skillName, ACTION_LOAD, permissionContext, entryClassName, sourceHash);
        } catch (SkillHotLoadPermissionException e) {
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_PERMISSION_REJECTED, null);
            throw e;
        }
        try {
            requireTrustedSource(skillName, sourceHash);
        } catch (SkillHotLoadTrustException e) {
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_TRUST_REJECTED, null);
            throw e;
        }

        if (registry.containsKey(skillName)) {
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_FAILURE, FAILURE_DUPLICATE_REGISTRATION, null);
            return new SkillLoadResult(false, entryClassName,
                    List.of(new SkillCompilationDiagnostic(
                            "Skill '" + skillName + "' is already registered; use replace() to update it", -1, -1)));
        }

        LoadOutcome outcome;
        try {
            outcome = resolveLoader(skillName, entryClassName, javaSource, sourceHash);
        } catch (SkillHotLoaderException e) {
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_FAILURE, FAILURE_INFRASTRUCTURE, null);
            throw e;
        }
        if (!outcome.success()) {
            SkillLoadResult failure = new SkillLoadResult(false, entryClassName, outcome.diagnostics());
            failedRegistry.put(skillName, failure);
            publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                    RESULT_FAILURE, FAILURE_COMPILE_DIAGNOSTICS, null);
            return failure;
        }

        registry.put(skillName, new ActiveEntry(outcome.classLoader(), entryClassName, sourceHash));
        failedRegistry.remove(skillName);
        publishSourceAudit(OPERATION_LOAD, skillName, sourceHash, permissionContext,
                RESULT_SUCCESS, null, outcome.phase());
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
            publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_DISABLED, null);
            return disabledResult(entryClassName);
        }

        try {
            requirePermission(skillName, ACTION_REPLACE, permissionContext, entryClassName, sourceHash);
        } catch (SkillHotLoadPermissionException e) {
            publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_PERMISSION_REJECTED, null);
            throw e;
        }
        try {
            requireTrustedSource(skillName, sourceHash);
        } catch (SkillHotLoadTrustException e) {
            publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                    RESULT_REJECTED, FAILURE_TRUST_REJECTED, null);
            throw e;
        }

        LoadOutcome outcome;
        try {
            outcome = resolveLoader(skillName, entryClassName, javaSource, sourceHash);
        } catch (SkillHotLoaderException e) {
            publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                    RESULT_FAILURE, FAILURE_INFRASTRUCTURE, null);
            throw e;
        }
        if (!outcome.success()) {
            SkillLoadResult failure = new SkillLoadResult(false, entryClassName, outcome.diagnostics());
            failedRegistry.put(skillName, failure);
            publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                    RESULT_FAILURE, FAILURE_COMPILE_DIAGNOSTICS, null);
            return failure;
        }

        registry.put(skillName, new ActiveEntry(outcome.classLoader(), entryClassName, sourceHash));
        failedRegistry.remove(skillName);
        publishSourceAudit(OPERATION_REPLACE, skillName, sourceHash, permissionContext,
                RESULT_SUCCESS, null, outcome.phase());
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
            publishAudit(OPERATION_UNLOAD, skillName, null, permissionContext,
                    RESULT_REJECTED, FAILURE_DISABLED, null, null);
            return;
        }
        try {
            requirePermission(skillName, ACTION_UNLOAD, permissionContext, null, null);
        } catch (SkillHotLoadPermissionException e) {
            publishAudit(OPERATION_UNLOAD, skillName, null, permissionContext,
                    RESULT_REJECTED, FAILURE_PERMISSION_REJECTED, null, null);
            throw e;
        }
        ActiveEntry removed = registry.remove(skillName);
        failedRegistry.remove(skillName);
        publishAudit(OPERATION_UNLOAD, skillName, null, permissionContext,
                removed == null ? RESULT_NOOP : RESULT_SUCCESS, null, null,
                removed == null ? "absent" : "removed");
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
                return LoadOutcome.success(loader, PHASE_CACHE_HIT);
            }
            Path classDir = cacheManager.resolveClassDir(skillName, sourceHash);
            SkillCompilationResult result = compiler.compile(entryClassName, javaSource, classDir);
            if (!result.success()) {
                return LoadOutcome.failure(result.diagnostics());
            }
            ClassLoader loader = cacheManager.loadCached(skillName, sourceHash);
            return LoadOutcome.success(loader, PHASE_COMPILED);
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

    private void publishSourceAudit(
            String operation,
            String skillName,
            String sourceHash,
            PermissionContext permissionContext,
            String result,
            String failureCategory,
            String activationPhase) {
        publishAudit(operation, skillName, sourceHash, permissionContext, result, failureCategory, activationPhase, null);
    }

    private void publishAudit(
            String operation,
            String skillName,
            String sourceHash,
            PermissionContext permissionContext,
            String result,
            String failureCategory,
            String activationPhase,
            String unloadOutcome) {
        if (auditEventBus == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operation", operation);
        metadata.put("skillName", skillName);
        metadata.put("result", result);
        if (sourceHash != null) {
            metadata.put("sourceHash", sourceHash);
        }
        if (permissionContext != null
                && permissionContext.requester() != null
                && !permissionContext.requester().isBlank()) {
            metadata.put("requester", permissionContext.requester());
        }
        if (failureCategory != null) {
            metadata.put("failureCategory", failureCategory);
        }
        if (activationPhase != null) {
            metadata.put("activationPhase", activationPhase);
        }
        if (unloadOutcome != null) {
            metadata.put("unloadOutcome", unloadOutcome);
        }
        auditEventBus.publish(new Event(
                EventType.SKILL_HOT_LOAD_OPERATION,
                System.currentTimeMillis(),
                AUDIT_SOURCE,
                metadata));
    }

    private record LoadOutcome(
            boolean success,
            ClassLoader classLoader,
            List<SkillCompilationDiagnostic> diagnostics,
            String phase) {

        static LoadOutcome success(ClassLoader classLoader, String phase) {
            return new LoadOutcome(true, classLoader, List.of(), phase);
        }

        static LoadOutcome failure(List<SkillCompilationDiagnostic> diagnostics) {
            return new LoadOutcome(false, null, diagnostics, null);
        }
    }
}
