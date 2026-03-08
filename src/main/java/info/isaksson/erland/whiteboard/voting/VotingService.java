package info.isaksson.erland.whiteboard.voting;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.domain.Board;
import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.VoteRecordsRepository;
import info.isaksson.erland.whiteboard.persistence.VotingSessionsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingService {

    private final VotingSessionsRepository votingSessionsRepository;
    private final VoteRecordsRepository voteRecordsRepository;
    private final BoardsRepository boardsRepository;

    @Inject
    public VotingService(VotingSessionsRepository votingSessionsRepository,
                         VoteRecordsRepository voteRecordsRepository,
                         BoardsRepository boardsRepository) {
        this.votingSessionsRepository = votingSessionsRepository;
        this.voteRecordsRepository = voteRecordsRepository;
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
        String normalizedScopeRef = scopeType == VotingScopeType.BOARD ? board.id() : normalizeText(scopeRef, "scopeRef");
        VotingSession session = new VotingSession(
                UUID.randomUUID().toString(),
                board.id(),
                scopeType,
                normalizedScopeRef,
                VotingSessionState.DRAFT,
                normalizeText(createdByUserId, "createdByUserId"),
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
        Instant now = Instant.now();
        return votingSessionsRepository.update(new VotingSession(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), VotingSessionState.OPEN,
                current.createdByUserId(), current.rules(), current.createdAt(), now, now, null, null))
                .orElseThrow(() -> new IllegalStateException("Updated voting session not found"));
    }

    public VotingSession closeSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanClose(current);
        Instant now = Instant.now();
        return votingSessionsRepository.update(new VotingSession(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), VotingSessionState.CLOSED,
                current.createdByUserId(), current.rules(), current.createdAt(), now, current.openedAt(), now, null))
                .orElseThrow(() -> new IllegalStateException("Updated voting session not found"));
    }

    public VotingSession revealSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanReveal(current);
        Instant now = Instant.now();
        return votingSessionsRepository.update(new VotingSession(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), VotingSessionState.REVEALED,
                current.createdByUserId(), current.rules(), current.createdAt(), now, current.openedAt(), current.closedAt(), now))
                .orElseThrow(() -> new IllegalStateException("Updated voting session not found"));
    }

    public VotingSession cancelSession(String sessionId) {
        VotingSession current = requireSession(sessionId);
        VotingSessionRules.requireCanCancel(current);
        Instant now = Instant.now();
        return votingSessionsRepository.update(new VotingSession(
                current.id(), current.boardId(), current.scopeType(), current.scopeRef(), VotingSessionState.CANCELLED,
                current.createdByUserId(), current.rules(), current.createdAt(), now, current.openedAt(), current.closedAt(), null))
                .orElseThrow(() -> new IllegalStateException("Updated voting session not found"));
    }

    public VoteRecord castVote(String sessionId,
                               String participantId,
                               String participantRole,
                               boolean viaPublication,
                               String targetRef,
                               int voteValue) {
        VotingSession session = requireSession(sessionId);
        VotingSessionRules.requireAcceptsVotes(session);
        if (!VotingSessionRules.canParticipantVote(participantRole, session.rules(), viaPublication)) {
            throw new IllegalArgumentException("Participant is not allowed to vote in this session");
        }
        String normalizedParticipantId = normalizeText(participantId, "participantId");
        String normalizedTargetRef = normalizeText(targetRef, "targetRef");
        if (voteValue <= 0) {
            throw new IllegalArgumentException("voteValue must be greater than zero");
        }
        if (voteValue > session.rules().maxVotesPerParticipant()) {
            throw new IllegalArgumentException("voteValue exceeds maxVotesPerParticipant");
        }

        List<VoteRecord> existingVotes = voteRecordsRepository.listForSessionAndParticipant(sessionId, normalizedParticipantId);
        Optional<VoteRecord> existingForTarget = existingVotes.stream()
                .filter(vote -> vote.targetRef().equals(normalizedTargetRef))
                .findFirst();
        int otherVotes = existingVotes.stream()
                .filter(vote -> existingForTarget.isEmpty() || !vote.id().equals(existingForTarget.get().id()))
                .mapToInt(VoteRecord::voteValue)
                .sum();
        if (otherVotes + voteValue > session.rules().maxVotesPerParticipant()) {
            throw new IllegalArgumentException("Vote limit exceeded");
        }

        Instant now = Instant.now();
        if (existingForTarget.isPresent()) {
            if (!session.rules().allowVoteUpdates()) {
                throw new IllegalArgumentException("Vote updates are not allowed for this session");
            }
            VoteRecord current = existingForTarget.get();
            VoteRecord updated = new VoteRecord(
                    current.id(), current.sessionId(), current.participantId(), current.targetRef(), voteValue,
                    current.createdAt(), now);
            return voteRecordsRepository.update(updated).orElseThrow(() -> new IllegalStateException("Updated vote not found"));
        }

        VoteRecord created = new VoteRecord(
                UUID.randomUUID().toString(),
                sessionId,
                normalizedParticipantId,
                normalizedTargetRef,
                voteValue,
                now,
                now
        );
        return voteRecordsRepository.create(created);
    }

    public boolean removeVote(String sessionId,
                              String participantId,
                              String targetRef,
                              boolean updatesAllowedOverride) {
        VotingSession session = requireSession(sessionId);
        VotingSessionRules.requireAcceptsVotes(session);
        if (!session.rules().allowVoteUpdates() && !updatesAllowedOverride) {
            throw new IllegalArgumentException("Vote removal is not allowed for this session");
        }
        return voteRecordsRepository.deleteForSessionParticipantAndTarget(
                sessionId,
                normalizeText(participantId, "participantId"),
                normalizeText(targetRef, "targetRef"));
    }

    public List<VoteRecord> listVotes(String sessionId) {
        requireSession(sessionId);
        return voteRecordsRepository.listForSession(sessionId);
    }

    public VotingResults getResults(String sessionId) {
        VotingSession session = requireSession(sessionId);
        List<VoteRecord> votes = voteRecordsRepository.listForSession(sessionId);
        Map<String, Integer> totalsByTarget = new LinkedHashMap<>();
        for (VoteRecord vote : votes) {
            totalsByTarget.merge(vote.targetRef(), vote.voteValue(), Integer::sum);
        }
        boolean progressHidden = session.state() == VotingSessionState.OPEN && !session.rules().showProgressDuringVoting();
        boolean identitiesHidden = session.rules().anonymousVotes();
        List<VoteRecord> visibleVotes = identitiesHidden || progressHidden ? List.of() : votes;
        return new VotingResults(session, progressHidden ? Map.of() : totalsByTarget, visibleVotes, identitiesHidden, progressHidden);
    }

    private VotingSession requireSession(String sessionId) {
        return votingSessionsRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Voting session not found"));
    }

    private Board requireActiveBoard(String boardId) {
        return boardsRepository.findById(boardId)
                .filter(board -> "active".equals(board.status()))
                .orElseThrow(() -> new IllegalArgumentException("Board not found or not active"));
    }

    private static String normalizeText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
