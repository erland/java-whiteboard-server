package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import info.isaksson.erland.whiteboard.voting.VoteRecord;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryVoteRecordsRepository implements VoteRecordsRepository {

    private final ConcurrentHashMap<String, VoteRecord> votes = new ConcurrentHashMap<>();

    public void clear() {
        votes.clear();
    }

    @Override
    public VoteRecord create(VoteRecord voteRecord) {
        Instant now = Instant.now();
        VoteRecord created = new VoteRecord(
                voteRecord.id(), voteRecord.sessionId(), voteRecord.participantId(), voteRecord.targetRef(), voteRecord.voteValue(),
                voteRecord.createdAt() == null ? now : voteRecord.createdAt(),
                voteRecord.updatedAt() == null ? now : voteRecord.updatedAt());
        votes.put(created.id(), created);
        return created;
    }

    @Override
    public Optional<VoteRecord> update(VoteRecord voteRecord) {
        return Optional.ofNullable(votes.computeIfPresent(voteRecord.id(), (id, existing) -> voteRecord));
    }

    @Override
    public List<VoteRecord> listForSession(String sessionId) {
        return votes.values().stream()
                .filter(vote -> vote.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(VoteRecord::createdAt).thenComparing(VoteRecord::id))
                .toList();
    }

    @Override
    public List<VoteRecord> listForSessionAndParticipant(String sessionId, String participantId) {
        return votes.values().stream()
                .filter(vote -> vote.sessionId().equals(sessionId))
                .filter(vote -> vote.participantId().equals(participantId))
                .sorted(Comparator.comparing(VoteRecord::createdAt).thenComparing(VoteRecord::id))
                .toList();
    }

    @Override
    public boolean deleteForSessionParticipantAndTarget(String sessionId, String participantId, String targetRef) {
        Optional<String> key = votes.values().stream()
                .filter(vote -> vote.sessionId().equals(sessionId))
                .filter(vote -> vote.participantId().equals(participantId))
                .filter(vote -> vote.targetRef().equals(targetRef))
                .map(VoteRecord::id)
                .findFirst();
        key.ifPresent(votes::remove);
        return key.isPresent();
    }
}
