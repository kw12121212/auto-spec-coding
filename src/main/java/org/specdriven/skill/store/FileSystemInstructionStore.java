package org.specdriven.skill.store;

import org.specdriven.skill.sql.SkillMarkdownParser;
import org.specdriven.skill.sql.SkillSqlException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSystemInstructionStore implements SkillInstructionStore {

    @Override
    public String loadInstructions(String skillId, Path skillDir) {
        Path skillMd = skillDir.resolve("SKILL.md");
        try {
            return SkillMarkdownParser.parse(skillMd).instructionBody();
        } catch (SkillSqlException e) {
            throw new SkillInstructionStoreException(
                    "Failed to load instructions for skill '" + skillId + "' from " + skillMd, e);
        }
    }

    @Override
    public String loadResource(String skillId, Path skillDir, String relativePath) {
        Path resolved = skillDir.resolve(relativePath).normalize();
        Path base = skillDir.normalize();

        if (!resolved.startsWith(base)) {
            throw new SkillInstructionStoreException(
                    "Resource path escapes skill directory for skill '" + skillId + "': " + relativePath);
        }

        if (!Files.exists(resolved)) {
            throw new SkillInstructionStoreException(
                    "Resource not found for skill '" + skillId + "': " + resolved);
        }

        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new SkillInstructionStoreException(
                    "Failed to read resource for skill '" + skillId + "': " + resolved, e);
        }
    }
}
