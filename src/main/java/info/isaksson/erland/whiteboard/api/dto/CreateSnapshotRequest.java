package info.isaksson.erland.whiteboard.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Snapshot payload.
 * The server stores the snapshot as JSON (opaque to the backend in this phase).
 */
public record CreateSnapshotRequest(JsonNode snapshot) {
}
