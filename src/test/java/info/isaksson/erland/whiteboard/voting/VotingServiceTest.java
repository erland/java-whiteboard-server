package info.isaksson.erland.whiteboard.voting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVoteRecordsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVotingSessionsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;

public class VotingServiceTest {

    private InMemoryBoardsRepository boardsRepository;
    private InMemoryVotingSessionsRepository votingSessionsRepository;
    private InMemoryVoteRecordsRepository voteRecordsRepository;
    private VotingService votingService;
    private String boardId;

    @BeforeEach
    void setup() {
        boardsRepository = new InMemoryBoardsRepository();
        votingSessionsRepository = new InMemoryVotingSessionsRepository();
        voteRecordsRepository = new InMemoryVoteRecordsRepository();
        votingService = new VotingService(votingSessionsRepository, voteRecordsRepository, boardsRepository);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
    }

    @Test
    void creates_opens_closes_and_reveals_voting_session() {
        VotingSession created = votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice", VotingRules.defaults());
        assertEquals(VotingSessionState.DRAFT, created.state());
        assertNotNull(created.createdAt());

        VotingSession open = votingService.openSession(created.id());
        assertEquals(VotingSessionState.OPEN, open.state());
        assertNotNull(open.openedAt());

        VotingSession closed = votingService.closeSession(open.id());
        assertEquals(VotingSessionState.CLOSED, closed.state());
        assertNotNull(closed.closedAt());

        VotingSession revealed = votingService.revealSession(closed.id());
        assertEquals(VotingSessionState.REVEALED, revealed.state());
        assertNotNull(revealed.revealedAt());
    }

    @Test
    void enforces_vote_limits_and_updates() {
        VotingRules rules = new VotingRules(true, false, 3, false, true, true, null);
        VotingSession session = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.SECTION, "section-1", "alice", rules).id());

        votingService.castVote(session.id(), "bob", "viewer", false, "item-1", 2);
        VoteRecord updated = votingService.castVote(session.id(), "bob", "viewer", false, "item-1", 3);
        assertEquals(3, updated.voteValue());

        assertThrows(IllegalArgumentException.class, () ->
                votingService.castVote(session.id(), "bob", "viewer", false, "item-2", 1));
    }

    @Test
    void blocks_updates_when_disabled_and_supports_delete_when_enabled() {
        VotingRules rules = new VotingRules(true, false, 2, true, false, true, null);
        VotingSession session = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.PAGE, "page-1", "alice", rules).id());

        votingService.castVote(session.id(), "bob", "editor", false, "item-1", 1);
        assertTrue(votingService.removeVote(session.id(), "bob", "item-1", false));
        assertTrue(votingService.listVotes(session.id()).isEmpty());

        VotingSession noUpdateSession = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.PAGE, "page-2", "alice",
                        new VotingRules(true, false, 1, true, false, false, null)).id());
        votingService.castVote(noUpdateSession.id(), "bob", "editor", false, "item-1", 1);
        assertThrows(IllegalArgumentException.class, () ->
                votingService.castVote(noUpdateSession.id(), "bob", "editor", false, "item-1", 1));
    }

    @Test
    void hides_progress_and_identities_for_anonymous_sessions() {
        VotingSession session = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice",
                        new VotingRules(true, false, 2, true, false, true, null)).id());
        votingService.castVote(session.id(), "bob", "viewer", false, "item-1", 1);

        VotingResults publicResults = votingService.getResults(session.id());
        assertTrue(publicResults.progressHidden());
        assertTrue(publicResults.identitiesHidden());
        assertTrue(publicResults.totalsByTarget().isEmpty());
        assertTrue(publicResults.visibleVotes().isEmpty());

        VotingResults facilitatorResults = votingService.getResults(session.id(), new BoardAccessService.Access(boardsRepository.findById(boardId).orElseThrow(), BoardAccessService.ROLE_OWNER));
        assertFalse(facilitatorResults.progressHidden());
        assertTrue(facilitatorResults.identitiesHidden());
        assertEquals(1, facilitatorResults.totalsByTarget().get("item-1"));
    }

    @Test
    void rejects_publication_participation_when_policy_disallows_it() {
        VotingSession session = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice",
                        new VotingRules(true, false, 1, true, false, false, null)).id());

        assertThrows(IllegalArgumentException.class, () ->
                votingService.castVote(session.id(), "anon-session", "publication_reader", true, "item-1", 1));
    }

    @Test
    void can_cancel_from_open_but_not_reveal_after_cancel() {
        VotingSession session = votingService.openSession(
                votingService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice", VotingRules.defaults()).id());
        VotingSession cancelled = votingService.cancelSession(session.id());
        assertEquals(VotingSessionState.CANCELLED, cancelled.state());
        assertFalse(cancelled.state().acceptsVotes());
        assertThrows(IllegalArgumentException.class, () -> votingService.revealSession(cancelled.id()));
    }
}
