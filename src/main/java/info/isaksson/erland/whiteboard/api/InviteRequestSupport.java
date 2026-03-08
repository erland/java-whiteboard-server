package info.isaksson.erland.whiteboard.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import jakarta.enterprise.context.ApplicationScoped;

import info.isaksson.erland.whiteboard.api.dto.CreateInviteRequest;

@ApplicationScoped
public class InviteRequestSupport {

    public String requirePermission(CreateInviteRequest req) {
        String permission = req == null ? null : req.permission();
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("Field 'permission' is required.");
        }
        permission = permission.trim();
        if (!permission.equals("viewer") && !permission.equals("editor")) {
            throw new IllegalArgumentException("Field 'permission' must be 'viewer' or 'editor'.");
        }
        return permission;
    }

    public Instant parseExpiresAt(CreateInviteRequest req) {
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

    public Integer parseMaxUses(CreateInviteRequest req) {
        Integer maxUses = req == null ? null : req.maxUses();
        if (maxUses != null && maxUses <= 0) {
            throw new IllegalArgumentException("Field 'maxUses' must be a positive integer.");
        }
        return maxUses;
    }
}
