package info.isaksson.erland.whiteboard.timer;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.isaksson.erland.whiteboard.persistence.BoardsRepository;
import info.isaksson.erland.whiteboard.persistence.TimersRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SharedTimerService {

    private final SharedTimerCommandService commandService;
    private final SharedTimerPayloadMapper payloadMapper;

    @Inject
    public SharedTimerService(SharedTimerCommandService commandService,
                              SharedTimerPayloadMapper payloadMapper) {
        this.commandService = commandService;
        this.payloadMapper = payloadMapper;
    }

    public SharedTimerService(TimersRepository timersRepository,
                              BoardsRepository boardsRepository,
                              ObjectMapper mapper) {
        SharedTimerStateCalculator stateCalculator = new SharedTimerStateCalculator(timersRepository);
        this.commandService = new SharedTimerCommandService(timersRepository, boardsRepository, stateCalculator);
        this.payloadMapper = new SharedTimerPayloadMapper(mapper, stateCalculator);
    }

    public SharedTimerService(TimersRepository timersRepository, BoardsRepository boardsRepository) {
        this(timersRepository, boardsRepository, new ObjectMapper());
    }

    public SharedTimer applyControl(String boardId, String actorUserId, JsonNode payload) {
        return commandService.applyControl(boardId, actorUserId, payload);
    }

    public Optional<SharedTimer> current(String boardId) {
        return commandService.current(boardId);
    }

    public JsonNode toPayload(SharedTimer timer) {
        return payloadMapper.toPayload(timer);
    }
}
