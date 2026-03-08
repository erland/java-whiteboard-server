package info.isaksson.erland.whiteboard.assets;

public enum AssetScopeType {
    BOARD("board"),
    USER_PRIVATE("user_private"),
    LIBRARY("library");

    private final String storageValue;

    AssetScopeType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static AssetScopeType fromStorageValue(String value) {
        for (AssetScopeType type : values()) {
            if (type.storageValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown asset scope type: " + value);
    }
}
