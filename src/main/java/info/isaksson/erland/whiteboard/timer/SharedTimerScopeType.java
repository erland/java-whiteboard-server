package info.isaksson.erland.whiteboard.timer;

public enum SharedTimerScopeType {
    BOARD("board"),
    PAGE("page"),
    SECTION("section");

    private final String storageValue;

    SharedTimerScopeType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static SharedTimerScopeType fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return BOARD;
        }
        for (SharedTimerScopeType type : values()) {
            if (type.storageValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported timer scope type: " + value);
    }
}
