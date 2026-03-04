package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Board access grants for authenticated users.
 *
 * Roles are stored as strings to keep storage simple and compatible with clients.
 * Expected values: "viewer" or "editor".
 */
public interface BoardPermissionsRepository {

    void upsert(String boardId, String userId, String role);

    Optional<String> findRole(String boardId, String userId);

    List<String> listBoardIdsForUser(String userId);
}
