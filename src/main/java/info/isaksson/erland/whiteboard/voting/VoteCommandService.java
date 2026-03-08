package info.isaksson.erland.whiteboard.voting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import info.isaksson.erland.whiteboard.persistence.VoteRecordsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VoteCommandService {

    private final VoteRecordsRepository voteRecordsRepository;
    private final VotingSessionService votingSessionService;

    @Inject
    public VoteCommandService(VoteRecordsRepository voteRecordsRepository,
                              VotingSessionService votingSessionService) {
        this.voteRecordsRepository = voteRecordsRepository;
        this.votingSessionService = votingSessionService;
    }

    public VoteRecord castVote(String sessionId,
                               String participantId,
                               String participantRole,
                               boolean viaPublication,
                               String targetRef,
                               int voteValue) {
        VotingSession session = votingSessionService.requireSession(sessionId);
        VotingSessionRules.requireAcceptsVotes(session);
        if (!VotingSessionRules.canParticipantVote(participantRole, session.rules(), viaPublication)) {
            throw new IllegalArgumentException("Participant is not allowed to vote in this session");
        }
        String normalizedParticipantId = VotingValidation.normalizeText(participantId, "participantId");
        String normalizedTargetRef = VotingValidation.normalizeText(targetRef, "targetRef");
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
        VotingSession session = votingSessionService.requireSession(sessionId);
        VotingSessionRules.requireAcceptsVotes(session);
        if (!session.rules().allowVoteUpdates() && !updatesAllowedOverride) {
            throw new IllegalArgumentException("Vote removal is not allowed for this session");
        }
        return voteRecordsRepository.deleteForSessionParticipantAndTarget(
                sessionId,
                VotingValidation.normalizeText(participantId, "participantId"),
                VotingValidation.normalizeText(targetRef, "targetRef"));
    }

    public List<VoteRecord> listVotes(String sessionId) {
        votingSessionService.requireSession(sessionId);
        return voteRecordsRepository.listForSession(sessionId);
    }
}
