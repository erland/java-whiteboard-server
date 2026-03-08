package info.isaksson.erland.whiteboard.voting;

public enum VotingScopeType {
    BOARD("board"),
    PAGE("page"),
    SECTION("section"),
    OBJECT_SET("object_set"),
    EXPLICIT_TARGET("explicit_target");

    private final String storageValue;

    VotingScopeType(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static VotingScopeType fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Voting scope type is required");
        }
        for (VotingScopeType candidate : values()) {
            if (candidate.storageValue.equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported voting scope type: " + value);
    }
}
