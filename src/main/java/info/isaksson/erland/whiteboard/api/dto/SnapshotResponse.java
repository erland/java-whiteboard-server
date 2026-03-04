package info.isaksson.erland.whiteboard.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.isaksson.erland.whiteboard.domain.BoardSnapshot;

public record SnapshotResponse(
        String boardId,
        long version,
        JsonNode snapshot,
        Instant createdAt,
        String createdBy
) {
    public static SnapshotResponse from(BoardSnapshot s, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(s.snapshotJson());
            return new SnapshotResponse(s.boardId(), s.version(), node, s.createdAt(), s.createdBy());
        } catch (Exception e) {
            throw new IllegalStateException("Stored snapshot is not valid JSON", e);
        }
    }
}
