package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.domain.Invite;

public interface InvitesRepository {

    Invite create(Invite invite);

    List<Invite> listForBoard(String boardId);

    Optional<Invite> findById(String inviteId);

    Optional<Invite> findByTokenHash(String tokenHash);

    /**
     * Marks the invite revoked (idempotent).
     */
    boolean revoke(String inviteId);

    /**
     * Increments uses (idempotent behavior is not guaranteed; used for future enhancements).
     * Returns updated invite if found.
     */
    Optional<Invite> incrementUses(String inviteId);
}
