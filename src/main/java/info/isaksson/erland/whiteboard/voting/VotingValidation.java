package info.isaksson.erland.whiteboard.voting;

final class VotingValidation {

    private VotingValidation() {
    }

    static String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
