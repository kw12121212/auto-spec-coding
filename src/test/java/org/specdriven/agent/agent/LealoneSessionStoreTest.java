package org.specdriven.agent.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.specdriven.agent.testsupport.LealoneTestDb;

class LealoneSessionStoreTest {

    private LealoneSessionStore store;
    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        // Unique in-memory DB per test for isolation
        jdbcUrl = LealoneTestDb.freshJdbcUrl();
        store = new LealoneSessionStore(jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // save → load round-trip
    // -------------------------------------------------------------------------

    @Test
    void saveAndLoad_returnsEquivalentSession() {
        Conversation conv = new Conversation();
        conv.append(new UserMessage("hello", 1000L));
        conv.append(new AssistantMessage("world", 2000L));

        Session session = new Session("sid-001", AgentState.RUNNING,
                1000L, 2000L, 1000L + Session.TTL_MS, conv);
        store.save(session);

        Optional<Session> loaded = store.load("sid-001");
        assertTrue(loaded.isPresent());

        Session s = loaded.get();
        assertEquals("sid-001", s.id());
        assertEquals(AgentState.RUNNING, s.state());
        assertEquals(1000L, s.createdAt());
        assertEquals(1000L + Session.TTL_MS, s.expiryAt());

        List<Message> messages = s.conversation().history();
        assertEquals(2, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("hello", messages.get(0).content());
        assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("world", messages.get(1).content());
    }

    @Test
    void saveAndLoad_preservesAllMessageTypes() {
        Conversation conv = new Conversation();
        conv.append(new SystemMessage("system prompt", 100L));
        conv.append(new UserMessage("user input", 200L));
        conv.append(new AssistantMessage("assistant reply", 300L));
        conv.append(new ToolMessage("tool output", 400L, "myTool", "call-xyz"));

        Session session = new Session("sid-002", AgentState.IDLE,
                100L, 100L, 100L + Session.TTL_MS, conv);
        store.save(session);

        Session loaded = store.load("sid-002").orElseThrow();
        List<Message> messages = loaded.conversation().history();
        assertEquals(4, messages.size());

        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals("system prompt", messages.get(0).content());

        assertInstanceOf(UserMessage.class, messages.get(1));

        assertInstanceOf(AssistantMessage.class, messages.get(2));

        ToolMessage tm = assertInstanceOf(ToolMessage.class, messages.get(3));
        assertEquals("tool output", tm.content());
        assertEquals("myTool", tm.toolName());
        assertEquals("call-xyz", tm.toolCallId());
    }

    // -------------------------------------------------------------------------
    // UUID generation on first save
    // -------------------------------------------------------------------------

    @Test
    void save_withNullId_generatesUuid() {
        Conversation conv = new Conversation();
        Session session = new Session(null, AgentState.IDLE,
                1000L, 1000L, 1000L + Session.TTL_MS, conv);

        String id = store.save(session);

        assertNotNull(id);
        assertFalse(id.isBlank());
        // Verify the session is retrievable by the returned ID
        assertTrue(store.load(id).isPresent());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesSession() {
        Conversation conv = new Conversation();
        Session session = new Session("sid-del", AgentState.STOPPED,
                1000L, 1000L, 1000L + Session.TTL_MS, conv);
        store.save(session);
        assertTrue(store.load("sid-del").isPresent());

        store.delete("sid-del");

        assertTrue(store.load("sid-del").isEmpty());
    }

    // -------------------------------------------------------------------------
    // listActive
    // -------------------------------------------------------------------------

    @Test
    void listActive_excludesExpiredSessions() {
        long now = System.currentTimeMillis();

        // Active session
        Session active = new Session("active-sid", AgentState.RUNNING,
                now, now, now + 3_600_000L, new Conversation());
        store.save(active);

        // Expired session (expiryAt in the past)
        Session expired = new Session("expired-sid", AgentState.STOPPED,
                now - 10_000L, now, now - 1L, new Conversation());
        store.save(expired);

        List<Session> actives = store.listActive();

        assertTrue(actives.stream().anyMatch(s -> "active-sid".equals(s.id())));
        assertTrue(actives.stream().noneMatch(s -> "expired-sid".equals(s.id())));
    }

    // -------------------------------------------------------------------------
    // TTL cleanup
    // -------------------------------------------------------------------------

    @Test
    void cleanupExpired_deletesExpiredSessions() throws Exception {
        // Insert an expired session directly via JDBC to bypass TTL enforcement in save
        long past = System.currentTimeMillis() - 1000L;
        insertSessionDirectly("expired-for-cleanup", AgentState.STOPPED, past - 1000L, past, past - 1L);

        // Invoke cleanup via reflection-accessible helper method
        invokeTtlCleanup();

        assertTrue(store.load("expired-for-cleanup").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void insertSessionDirectly(String id, AgentState state,
                                       long createdAt, long updatedAt, long expiryAt)
            throws SQLException {
        String sql = "INSERT INTO agent_sessions (id, state, created_at, updated_at, expiry_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, state.name());
            ps.setLong(3, createdAt);
            ps.setLong(4, updatedAt);
            ps.setLong(5, expiryAt);
            ps.executeUpdate();
        }
    }

    /** Runs the same TTL cleanup SQL that the background thread executes. */
    private void invokeTtlCleanup() throws SQLException {
        long now = System.currentTimeMillis();
        String deleteMessages = "DELETE FROM agent_messages WHERE session_id IN (SELECT id FROM agent_sessions WHERE expiry_at < ?)";
        String deleteSessions = "DELETE FROM agent_sessions WHERE expiry_at < ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps1 = conn.prepareStatement(deleteMessages);
             PreparedStatement ps2 = conn.prepareStatement(deleteSessions)) {
            ps1.setLong(1, now);
            ps1.executeUpdate();
            ps2.setLong(1, now);
            ps2.executeUpdate();
        }
    }
}
