package org.specdriven.agent.registry;

/**
 * An immutable team membership entry.
 *
 * @param teamId   the team this member belongs to
 * @param memberId unique member identifier (agent ID or user ID)
 * @param role     the member's role in the team
 * @param joinedAt epoch millis when the member joined
 */
public record TeamMember(
        String teamId,
        String memberId,
        TeamRole role,
        long joinedAt
) {
}
