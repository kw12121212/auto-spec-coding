package org.specdriven.agent.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class LealoneTeamStoreTest {

    private LealoneTeamStore store;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        String dbName = "test_teams_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        eventBus = new CapturingEventBus();
        store = new LealoneTeamStore(eventBus, jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // create → load round-trip
    // -------------------------------------------------------------------------

    @Test
    void createAndLoad_returnsEquivalentTeam() {
        Team team = new Team(null, "Alpha", "A team", TeamStatus.ACTIVE,
                Map.of("project", "sdk"), 0, 0);
        String id = store.create(team);

        Optional<Team> loaded = store.load(id);
        assertTrue(loaded.isPresent());
        Team t = loaded.get();
        assertEquals("Alpha", t.name());
        assertEquals("A team", t.description());
        assertEquals(TeamStatus.ACTIVE, t.status());
        assertEquals("sdk", t.metadata().get("project"));
        assertNotNull(t.id());
        assertFalse(t.id().isBlank());
        assertTrue(t.createdAt() > 0);
        assertTrue(t.updatedAt() > 0);
    }

    @Test
    void create_withNullId_generatesUuid() {
        Team team = new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0);
        String id = store.create(team);
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertTrue(store.load(id).isPresent());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_nameAndDescription() {
        String id = store.create(new Team(null, "Old Name", "Old Desc",
                TeamStatus.ACTIVE, null, 0, 0));

        Team updated = store.update(id, "New Name", "New Desc");

        assertEquals("New Name", updated.name());
        assertEquals("New Desc", updated.description());
        assertEquals(TeamStatus.ACTIVE, updated.status());
        assertTrue(updated.updatedAt() >= updated.createdAt());
    }

    // -------------------------------------------------------------------------
    // dissolve
    // -------------------------------------------------------------------------

    @Test
    void dissolve_transitionsToDissolved() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.joinTeam(id, "member-1", TeamRole.MEMBER);

        store.dissolve(id);

        Team loaded = store.load(id).orElseThrow();
        assertEquals(TeamStatus.DISSOLVED, loaded.status());
        // Members removed
        assertTrue(store.listMembers(id).isEmpty());
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void list_emptyStore_returnsEmptyList() {
        assertTrue(store.list().isEmpty());
    }

    @Test
    void list_excludesDissolved() {
        store.create(new Team(null, "Active", null, TeamStatus.ACTIVE, null, 0, 0));
        String toDissolve = store.create(new Team(null, "ToDissolve", null, TeamStatus.ACTIVE, null, 0, 0));
        store.dissolve(toDissolve);

        List<Team> teams = store.list();
        assertEquals(1, teams.size());
        assertEquals("Active", teams.get(0).name());
    }

    // -------------------------------------------------------------------------
    // Member operations
    // -------------------------------------------------------------------------

    @Test
    void joinTeam_addsMember() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));

        store.joinTeam(id, "agent-1", TeamRole.LEAD);

        List<TeamMember> members = store.listMembers(id);
        assertEquals(1, members.size());
        assertEquals("agent-1", members.get(0).memberId());
        assertEquals(TeamRole.LEAD, members.get(0).role());
        assertEquals(id, members.get(0).teamId());
    }

    @Test
    void joinTeam_multipleMembers() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));

        store.joinTeam(id, "agent-1", TeamRole.LEAD);
        store.joinTeam(id, "agent-2", TeamRole.MEMBER);
        store.joinTeam(id, "agent-3", TeamRole.MEMBER);

        assertEquals(3, store.listMembers(id).size());
    }

    @Test
    void joinTeam_duplicateMember_throwsIllegalStateException() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.joinTeam(id, "agent-1", TeamRole.MEMBER);

        assertThrows(IllegalStateException.class,
                () -> store.joinTeam(id, "agent-1", TeamRole.LEAD));
    }

    @Test
    void joinTeam_dissolvedTeam_throwsIllegalStateException() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.dissolve(id);

        assertThrows(IllegalStateException.class,
                () -> store.joinTeam(id, "agent-1", TeamRole.MEMBER));
    }

    @Test
    void leaveTeam_removesMember() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.joinTeam(id, "agent-1", TeamRole.MEMBER);

        store.leaveTeam(id, "agent-1");

        assertTrue(store.listMembers(id).isEmpty());
    }

    @Test
    void leaveTeam_nonMember_throwsNoSuchElementException() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));

        assertThrows(NoSuchElementException.class,
                () -> store.leaveTeam(id, "agent-1"));
    }

    @Test
    void leaveTeam_dissolvedTeam_throwsIllegalStateException() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.dissolve(id);

        assertThrows(IllegalStateException.class,
                () -> store.leaveTeam(id, "agent-1"));
    }

    @Test
    void updateRole_changesMemberRole() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.joinTeam(id, "agent-1", TeamRole.MEMBER);

        store.updateRole(id, "agent-1", TeamRole.LEAD);

        List<TeamMember> members = store.listMembers(id);
        assertEquals(1, members.size());
        assertEquals(TeamRole.LEAD, members.get(0).role());
    }

    @Test
    void updateRole_nonMember_throwsNoSuchElementException() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));

        assertThrows(NoSuchElementException.class,
                () -> store.updateRole(id, "agent-1", TeamRole.LEAD));
    }

    @Test
    void listMembers_dissolvedTeam_returnsEmptyList() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        store.joinTeam(id, "agent-1", TeamRole.MEMBER);
        store.dissolve(id);

        assertTrue(store.listMembers(id).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void load_nonExistent_returnsEmpty() {
        assertTrue(store.load("nonexistent").isEmpty());
    }

    @Test
    void dissolve_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> store.dissolve("nonexistent"));
    }

    @Test
    void update_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class,
                () -> store.update("nonexistent", "name", "desc"));
    }

    @Test
    void joinTeam_nonExistentTeam_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class,
                () -> store.joinTeam("nonexistent", "agent-1", TeamRole.MEMBER));
    }

    // -------------------------------------------------------------------------
    // EventBus integration
    // -------------------------------------------------------------------------

    @Test
    void create_newTeam_publishesTeamCreated() {
        store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));

        assertEquals(1, eventBus.captured.size());
        Event event = eventBus.captured.get(0);
        assertEquals(EventType.TEAM_CREATED, event.type());
        assertEquals("TeamStore", event.source());
        assertNotNull(event.metadata().get("teamId"));
    }

    @Test
    void dissolve_publishesTeamDissolved() {
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0));
        eventBus.captured.clear();

        store.dissolve(id);

        assertEquals(1, eventBus.captured.size());
        Event event = eventBus.captured.get(0);
        assertEquals(EventType.TEAM_DISSOLVED, event.type());
        assertEquals(id, event.metadata().get("teamId"));
    }

    // -------------------------------------------------------------------------
    // Full CRUD lifecycle
    // -------------------------------------------------------------------------

    @Test
    void fullCrudLifecycle() {
        // Create
        String id = store.create(new Team(null, "Alpha Team", "Desc",
                TeamStatus.ACTIVE, Map.of("env", "prod"), 0, 0));
        assertNotNull(id);

        // Load
        Team loaded = store.load(id).orElseThrow();
        assertEquals(TeamStatus.ACTIVE, loaded.status());
        assertEquals("Alpha Team", loaded.name());

        // Update
        Team updated = store.update(id, "Beta Team", "New desc");
        assertEquals("Beta Team", updated.name());
        assertEquals("New desc", updated.description());
        assertEquals(TeamStatus.ACTIVE, updated.status());

        // Add members
        store.joinTeam(id, "agent-1", TeamRole.LEAD);
        store.joinTeam(id, "agent-2", TeamRole.MEMBER);
        assertEquals(2, store.listMembers(id).size());

        // Update role
        store.updateRole(id, "agent-2", TeamRole.LEAD);
        assertEquals(TeamRole.LEAD, store.listMembers(id).get(1).role());

        // Member leaves
        store.leaveTeam(id, "agent-2");
        assertEquals(1, store.listMembers(id).size());

        // Dissolve
        store.dissolve(id);
        Team dissolved = store.load(id).orElseThrow();
        assertEquals(TeamStatus.DISSOLVED, dissolved.status());
        assertTrue(store.listMembers(id).isEmpty());
        assertTrue(store.list().stream().noneMatch(t -> t.id().equals(id)));

        // DISSOLVED is terminal
        assertThrows(IllegalStateException.class, () -> {
            // Try to dissolve again — same state transition
            TeamStatus.validateTransition(TeamStatus.DISSOLVED, TeamStatus.DISSOLVED);
        });
    }

    // -------------------------------------------------------------------------
    // Metadata serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    void metadataSerialization_roundTrip() {
        Map<String, Object> meta = Map.of("string", "value", "number", 42, "bool", true);
        String id = store.create(new Team(null, "Team", null, TeamStatus.ACTIVE, meta, 0, 0));

        Team loaded = store.load(id).orElseThrow();
        assertEquals("value", loaded.metadata().get("string"));
        assertEquals(42, loaded.metadata().get("number"));
        assertEquals(true, loaded.metadata().get("bool"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static class CapturingEventBus implements EventBus {
        final List<Event> captured = new ArrayList<>();

        @Override
        public void publish(Event event) {
            captured.add(event);
        }

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {
        }

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {
        }
    }
}
