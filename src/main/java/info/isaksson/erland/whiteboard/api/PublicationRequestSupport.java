package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import jakarta.enterprise.context.ApplicationScoped;

import info.isaksson.erland.whiteboard.api.dto.CreatePublicationRequest;
import info.isaksson.erland.whiteboard.publication.PublicationTargetType;

@ApplicationScoped
public class PublicationRequestSupport {

    public PublicationTargetType parseTargetType(CreatePublicationRequest req) {
        String rawValue = req == null ? null : req.targetType();
        if (rawValue == null || rawValue.isBlank()) {
            return PublicationTargetType.BOARD;
        }
        try {
            return PublicationTargetType.fromStorageValue(rawValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'targetType' must be 'board' or 'snapshot'.");
        }
    }

    public Instant parseExpiresAt(CreatePublicationRequest req) {
        String rawValue = req == null ? null : req.expiresAt();
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Field 'expiresAt' must be an ISO-8601 instant (e.g. 2026-01-01T00:00:00Z).");
        }
    }

    public boolean allowComments(CreatePublicationRequest req) {
        return req != null && Boolean.TRUE.equals(req.allowComments());
    }

    public long requireSnapshotVersion(CreatePublicationRequest req) {
        Long snapshotVersion = req == null ? null : req.snapshotVersion();
        if (snapshotVersion == null || snapshotVersion <= 0) {
            throw new IllegalArgumentException("Field 'snapshotVersion' must be a positive integer when targetType is 'snapshot'.");
        }
        return snapshotVersion;
    }
}
