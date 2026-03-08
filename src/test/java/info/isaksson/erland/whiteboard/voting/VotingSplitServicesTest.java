package info.isaksson.erland.whiteboard.voting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.InMemoryBoardsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVoteRecordsRepository;
import info.isaksson.erland.whiteboard.persistence.InMemoryVotingSessionsRepository;
import info.isaksson.erland.whiteboard.security.BoardAccessService;

class VotingSplitServicesTest {

    private InMemoryBoardsRepository boardsRepository;
    private InMemoryVotingSessionsRepository votingSessionsRepository;
    private InMemoryVoteRecordsRepository voteRecordsRepository;
    private VotingSessionService votingSessionService;
    private VoteCommandService voteCommandService;
    private VotingResultsService votingResultsService;
    private String boardId;

    @BeforeEach
    void setup() {
        boardsRepository = new InMemoryBoardsRepository();
        votingSessionsRepository = new InMemoryVotingSessionsRepository();
        voteRecordsRepository = new InMemoryVoteRecordsRepository();
        votingSessionService = new VotingSessionService(votingSessionsRepository, boardsRepository);
        voteCommandService = new VoteCommandService(voteRecordsRepository, votingSessionService);
        votingResultsService = new VotingResultsService(voteRecordsRepository, votingSessionService);
        boardId = UUID.randomUUID().toString();
        boardsRepository.create(new Board(boardId, "Board", "whiteboard", "advanced", "alice", "active", null, null));
    }

    @Test
    void session_service_handles_lifecycle_transitions() {
        VotingSession created = votingSessionService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice", VotingRules.defaults());
        assertEquals(VotingSessionState.DRAFT, created.state());

        VotingSession opened = votingSessionService.openSession(created.id());
        VotingSession closed = votingSessionService.closeSession(opened.id());
        VotingSession revealed = votingSessionService.revealSession(closed.id());

        assertEquals(VotingSessionState.REVEALED, revealed.state());
        assertTrue(revealed.openedAt() != null);
        assertTrue(revealed.closedAt() != null);
        assertTrue(revealed.revealedAt() != null);
    }

    @Test
    void command_and_results_services_work_together() {
        VotingSession session = votingSessionService.openSession(
                votingSessionService.createDraftSession(boardId, VotingScopeType.BOARD, boardId, "alice",
                        new VotingRules(true, false, 3, false, true, true, null)).id());

        voteCommandService.castVote(session.id(), "bob", "viewer", false, "item-1", 1);
        voteCommandService.castVote(session.id(), "bob", "viewer", false, "item-2", 2);

        VotingResults facilitatorResults = votingResultsService.getResults(session.id(),
                new BoardAccessService.Access(boardsRepository.findById(boardId).orElseThrow(), BoardAccessService.ROLE_OWNER));
        assertEquals(1, facilitatorResults.totalsByTarget().get("item-1"));
        assertEquals(2, facilitatorResults.totalsByTarget().get("item-2"));
        assertTrue(facilitatorResults.visibleVotes().size() == 2);
    }
}
