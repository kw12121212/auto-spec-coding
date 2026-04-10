package org.specdriven.agent.answer;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.AssistantMessage;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.agent.SystemMessage;
import org.specdriven.agent.agent.UserMessage;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerContextManagerTest {

    private Question createTestQuestion() {
        return new Question(
                "q-123",
                "session-456",
                "What is the best approach?",
                "Choosing wrong approach may delay the project",
                "Consider using the Strategy pattern",
                QuestionStatus.OPEN,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.AUTO_AI_REPLY
        );
    }

    @Test
    void testEmptyMessagesReturnsFallback() {
        AnswerContextManager manager = new AnswerContextManager(10);
        Question question = createTestQuestion();

        List<Message> result = manager.crop(List.of(), question);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof SystemMessage);
        String content = ((SystemMessage) result.get(0)).content();
        assertTrue(content.contains("What is the best approach?"));
        assertTrue(content.contains("Choosing wrong approach may delay the project"));
    }

    @Test
    void testPreservesSystemMessages() {
        AnswerContextManager manager = new AnswerContextManager(5);
        Question question = createTestQuestion();

        List<Message> messages = List.of(
                new SystemMessage("System instruction 1", 1000),
                new UserMessage("User message 1", 2000),
                new SystemMessage("System instruction 2", 3000),
                new AssistantMessage("Assistant response", 4000)
        );

        List<Message> result = manager.crop(messages, question);

        // Should include both system messages
        long systemCount = result.stream().filter(m -> m instanceof SystemMessage).count();
        assertEquals(2, systemCount);
    }

    @Test
    void testRespectsMaxContextMessages() {
        AnswerContextManager manager = new AnswerContextManager(3);
        Question question = createTestQuestion();

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(new UserMessage("Message " + i, i * 1000));
        }

        List<Message> result = manager.crop(messages, question);

        assertTrue(result.size() <= 3, "Result should not exceed maxContextMessages");
    }

    @Test
    void testKeepsMostRecentMessages() {
        AnswerContextManager manager = new AnswerContextManager(3);
        Question question = createTestQuestion();

        List<Message> messages = List.of(
                new UserMessage("Old message 1", 1000),
                new UserMessage("Old message 2", 2000),
                new UserMessage("Recent message 1", 8000),
                new UserMessage("Recent message 2", 9000)
        );

        List<Message> result = manager.crop(messages, question);

        // Should keep the most recent messages
        assertTrue(result.stream().anyMatch(m -> m instanceof UserMessage && ((UserMessage) m).content().equals("Recent message 1")));
        assertTrue(result.stream().anyMatch(m -> m instanceof UserMessage && ((UserMessage) m).content().equals("Recent message 2")));
    }

    @Test
    void testSystemMessagesFirst() {
        AnswerContextManager manager = new AnswerContextManager(5);
        Question question = createTestQuestion();

        List<Message> messages = List.of(
                new UserMessage("User 1", 1000),
                new SystemMessage("System 1", 2000),
                new UserMessage("User 2", 3000)
        );

        List<Message> result = manager.crop(messages, question);

        // System message should be first
        assertTrue(result.get(0) instanceof SystemMessage);
    }

    @Test
    void testOnlySystemMessages() {
        AnswerContextManager manager = new AnswerContextManager(5);
        Question question = createTestQuestion();

        List<Message> messages = List.of(
                new SystemMessage("System 1", 1000),
                new SystemMessage("System 2", 2000),
                new SystemMessage("System 3", 3000)
        );

        List<Message> result = manager.crop(messages, question);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(m -> m instanceof SystemMessage));
    }

    @Test
    void testMaxContextMessagesZeroOrNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerContextManager(0));
        assertThrows(IllegalArgumentException.class, () -> new AnswerContextManager(-1));
    }

    @Test
    void testNullMessagesThrows() {
        AnswerContextManager manager = new AnswerContextManager(10);
        assertThrows(NullPointerException.class, () -> manager.crop(null, createTestQuestion()));
    }

    @Test
    void testNullQuestionThrows() {
        AnswerContextManager manager = new AnswerContextManager(10);
        List<Message> messages = List.of(new UserMessage("Test", 1000));
        assertThrows(NullPointerException.class, () -> manager.crop(messages, null));
    }
}
