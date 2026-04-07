package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    @Test
    void construction_withAllFields() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");
        meta.put("num", 42);

        Team team = new Team("id-1", "Team Alpha", "A team", TeamStatus.ACTIVE,
                meta, 1000L, 2000L);

        assertEquals("id-1", team.id());
        assertEquals("Team Alpha", team.name());
        assertEquals("A team", team.description());
        assertEquals(TeamStatus.ACTIVE, team.status());
        assertEquals(1000L, team.createdAt());
        assertEquals(2000L, team.updatedAt());
        assertEquals(2, team.metadata().size());
        assertEquals("value", team.metadata().get("key"));
        assertEquals(42, team.metadata().get("num"));
    }

    @Test
    void nullId_allowedBeforeSave() {
        Team team = new Team(null, "Team", null, TeamStatus.ACTIVE, null, 0, 0);
        assertNull(team.id());
    }

    @Test
    void nullMetadata_normalizedToEmptyMap() {
        Team team = new Team("id", "Team", null, TeamStatus.ACTIVE, null, 1000L, 2000L);
        assertNotNull(team.metadata());
        assertTrue(team.metadata().isEmpty());
    }

    @Test
    void defensiveCopy_metadataIsUnmodifiable() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        Team team = new Team("id", "Team", null, TeamStatus.ACTIVE, meta, 1000L, 2000L);

        assertThrows(UnsupportedOperationException.class, () ->
                team.metadata().put("new", "value"));
    }

    @Test
    void defensiveCopy_originalMapMutationDoesNotAffectTeam() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        Team team = new Team("id", "Team", null, TeamStatus.ACTIVE, meta, 1000L, 2000L);

        meta.put("extra", "extra");
        assertEquals(1, team.metadata().size());
        assertFalse(team.metadata().containsKey("extra"));
    }
}
