package org.specdriven.skill.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillInstructionStoreTest {

    @TempDir
    Path tempDir;

    private final SkillInstructionStore store = new FileSystemInstructionStore();

    // --- loadInstructions ---

    @Test
    void loadInstructionsReturnsBody() throws Exception {
        Path skillDir = createSkillDir("my-skill", """
                ---
                skill_id: my_skill
                name: my-skill
                ---
                You are a helpful assistant.
                """);

        String body = store.loadInstructions("my_skill", skillDir);
        assertEquals("You are a helpful assistant.\n", body);
    }

    @Test
    void loadInstructionsReturnsEmptyBodyWhenNonePresent() throws Exception {
        Path skillDir = createSkillDir("empty-skill", """
                ---
                skill_id: empty_skill
                name: empty-skill
                ---
                """);

        String body = store.loadInstructions("empty_skill", skillDir);
        assertEquals("", body);
    }

    @Test
    void loadInstructionsThrowsWhenSkillMdMissing() {
        Path missingDir = tempDir.resolve("nonexistent");

        SkillInstructionStoreException ex = assertThrows(SkillInstructionStoreException.class,
                () -> store.loadInstructions("missing_skill", missingDir));
        assertTrue(ex.getMessage().contains("missing_skill"));
    }

    // --- loadResource ---

    @Test
    void loadResourceReturnsFileContent() throws Exception {
        Path skillDir = createSkillDir("tool-skill", """
                ---
                skill_id: tool_skill
                name: tool-skill
                ---
                body
                """);
        Files.writeString(skillDir.resolve("script.sh"), "#!/bin/bash\necho hello\n");

        String content = store.loadResource("tool_skill", skillDir, "script.sh");
        assertEquals("#!/bin/bash\necho hello\n", content);
    }

    @Test
    void loadResourceThrowsOnPathTraversal() throws Exception {
        Path skillDir = createSkillDir("traversal-skill", """
                ---
                skill_id: traversal_skill
                name: traversal-skill
                ---
                body
                """);

        SkillInstructionStoreException ex = assertThrows(SkillInstructionStoreException.class,
                () -> store.loadResource("traversal_skill", skillDir, "../outside.txt"));
        assertTrue(ex.getMessage().contains("escapes skill directory"));
    }

    @Test
    void loadResourceThrowsOnMissingFile() throws Exception {
        Path skillDir = createSkillDir("missing-file-skill", """
                ---
                skill_id: missing_file_skill
                name: missing-file-skill
                ---
                body
                """);

        SkillInstructionStoreException ex = assertThrows(SkillInstructionStoreException.class,
                () -> store.loadResource("missing_file_skill", skillDir, "no_such_file.txt"));
        assertTrue(ex.getMessage().contains("Resource not found"));
    }

    private Path createSkillDir(String name, String skillMdContent) throws Exception {
        Path dir = tempDir.resolve(name);
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("SKILL.md"), skillMdContent);
        return dir;
    }
}
