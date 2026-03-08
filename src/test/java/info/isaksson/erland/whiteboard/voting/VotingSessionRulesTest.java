package info.isaksson.erland.whiteboard.voting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class VotingSessionRulesTest {

    @Test
    void rejects_blank_scope_ref_for_non_board_scope() {
        assertThrows(IllegalArgumentException.class, () ->
                VotingSessionRules.requireValidScope(VotingScopeType.SECTION, " "));
    }

    @Test
    void supports_participant_policy_checks() {
        VotingRules rules = new VotingRules(true, false, 3, true, false, true, null);
        assertTrue(VotingSessionRules.canParticipantVote("owner", rules, false));
        assertTrue(VotingSessionRules.canParticipantVote("viewer", rules, false));
        assertFalse(VotingSessionRules.canParticipantVote("publication_reader", rules, true));
    }

    @Test
    void rejects_invalid_reveal_transition() {
        VotingSession session = new VotingSession(
                "s1", "b1", VotingScopeType.BOARD, "b1", VotingSessionState.DRAFT, "alice", VotingRules.defaults(),
                Instant.now(), Instant.now(), null, null, null);
        assertThrows(IllegalArgumentException.class, () -> VotingSessionRules.requireCanReveal(session));
    }
}
