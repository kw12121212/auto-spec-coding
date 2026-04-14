package org.specdriven.skill.hotload;

import java.util.Optional;
import java.util.Set;

public interface SkillHotLoader {

    boolean isActivationEnabled();

    SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash);

    SkillLoadResult replace(String skillName, String entryClassName, String javaSource, String sourceHash);

    void unload(String skillName);

    Optional<ClassLoader> activeLoader(String skillName);

    Set<String> loadedSkillNames();

    Set<String> failedSkillNames();
}
