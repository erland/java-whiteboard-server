package info.isaksson.erland.whiteboard.comments;

public enum CommentTargetType {
    BOARD("board"),
    OBJECT("object"),
    REGION("region"),
    COMMENT("comment");

    private final String storageValue;

    CommentTargetType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static CommentTargetType fromStorageValue(String storageValue) {
        if (storageValue == null) {
            throw new IllegalArgumentException("Comment target type is required");
        }
        for (CommentTargetType value : values()) {
            if (value.storageValue.equalsIgnoreCase(storageValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported comment target type: " + storageValue);
    }
}
