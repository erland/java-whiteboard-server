package info.isaksson.erland.whiteboard.timer;

public enum SharedTimerState {
    RUNNING("running"),
    PAUSED("paused"),
    CANCELLED("cancelled"),
    COMPLETED("completed");

    private final String storageValue;

    SharedTimerState(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    public static SharedTimerState fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Timer state is required");
        }
        for (SharedTimerState state : values()) {
            if (state.storageValue.equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unsupported timer state: " + value);
    }
}
