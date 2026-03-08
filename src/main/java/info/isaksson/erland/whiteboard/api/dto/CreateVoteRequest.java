package info.isaksson.erland.whiteboard.api.dto;

public record CreateVoteRequest(
        String targetRef,
        Integer voteValue
) {
}
