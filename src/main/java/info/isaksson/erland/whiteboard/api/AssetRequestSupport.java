package info.isaksson.erland.whiteboard.api;

import info.isaksson.erland.whiteboard.api.dto.ActivateAssetRequest;
import info.isaksson.erland.whiteboard.api.dto.AssetFailureRequest;
import info.isaksson.erland.whiteboard.api.dto.CreateAssetRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AssetRequestSupport {

    @Inject
    ApiRequestSupport apiRequestSupport;

    public String logicalName(CreateAssetRequest req) {
        return req == null ? null : req.logicalName();
    }

    public String contentType(CreateAssetRequest req) {
        return req == null ? null : req.contentType();
    }

    public long sizeBytes(CreateAssetRequest req) {
        return req == null || req.sizeBytes() == null ? -1L : req.sizeBytes();
    }

    public String integrityHash(CreateAssetRequest req) {
        return req == null ? null : req.integrityHash();
    }

    public String versionTag(CreateAssetRequest req) {
        return req == null ? null : req.versionTag();
    }

    public String activatedVersionTag(ActivateAssetRequest req) {
        return req == null ? null : req.versionTag();
    }

    public String requireFailureReason(AssetFailureRequest req) {
        return req == null ? null : req.failureReason();
    }

    public Response validationError(String message) {
        return apiRequestSupport.validationError(message);
    }
}
