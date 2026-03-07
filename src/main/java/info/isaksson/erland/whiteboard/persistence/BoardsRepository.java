package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.domain.Board;

public interface BoardsRepository {

    Board create(Board board);

    List<Board> listForOwner(String ownerUserId);

    Optional<Board> findById(String id);

    Board updateMetadata(String id, String ownerUserId, String name, String type, String boardType);

    boolean archive(String id, String ownerUserId);
}
