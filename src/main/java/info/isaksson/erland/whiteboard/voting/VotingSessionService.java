package info.isaksson.erland.whiteboard.voting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.VotingSessionsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingSessionService {

    private final VotingSessionsRepository votingSessionsRepository;
    private final BoardsRepository boardsRepository;

    @Inject
    public VotingSessionService(VotingSessionsRepository votingSessionsRepository,
                                BoardsRepository boardsRepository) {
        this.votingSessionsRepository = votingSessionsRepository;
        this.boardsRepository = boardsRepository;
    }

    public VotingSession createDraftSession(String boardId,
                                            VotingScopeType scopeType,
                                            String scopeRef,
                                            String createdByUserId,
                                            VotingRules rules) {
        Board board = requireActiveBoard(boardId);
        VotingSessionRules.requireValidScope(scopeType, scopeRef);
        VotingRules effectiveRules = rules == null ? VotingRules.defaults() : rules;
        Instant now = Instant.now();
        String normalizedScopeRef = scopeType == VotingScopeType.BOARD ? board.id() : VotingValidation.normalizeText(scopeRef, "scopeRef");
        VotingSession session = new VotingSession(
                UUID.randomUUID().toString(),
                board.id(),
                scopeType,
                normalizedScopeRef,
                VotingSessionState.DRAFT,
                VotingValidation.normalizeText(createdByUserId, "createdByUserId"),
                effectiveRules,
                now,
                now,
                null,
                null,
                null
        );
        return votingSessionsRepository.create(session);
    }

    public Optional<VotingSession> findSession(String sessionId) {
        return votingSessionsRepository.findById(sessionId);
    }

    public List<VotingSession> listSessionsForBoard(String boardId) {
        return votingSessionsRepository.listForBoard(boardId);
    }

    public VotingSession openSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanOpen(current);
        return updateState(current, VotingSessionState.OPEN);
    }

    public VotingSession closeSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanClose(current);
        return updateState(current, VotingSessionState.CLOSED);
    }

    public VotingSession revealSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanReveal(current);
        return updateState(current, VotingSessionState.REVEALED);
    }

    public VotingSession cancelSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanCancel(current);
        return updateState(current, VotingSessionState.CANCELLED);
    }

    public VotingSession requireSession(String sessionId) {
        return votingSessionsRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Voting session not found"));
    }

    public Board requireActiveBoard(String boardId) {
        return boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
    }

    private VotingSession updateState(VotingSession current, VotingSessionState targetState) {
        Instant now = Instant.now();
        VotingSession updated = new VotingSession(
                current.id(),
                current.boardId(),
                current.scopeType(),
                current.scopeRef(),
                targetState,
                current.createdByUserId(),
                current.rules(),
                current.createdAt(),
                now,
                resolveOpenedAt(current, targetState, now),
                resolveClosedAt(current, targetState, now),
                resolveRevealedAt(current, targetState, now)
        );
        return votingSessionsRepository.update(updated)
                .orElseThrow(() -> new IllegalStateException("Updated voting session not found"));
    }

    private static Instant resolveOpenedAt(VotingSession current, VotingSessionState targetState, Instant now) {
        return targetState == VotingSessionState.OPEN ? now : current.openedAt();
    }

    private static Instant resolveClosedAt(VotingSession current, VotingSessionState targetState, Instant now) {
        return targetState == VotingSessionState.CLOSED ? now : current.closedAt();
    }

    private static Instant resolveRevealedAt(VotingSession current, VotingSessionState targetState, Instant now) {
        if (targetState == VotingSessionState.REVEALED) {
            return now;
        }
        if (targetState == VotingSessionState.CANCELLED) {
            return null;
        }
        return current.revealedAt();
    }
}
