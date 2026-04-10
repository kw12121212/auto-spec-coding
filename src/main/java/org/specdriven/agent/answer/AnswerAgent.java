package org.specdriven.agent.answer;

import org.specdriven.agent.agent.Message;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.Question;

import java.util.List;

/**
 * Answer Agent generates answers to questions automatically.
 *
 * <p>The Answer Agent analyzes a question in the context of a conversation
 * and produces a structured {@link Answer} without requiring human intervention.
 */
public interface AnswerAgent {

    /**
     * Generates an answer to the given question based on the conversation context.
     *
     * @param question the question to answer
     * @param messages the conversation history for context
     * @return a structured answer
     * @throws AnswerAgentException         if an error occurs during answer generation
     * @throws AnswerAgentTimeoutException  if the operation times out
     */
    Answer resolve(Question question, List<Message> messages);
}
