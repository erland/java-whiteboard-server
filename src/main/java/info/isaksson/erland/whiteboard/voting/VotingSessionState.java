package info.isaksson.erland.whiteboard.voting;

public enum VotingSessionState {
    DRAFT("draft"),
    OPEN("open"),
    CLOSED("closed"),
    REVEALED("revealed"),
    CANCELLED("cancelled");

    private final String storageValue;

    VotingSessionState(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public boolean acceptsVotes() {
        return this == OPEN;
    }

    public boolean isFinal() {
        return this == REVEALED || this == CANCELLED;
    }

    public static VotingSessionState fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Voting session state is required");
        }
        for (VotingSessionState candidate : values()) {
            if (candidate.storageValue.equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported voting session state: " + value);
    }
}
