package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.permission.LealonePolicyStore;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import org.specdriven.agent.testsupport.LealoneTestDb;

/**
 * Documents the ORM adoption guidelines invariants established by the M31 pilot:
 *
 * <ul>
 *   <li><strong>Escape hatch</strong>: {@link LealonePolicyStore} was deliberately NOT
 *       migrated to ORM. It uses raw JDBC and MUST operate correctly alongside
 *       ORM-backed Stores on the same embedded database.</li>
 *   <li><strong>Coexistence</strong>: ORM-backed ({@link LealoneDeliveryLogStore},
 *       {@link LealoneQuestionStore}) and raw-JDBC ({@link LealonePolicyStore})
 *       Stores MUST each read their own written data without interference.</li>
 *   <li><strong>Interface preservation</strong>: All Stores retain their public
 *       method signatures and return types regardless of their backing strategy.</li>
 * </ul>
 */
class OrmAdoptionGuidelinesTest {

    /**
     * The escape-hatch invariant: a Store that does not qualify for ORM migration
     * (LealonePolicyStore — raw JDBC, two tables, MERGE-based upsert) MUST work
     * correctly alongside ORM-backed Stores using the same embedded database URL.
     */
    @Test
    void escapehatchStore_operatesAlongsideOrmStores() {
        String jdbcUrl = freshUrl("escape_hatch");

        // ORM-backed Stores (migrated in M31)
        LealoneDeliveryLogStore deliveryStore = new LealoneDeliveryLogStore(jdbcUrl);
        LealoneQuestionStore questionStore = new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);
        // Escape-hatch Store — deliberately not migrated, remains raw JDBC
        LealonePolicyStore policyStore = new LealonePolicyStore(jdbcUrl);

        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-guidelines", "discord", 1, DeliveryStatus.SENT, 200, null, 1000L);
        Question question = waitingQuestion("q-guidelines", "s-guidelines");
        Permission permission = new Permission("read", "/workspace", Map.of());
        PermissionContext context = new PermissionContext("read-tool", "read", "agent-guidelines");

        deliveryStore.save(attempt);
        questionStore.save(question);
        policyStore.grant(permission, context);

        List<DeliveryAttempt> attempts = deliveryStore.findByQuestion("q-guidelines");
        assertEquals(1, attempts.size());
        assertEquals(attempt, attempts.get(0));

        Optional<Question> pending = questionStore.findPending("s-guidelines");
        assertTrue(pending.isPresent());
        assertEquals(question, pending.get());

        assertEquals(PermissionDecision.ALLOW, policyStore.find(permission, context).orElseThrow());
    }

    /**
     * Coexistence contract: each Store reads only its own data after interleaved
     * writes, with no cross-Store interference.
     */
    @Test
    void allThreeStores_readOwnDataWithoutInterference() {
        String jdbcUrl = freshUrl("no_interference");

        LealoneDeliveryLogStore deliveryStore = new LealoneDeliveryLogStore(jdbcUrl);
        LealoneQuestionStore questionStore = new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);
        LealonePolicyStore policyStore = new LealonePolicyStore(jdbcUrl);

        // Interleaved writes
        policyStore.grant(new Permission("exec", "/bin/bash", Map.of()), new PermissionContext("bash", "run", "a1"));
        questionStore.save(waitingQuestion("q-ni", "s-ni"));
        deliveryStore.save(new DeliveryAttempt("q-ni", "telegram", 1, DeliveryStatus.FAILED, 500, "err", 9000L));
        policyStore.grant(new Permission("write", "/tmp", Map.of()), new PermissionContext("write-tool", "write", "a2"));

        // Each Store reads only its own data
        assertEquals(1, deliveryStore.findByQuestion("q-ni").size());
        assertEquals(1, questionStore.findBySession("s-ni").size());
        assertEquals(2, policyStore.listPolicies().size());

        assertTrue(deliveryStore.findByQuestion("q-other").isEmpty());
        assertTrue(questionStore.findBySession("s-other").isEmpty());
    }

    private static String freshUrl(String prefix) {
        return LealoneTestDb.freshJdbcUrl();
    }

    private static Question waitingQuestion(String questionId, String sessionId) {
        return new Question(
                questionId,
                sessionId,
                "Proceed with the operation?",
                "The workflow cannot proceed without confirmation.",
                "Confirm if the context is safe.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
    }

    private static class NoOpEventBus implements EventBus {
        @Override
        public void publish(Event event) {}

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {}

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {}
    }
}
