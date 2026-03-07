package info.isaksson.erland.whiteboard.domain;

import info.isaksson.erland.whiteboard.api.dto.CreateBoardRequest;
import info.isaksson.erland.whiteboard.api.dto.UpdateBoardRequest;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BoardMetadataRules {

    public static final String DEFAULT_BOARD_KIND = "whiteboard";
    public static final String DEFAULT_BOARD_TYPE = "advanced";
    public static final String STATUS_ACTIVE = "active";

    public record NormalizedCreate(String name, String type, String boardType) {
    }

    public record NormalizedUpdate(String name, String type, String boardType) {
    }

    public NormalizedCreate normalizeCreate(CreateBoardRequest req) {
        String rawName = req == null ? null : req.name();
        String rawType = req == null ? null : req.type();
        String rawBoardType = req == null ? null : req.boardType();

        String name = trimToNull(rawName);
        if (name == null) {
            throw validation("Field 'name' is required.");
        }
        if (rawType == null || rawType.isBlank()) {
            throw validation("Field 'type' is required.");
        }

        String type = normalizeBoardKind(rawType);
        String boardType = normalizeBoardType(rawBoardType, rawType);
        if (boardType == null || boardType.isBlank()) {
            throw validation("Field 'boardType' is required.");
        }

        return new NormalizedCreate(name, type, boardType);
    }

    public NormalizedUpdate normalizeUpdate(UpdateBoardRequest req, Board existing) {
        requireActive(existing);

        String rawName = req == null ? null : req.name();
        String newName = trimToNull(rawName);
        if (newName == null) {
            newName = existing.name();
        }

        String newType = normalizeBoardKind(req == null ? null : req.type());
        if (newType == null || newType.isBlank()) {
            newType = existing.type();
        }

        String newBoardType = normalizeBoardType(req == null ? null : req.boardType(), req == null ? null : req.type());
        if (newBoardType == null || newBoardType.isBlank()) {
            newBoardType = existing.boardType();
        }

        return new NormalizedUpdate(newName, newType, newBoardType);
    }

    public void requireActive(Board board) {
        if (board == null || !STATUS_ACTIVE.equals(board.status())) {
            throw new BoardNotActiveException();
        }
    }

    public String normalizeBoardKind(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return DEFAULT_BOARD_KIND;
        }
        return DEFAULT_BOARD_KIND;
    }

    public String normalizeBoardType(String requestedBoardType, String requestedType) {
        if (requestedBoardType != null && !requestedBoardType.isBlank()) {
            return requestedBoardType.trim();
        }
        if (requestedType != null && !requestedType.isBlank()) {
            String trimmedType = requestedType.trim();
            if (!DEFAULT_BOARD_KIND.equals(trimmedType)) {
                return trimmedType;
            }
        }
        return DEFAULT_BOARD_TYPE;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ValidationException validation(String message) {
        return new ValidationException(message);
    }

    public static final class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static final class BoardNotActiveException extends RuntimeException {
        public BoardNotActiveException() {
            super("Board is not active.");
        }
    }
}
