package org.specdriven.skill.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.lealone.db.service.Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
import org.specdriven.skill.store.FileSystemInstructionStore;
import org.specdriven.skill.store.SkillInstructionStoreException;

import sun.misc.Unsafe;

class SkillServiceExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void executeServiceReturnsFinalAssistantText() throws Exception {
        Path skillDir = Files.createDirectory(tempDir.resolve("my-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                skill_id: my_skill
                name: my-skill
                description: test
                author: test
                type: agent_skill
                version: 1.0.0
                ---
                You are the system prompt.
                """);

        Service service = serviceWithSql("""
                CREATE SERVICE my_skill LANGUAGE 'skill'
                PARAMETERS 'skill_id' 'my_skill', 'skill_dir' '%s'
                """.formatted(skillDir.toAbsolutePath().toString()));

        LlmClient llmClient = messages -> {
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).role());
            assertEquals("You are the system prompt.\n", messages.get(0).content());
            assertEquals("user", messages.get(1).role());
            assertEquals("hello", messages.get(1).content());
            return new LlmResponse.TextResponse("final answer");
        };

        SkillServiceExecutor executor = new SkillServiceExecutor(
                service,
                new SkillParameterParser(),
                new FileSystemInstructionStore(),
                ignored -> llmClient,
                Map.of()
        );

        Object result = executor.executeService("EXECUTE", Map.of("PROMPT", "hello"));
        assertEquals("final answer", result);
    }

    @Test
    void executeServiceReturnsEmptyStringWhenNoAssistantTextProduced() throws Exception {
        Path skillDir = Files.createDirectory(tempDir.resolve("loop-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                skill_id: loop_skill
                name: loop-skill
                ---
                Loop forever.
                """);

        Service service = serviceWithSql("""
                CREATE SERVICE loop_skill LANGUAGE 'skill'
                PARAMETERS 'skill_id' 'loop_skill', 'skill_dir' '%s'
                """.formatted(skillDir.toAbsolutePath().toString()));

        SkillServiceExecutor executor = new SkillServiceExecutor(
                service,
                new SkillParameterParser(),
                new FileSystemInstructionStore(),
                ignored -> messages -> new LlmResponse.ToolCallResponse(List.of()),
                Map.of()
        );

        Object result = executor.executeService("EXECUTE", Map.of("PROMPT", "hello"));
        assertEquals("", result);
    }

    @Test
    void instructionLoadFailureIsPropagated() throws Exception {
        Path skillDir = Files.createDirectory(tempDir.resolve("broken-skill"));
        Service service = serviceWithSql("""
                CREATE SERVICE broken_skill LANGUAGE 'skill'
                PARAMETERS 'skill_id' 'broken_skill', 'skill_dir' '%s'
                """.formatted(skillDir.toAbsolutePath().toString()));

        SkillServiceExecutor executor = new SkillServiceExecutor(
                service,
                new SkillParameterParser(),
                new FileSystemInstructionStore(),
                ignored -> messages -> new LlmResponse.TextResponse("unused"),
                Map.of()
        );

        SkillInstructionStoreException error = assertThrows(
                SkillInstructionStoreException.class,
                () -> executor.executeService("EXECUTE", Map.of("PROMPT", "hello"))
        );
        assertTrue(error.getMessage().contains("broken_skill"));
    }

    private static Service serviceWithSql(String sql) throws Exception {
        Unsafe unsafe = unsafe();
        Service service = (Service) unsafe.allocateInstance(Service.class);
        Field sqlField = Service.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(service, sql);
        return service;
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}
