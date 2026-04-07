package org.specdriven.agent.registry;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for team CRUD and member management.
 */
public interface TeamStore {

    /**
     * Creates a team. If the team has no ID, a UUID is generated.
     *
     * @return the team ID
     */
    String create(Team team);

    /**
     * Loads a team by ID.
     */
    Optional<Team> load(String teamId);

    /**
     * Updates a team's name and description.
     *
     * @return the updated team
     * @throws java.util.NoSuchElementException if the team does not exist
     */
    Team update(String teamId, String name, String description);

    /**
     * Transitions a team to DISSOLVED status and removes all members.
     *
     * @throws java.util.NoSuchElementException if the team does not exist
     */
    void dissolve(String teamId);

    /**
     * Returns all non-dissolved teams ordered by createdAt ascending.
     */
    List<Team> list();

    /**
     * Adds a member to a team with the given role.
     *
     * @throws java.util.NoSuchElementException if the team does not exist
     * @throws IllegalStateException if the team is dissolved or the member is already in the team
     */
    void joinTeam(String teamId, String memberId, TeamRole role);

    /**
     * Removes a member from a team.
     *
     * @throws java.util.NoSuchElementException if the team does not exist or the member is not in the team
     * @throws IllegalStateException if the team is dissolved
     */
    void leaveTeam(String teamId, String memberId);

    /**
     * Changes a member's role in a team.
     *
     * @throws java.util.NoSuchElementException if the team does not exist or the member is not in the team
     */
    void updateRole(String teamId, String memberId, TeamRole newRole);

    /**
     * Returns all members of the given team.
     * Returns an empty list for dissolved teams.
     */
    List<TeamMember> listMembers(String teamId);
}
