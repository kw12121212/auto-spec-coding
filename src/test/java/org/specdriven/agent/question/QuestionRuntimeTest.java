package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionRuntimeTest {

    @Test
    void beginWaitingQuestion_rejectsSecondQuestionForSameSession() {
        QuestionRuntime runtime = new QuestionRuntime(new SimpleEventBus());
        Question first = waitingQuestion("q-1", "session-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        Question second = waitingQuestion("q-2", "session-1", DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);

        runtime.beginWaitingQuestion(first);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runtime.beginWaitingQuestion(second));
        assertTrue(error.getMessage().contains("waiting question"));
    }

    @Test
    void acceptAnswer_publishesAnsweredEventAndClearsPendingQuestion() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<EventType> eventTypes = Collections.synchronizedList(new ArrayList<>());
        eventBus.subscribe(EventType.QUESTION_CREATED, event -> eventTypes.add(event.type()));
        eventBus.subscribe(EventType.QUESTION_ANSWERED, event -> eventTypes.add(event.type()));

        QuestionRuntime runtime = new QuestionRuntime(eventBus);
        Question waiting = waitingQuestion("q-1", "session-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        runtime.beginWaitingQuestion(waiting);

        Answer answer = new Answer(
                "Use the canary first.",
                "The rollout remains reversible that way.",
                "operator:zoe",
                AnswerSource.HUMAN_INLINE,
                1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Human approval required.",
                10L
        );

        runtime.submitAnswer("session-1", "q-1", answer);
        Answer accepted = runtime.pollAnswer("session-1", "q-1", 10L).orElseThrow();
        Question answered = runtime.acceptAnswer(waiting, accepted);

        assertEquals(QuestionStatus.ANSWERED, answered.status());
        assertTrue(runtime.pendingQuestion("session-1").isEmpty());
        assertEquals(List.of(EventType.QUESTION_CREATED, EventType.QUESTION_ANSWERED), eventTypes);
    }

    @Test
    void expireQuestion_rejectsLateAnswers() {
        QuestionRuntime runtime = new QuestionRuntime(new SimpleEventBus());
        Question waiting = waitingQuestion("q-9", "session-9", DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);
        runtime.beginWaitingQuestion(waiting);
        runtime.expireQuestion(waiting);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runtime.submitAnswer(
                "session-9",
                "q-9",
                new Answer(
                        "Still waiting on approval.",
                        "The reply arrived after the timeout.",
                        "mobile:late",
                        AnswerSource.HUMAN_MOBILE,
                        1.0d,
                        QuestionDecision.ANSWER_ACCEPTED,
                        DeliveryMode.PUSH_MOBILE_WAIT_HUMAN,
                        "Late reply.",
                        11L
                )));
        assertTrue(error.getMessage().contains("EXPIRED"));
    }

    private static Question waitingQuestion(String questionId, String sessionId, DeliveryMode deliveryMode) {
        return new Question(
                questionId,
                sessionId,
                "Should we continue?",
                "The workflow cannot proceed without a decision.",
                "Use the safest documented option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                deliveryMode
        );
    }
}
