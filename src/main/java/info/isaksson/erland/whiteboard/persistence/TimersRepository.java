package info.isaksson.erland.whiteboard.persistence;

import java.util.List;
import java.util.Optional;

import info.isaksson.erland.whiteboard.timer.SharedTimer;

public interface TimersRepository {

    SharedTimer create(SharedTimer timer);

    Optional<SharedTimer> update(SharedTimer timer);

    Optional<SharedTimer> findById(String timerId);

    Optional<SharedTimer> findActiveForBoard(String boardId);

    List<SharedTimer> listForBoard(String boardId);
}
