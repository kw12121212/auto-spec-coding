package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RetryingDeliveryChannelTest {

    private CapturingEventBus eventBus;
    private CapturingLogStore logStore;

    @BeforeEach
    void setUp() {
        eventBus = new CapturingEventBus();
        logStore = new CapturingLogStore();
    }

    // -------------------------------------------------------------------------
    // Successful first attempt
    // -------------------------------------------------------------------------

    @Test
    void successfulFirstAttempt_callsOnce() {
        StubChannel delegate = new StubChannel(0);
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.send(question("q-1", "s-1"));

        assertEquals(1, delegate.sendCount());
        assertEquals(1, logStore.attempts.size());
        assertEquals(DeliveryStatus.SENT, logStore.attempts.get(0).status());
    }

    @Test
    void successfulFirstAttempt_emitsDeliverySucceeded() {
        StubChannel delegate = new StubChannel(0);
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.send(question("q-1", "s-1"));

        List<Event> attempted = eventsOfType(EventType.DELIVERY_ATTEMPTED);
        List<Event> succeeded = eventsOfType(EventType.DELIVERY_SUCCEEDED);
        assertEquals(1, attempted.size());
        assertEquals(1, succeeded.size());
        assertEquals("q-1", succeeded.get(0).metadata().get("questionId"));
    }

    // -------------------------------------------------------------------------
    // Retry on transient failure
    // -------------------------------------------------------------------------

    @Test
    void retryOnFailure_succeedsOnThirdAttempt() {
        StubChannel delegate = new StubChannel(2); // fail 2 times, succeed on 3rd
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.send(question("q-2", "s-2"));

        assertEquals(3, delegate.sendCount());
        assertEquals(3, logStore.attempts.size());
        assertEquals(DeliveryStatus.RETRYING, logStore.attempts.get(0).status());
        assertEquals(DeliveryStatus.RETRYING, logStore.attempts.get(1).status());
        assertEquals(DeliveryStatus.SENT, logStore.attempts.get(2).status());
    }

    @Test
    void retryOnFailure_emitsSucceededOnFinalAttempt() {
        StubChannel delegate = new StubChannel(2);
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.send(question("q-2", "s-2"));

        List<Event> succeeded = eventsOfType(EventType.DELIVERY_SUCCEEDED);
        assertEquals(1, succeeded.size());
        assertEquals(3, succeeded.get(0).metadata().get("attemptNumber"));
    }

    // -------------------------------------------------------------------------
    // Exhausted retries
    // -------------------------------------------------------------------------

    @Test
    void exhaustedRetries_throwsAndEmitsFailed() {
        StubChannel delegate = new StubChannel(Integer.MAX_VALUE); // always fail
        RetryingDeliveryChannel channel = retrying(delegate, 2);

        assertThrows(MobileAdapterException.class,
                () -> channel.send(question("q-3", "s-3")));

        assertEquals(2, delegate.sendCount());
        List<Event> failed = eventsOfType(EventType.DELIVERY_FAILED);
        assertEquals(1, failed.size());
        assertEquals("q-3", failed.get(0).metadata().get("questionId"));
        assertNotNull(failed.get(0).metadata().get("errorMessage"));
    }

    // -------------------------------------------------------------------------
    // Non-adapter exception propagates immediately
    // -------------------------------------------------------------------------

    @Test
    void nonAdapterException_propagatesWithoutRetry() {
        QuestionDeliveryChannel throwing = new QuestionDeliveryChannel() {
            @Override public void send(Question q) { throw new NullPointerException("boom"); }
            @Override public void close() {}
        };
        RetryingDeliveryChannel channel = new RetryingDeliveryChannel(
                throwing, "test", new RetryConfig(3, 10, 2.0), logStore, eventBus);

        assertThrows(NullPointerException.class,
                () -> channel.send(question("q-4", "s-4")));

        assertEquals(0, logStore.attempts.size());
        List<Event> failed = eventsOfType(EventType.DELIVERY_FAILED);
        assertTrue(failed.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Event metadata
    // -------------------------------------------------------------------------

    @Test
    void eventsContainCorrectMetadata() {
        StubChannel delegate = new StubChannel(0);
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.send(question("q-meta", "s-meta"));

        Event attempted = eventsOfType(EventType.DELIVERY_ATTEMPTED).get(0);
        assertEquals("q-meta", attempted.metadata().get("questionId"));
        assertEquals("telegram", attempted.metadata().get("channelType"));
        assertEquals(1, attempted.metadata().get("attemptNumber"));
    }

    // -------------------------------------------------------------------------
    // Close delegates
    // -------------------------------------------------------------------------

    @Test
    void close_delegatesToUnderlyingChannel() {
        StubChannel delegate = new StubChannel(0);
        RetryingDeliveryChannel channel = retrying(delegate, 3);

        channel.close();

        assertTrue(delegate.closed());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RetryingDeliveryChannel retrying(StubChannel delegate, int maxAttempts) {
        return new RetryingDeliveryChannel(
                delegate, "telegram", new RetryConfig(maxAttempts, 10, 2.0), logStore, eventBus);
    }

    private static Question question(String questionId, String sessionId) {
        return new Question(
                questionId, sessionId,
                "Should we continue?",
                "Impact description",
                "Recommended action",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
    }

    private List<Event> eventsOfType(EventType type) {
        return eventBus.eventsOfType(type);
    }

    // --- Stubs ---

    private static class StubChannel implements QuestionDeliveryChannel {
        private final int failCount;
        private int sendCount;
        private boolean isClosed;

        StubChannel(int failCount) {
            this.failCount = failCount;
        }

        @Override
        public void send(Question question) {
            sendCount++;
            if (sendCount <= failCount) {
                throw new MobileAdapterException("telegram", "send failed attempt " + sendCount);
            }
        }

        @Override
        public void close() { isClosed = true; }

        int sendCount() { return sendCount; }
        boolean closed() { return isClosed; }
    }

    private static class CapturingLogStore implements DeliveryLogStore {
        final List<DeliveryAttempt> attempts = new ArrayList<>();

        @Override
        public void save(DeliveryAttempt attempt) { attempts.add(attempt); }

        @Override
        public List<DeliveryAttempt> findByQuestion(String questionId) {
            return attempts.stream().filter(a -> a.questionId().equals(questionId)).toList();
        }

        @Override
        public Optional<DeliveryAttempt> findLatestByQuestion(String questionId) {
            return attempts.stream()
                    .filter(a -> a.questionId().equals(questionId))
                    .max(Comparator.comparingInt(DeliveryAttempt::attemptNumber));
        }
    }
}
