package org.specdriven.agent.question;

import java.util.Map;

/**
 * Default {@link QuestionDeliveryChannel} that writes question payloads to the system logger.
 */
public class LoggingDeliveryChannel implements QuestionDeliveryChannel {

    private static final System.Logger LOG =
            System.getLogger(LoggingDeliveryChannel.class.getName());

    @Override
    public void send(Question question) {
        Map<String, Object> payload = question.toPayload();
        LOG.log(System.Logger.Level.INFO, "Question delivery: {0}", payload);
    }

    @Override
    public void close() {
        // no resources to release
    }
}
