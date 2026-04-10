package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggingDeliveryChannelTest {

    @Test
    void send_doesNotThrow() {
        LoggingDeliveryChannel channel = new LoggingDeliveryChannel();
        Question q = new Question(
                "q-1", "s-1", "Continue?",
                "Cannot proceed.", "Safe option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN);

        assertDoesNotThrow(() -> channel.send(q));
    }

    @Test
    void close_doesNotThrow() {
        LoggingDeliveryChannel channel = new LoggingDeliveryChannel();
        assertDoesNotThrow(channel::close);
    }

    @Test
    void closeIsIdempotent() {
        LoggingDeliveryChannel channel = new LoggingDeliveryChannel();
        assertDoesNotThrow(() -> {
            channel.close();
            channel.close();
        });
    }
}
