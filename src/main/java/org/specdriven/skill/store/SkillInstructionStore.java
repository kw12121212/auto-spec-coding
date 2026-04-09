package org.specdriven.skill.store;

import java.nio.file.Path;

public interface SkillInstructionStore {

    /**
     * Load the instruction body from SKILL.md in the given skill directory (Level 2 load).
     *
     * @param skillId  skill identifier
     * @param skillDir directory containing SKILL.md
     * @return instruction body text (may be empty)
     * @throws SkillInstructionStoreException if the file cannot be read or parsed
     */
    String loadInstructions(String skillId, Path skillDir);

    /**
     * Load a resource file relative to the skill directory (Level 3 load).
     *
     * @param skillId      skill identifier
     * @param skillDir     base directory for the skill
     * @param relativePath path to the resource, relative to skillDir
     * @return file content as a UTF-8 string
     * @throws SkillInstructionStoreException if the path escapes skillDir or the file does not exist
     */
    String loadResource(String skillId, Path skillDir, String relativePath);
}
