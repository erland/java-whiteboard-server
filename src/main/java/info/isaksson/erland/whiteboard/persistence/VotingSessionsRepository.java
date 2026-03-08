package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.voting.VotingSession;

public interface VotingSessionsRepository {

    VotingSession create(VotingSession session);

    Optional<VotingSession> update(VotingSession session);

    Optional<VotingSession> findById(String sessionId);

    List<VotingSession> listForBoard(String boardId);
}
