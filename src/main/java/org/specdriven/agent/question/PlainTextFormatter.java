package org.specdriven.agent.question;

/**
 * Default {@link RichMessageFormatter} that produces plain-text messages
 * containing the question, impact, recommendation, sessionId, and questionId.
 */
public final class PlainTextFormatter implements RichMessageFormatter {

    public static final PlainTextFormatter INSTANCE = new PlainTextFormatter();

    private PlainTextFormatter() {}

    @Override
    public String format(Question question) {
        return "[Question] " + question.question()
                + "\n[Impact] " + question.impact()
                + "\n[Recommendation] " + question.recommendation()
                + "\n[Session] " + question.sessionId()
                + "\n[Question ID] " + question.questionId();
    }
}
