package info.isaksson.erland.whiteboard.publication;

public enum PublicationTargetType {
    BOARD,
    SNAPSHOT;

    public String storageValue() {
        return name().toLowerCase();
    }

    public static PublicationTargetType fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return BOARD;
        }
        return PublicationTargetType.valueOf(value.trim().toUpperCase());
    }
}
