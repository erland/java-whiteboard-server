package info.isaksson.erland.whiteboard.publication;

public enum PublicationState {
    INACTIVE,
    ACTIVE,
    REVOKED,
    EXPIRED;

    public String storageValue() {
        return name().toLowerCase();
    }

    public static PublicationState fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return INACTIVE;
        }
        return PublicationState.valueOf(value.trim().toUpperCase());
    }
}
