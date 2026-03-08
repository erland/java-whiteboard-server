package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.voting.VoteRecord;

public interface VoteRecordsRepository {

    VoteRecord create(VoteRecord voteRecord);

    Optional<VoteRecord> update(VoteRecord voteRecord);

    List<VoteRecord> listForSession(String sessionId);

    List<VoteRecord> listForSessionAndParticipant(String sessionId, String participantId);

    boolean deleteForSessionParticipantAndTarget(String sessionId, String participantId, String targetRef);
}
