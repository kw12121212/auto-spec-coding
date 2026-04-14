package org.specdriven.skill.hotload;

import org.specdriven.agent.permission.PermissionContext;

import java.util.Optional;
import java.util.Set;

public interface SkillHotLoader {

    boolean isActivationEnabled();

    SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash);

    SkillLoadResult load(
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash,
            PermissionContext permissionContext);

    SkillLoadResult replace(String skillName, String entryClassName, String javaSource, String sourceHash);

    SkillLoadResult replace(
            String skillName,
            String entryClassName,
            String javaSource,
            String sourceHash,
            PermissionContext permissionContext);

    void unload(String skillName);

    void unload(String skillName, PermissionContext permissionContext);

    Optional<ClassLoader> activeLoader(String skillName);

    Set<String> loadedSkillNames();

    Set<String> failedSkillNames();
}
