package info.isaksson.erland.whiteboard.assets;

public final class AssetRules {

    public static final long MAX_ASSET_SIZE_BYTES = 100L * 1024L * 1024L;
    public static final int MAX_LOGICAL_NAME_LENGTH = 255;
    public static final int MAX_CONTENT_TYPE_LENGTH = 255;
    public static final int MAX_SCOPE_REF_LENGTH = 255;
    public static final int MAX_INTEGRITY_HASH_LENGTH = 512;
    public static final int MAX_VERSION_TAG_LENGTH = 128;
    public static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private AssetRules() {
    }

    public static void validateNewAsset(AssetScopeType scopeType,
                                        String scopeRef,
                                        String logicalName,
                                        String contentType,
                                        long sizeBytes,
                                        String createdByUserId,
                                        String integrityHash,
                                        String versionTag) {
        requireScope(scopeType, scopeRef);
        requireLogicalName(logicalName);
        requireContentType(contentType);
        requireSize(sizeBytes);
        requireCreatedByUserId(createdByUserId);
        requireOptionalLength(integrityHash, MAX_INTEGRITY_HASH_LENGTH, "integrityHash");
        requireOptionalLength(versionTag, MAX_VERSION_TAG_LENGTH, "versionTag");
    }

    public static void requireTransition(Asset asset, AssetState nextState) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset is required");
        }
        if (nextState == null) {
            throw new IllegalArgumentException("Asset state is required");
        }
        if (asset.state() == AssetState.DELETED) {
            throw new IllegalArgumentException("Deleted asset cannot transition");
        }
        if (asset.state() == nextState) {
            return;
        }
        switch (nextState) {
            case PENDING -> throw new IllegalArgumentException("Asset cannot transition back to pending");
            case ACTIVE -> {
                if (asset.state() != AssetState.PENDING && asset.state() != AssetState.FAILED && asset.state() != AssetState.QUARANTINED) {
                    throw new IllegalArgumentException("Asset cannot transition to active from current state");
                }
            }
            case FAILED, QUARANTINED -> {
                if (asset.state() != AssetState.PENDING && asset.state() != AssetState.ACTIVE) {
                    throw new IllegalArgumentException("Asset cannot transition to failure/quarantine from current state");
                }
            }
            case DELETED -> {
            }
        }
    }

    public static void requireFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason is required");
        }
        requireOptionalLength(failureReason, MAX_FAILURE_REASON_LENGTH, "failureReason");
    }

    private static void requireScope(AssetScopeType scopeType, String scopeRef) {
        if (scopeType == null) {
            throw new IllegalArgumentException("Asset scope type is required");
        }
        if (scopeRef == null || scopeRef.isBlank()) {
            throw new IllegalArgumentException("Asset scope reference is required");
        }
        requireOptionalLength(scopeRef, MAX_SCOPE_REF_LENGTH, "scopeRef");
    }

    private static void requireLogicalName(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            throw new IllegalArgumentException("Asset logical name is required");
        }
        requireOptionalLength(logicalName, MAX_LOGICAL_NAME_LENGTH, "logicalName");
    }

    private static void requireContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Asset content type is required");
        }
        requireOptionalLength(contentType, MAX_CONTENT_TYPE_LENGTH, "contentType");
    }

    private static void requireSize(long sizeBytes) {
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("Asset size must be zero or greater");
        }
        if (sizeBytes > MAX_ASSET_SIZE_BYTES) {
            throw new IllegalArgumentException("Asset size exceeds maximum allowed limit");
        }
    }

    private static void requireCreatedByUserId(String createdByUserId) {
        if (createdByUserId == null || createdByUserId.isBlank()) {
            throw new IllegalArgumentException("Asset creator is required");
        }
    }

    private static void requireOptionalLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length");
        }
    }
}
