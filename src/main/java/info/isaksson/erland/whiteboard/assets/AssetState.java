package info.isaksson.erland.whiteboard.assets;

public enum AssetState {
    PENDING("pending"),
    ACTIVE("active"),
    FAILED("failed"),
    DELETED("deleted"),
    QUARANTINED("quarantined");

    private final String storageValue;

    AssetState(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static AssetState fromStorageValue(String value) {
        for (AssetState state : values()) {
            if (state.storageValue.equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown asset state: " + value);
    }
}
