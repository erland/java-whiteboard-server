package info.isaksson.erland.whiteboard.comments;

public enum CommentState {
    ACTIVE("active"),
    RESOLVED("resolved"),
    DELETED("deleted");

    private final String storageValue;

    CommentState(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static CommentState fromStorageValue(String storageValue) {
        if (storageValue == null) {
            throw new IllegalArgumentException("Comment state is required");
        }
        for (CommentState value : values()) {
            if (value.storageValue.equalsIgnoreCase(storageValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported comment state: " + storageValue);
    }
}
