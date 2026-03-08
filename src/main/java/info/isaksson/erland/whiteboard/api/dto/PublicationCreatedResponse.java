package info.isaksson.erland.whiteboard.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import info.isaksson.erland.whiteboard.publication.PublicationService;

@Schema(name = "PublicationCreatedResponse", description = "Publication metadata returned when a publication is created or its access material is rotated. Includes the plain-text token exactly once.")
public record PublicationCreatedResponse(
        @Schema(description = "Publication metadata.")
        PublicationResponse publication,
        @Schema(description = "Plain-text publication access token. This is only returned at creation time or immediately after token rotation.", example = "b6d2f3f0e8c7495f9a1c3c7d6a4b2e1f")
        String token
) {
    public static PublicationCreatedResponse from(PublicationService.CreatedPublication createdPublication) {
        return new PublicationCreatedResponse(PublicationResponse.from(createdPublication.publication()), createdPublication.accessToken());
    }
}
