package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;

public interface SnapshotsRepository {

    BoardSnapshot create(String boardId, String createdBy, String snapshotJson);

    Optional<BoardSnapshot> get(String boardId, long version);

    Optional<BoardSnapshot> getLatest(String boardId);

    List<Long> listVersions(String boardId);
}
