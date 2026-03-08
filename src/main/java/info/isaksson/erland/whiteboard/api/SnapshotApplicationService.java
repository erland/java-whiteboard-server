package info.isaksson.erland.whiteboard.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import info.isaksson.erland.whiteboard.api.dto.CreateSnapshotRequest;
import info.isaksson.erland.whiteboard.api.dto.SnapshotResponse;
import info.isaksson.erland.whiteboard.api.dto.SnapshotVersionsResponse;
import info.isaksson.erland.whiteboard.domain.BoardSnapshot;
import info.isaksson.erland.whiteboard.persistence.SnapshotsRepository;
import info.isaksson.erland.whiteboard.security.BoardGuards;

@ApplicationScoped
public class SnapshotApplicationService {

    private final BoardGuards boardGuards;
    private final SnapshotsRepository snapshotsRepository;
    private final ObjectMapper mapper;

    public SnapshotApplicationService(BoardGuards boardGuards,
                                      SnapshotsRepository snapshotsRepository,
                                      ObjectMapper mapper) {
        this.boardGuards = boardGuards;
        this.snapshotsRepository = snapshotsRepository;
        this.mapper = mapper;
    }

    public SnapshotResponse create(String boardId, String userId, CreateSnapshotRequest req, long maxSnapshotBytes) {
        boardGuards.requireWritableAccess(boardId, userId);

        JsonNode snapshotNode = req == null ? null : req.snapshot();
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new SnapshotValidationException("Field 'snapshot' is required.");
        }

        String snapshotJson;
        try {
            snapshotJson = mapper.writeValueAsString(snapshotNode);
        } catch (Exception e) {
            throw new SnapshotValidationException("Field 'snapshot' must be valid JSON.");
        }

        int sizeBytes = snapshotJson.getBytes(StandardCharsets.UTF_8).length;
        if (sizeBytes > maxSnapshotBytes) {
            throw new SnapshotTooLargeException(maxSnapshotBytes);
        }

        BoardSnapshot created = snapshotsRepository.create(boardId, userId, snapshotJson);
        return SnapshotResponse.from(created, mapper);
    }

    public SnapshotResponse latest(String boardId, String userId) {
        boardGuards.requireReadableAccess(boardId, userId);
        BoardSnapshot latest = snapshotsRepository.getLatest(boardId).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(latest, mapper);
    }

    public SnapshotResponse get(String boardId, long version, String userId) {
        boardGuards.requireReadableAccess(boardId, userId);
        BoardSnapshot snapshot = snapshotsRepository.get(boardId, version).orElseThrow(NotFoundException::new);
        return SnapshotResponse.from(snapshot, mapper);
    }

    public SnapshotVersionsResponse versions(String boardId, String userId) {
        boardGuards.requireReadableAccess(boardId, userId);
        List<Long> versions = snapshotsRepository.listVersions(boardId);
        return new SnapshotVersionsResponse(versions);
    }

    public static final class SnapshotValidationException extends RuntimeException {
        public SnapshotValidationException(String message) {
            super(message);
        }
    }

    public static final class SnapshotTooLargeException extends RuntimeException {
        private final long maxBytes;

        public SnapshotTooLargeException(long maxBytes) {
            super("Snapshot exceeds max size of " + maxBytes + " bytes.");
            this.maxBytes = maxBytes;
        }

        public long maxBytes() {
            return maxBytes;
        }
    }
}
