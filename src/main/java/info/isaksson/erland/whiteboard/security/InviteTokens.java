package info.isaksson.erland.whiteboard.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class InviteTokens {

    private static final SecureRandom RNG = new SecureRandom();

    private InviteTokens() {}

    /**
     * Generates a URL-safe random token (no padding).
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Backwards-compatible alias used by some tests/docs.
     */
    public static String newToken() {
        return generateToken();
    }

    /**
     * Hash token with SHA-256 and encode as lower-case hex.
     * Store/compare the hash, never the raw token.
     */
    public static String sha256Hex(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }
}
