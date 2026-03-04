package info.isaksson.erland.whiteboard.ws;

/**
 * Simple token-bucket rate limiter.
 *
 * Not crypto/abuse-proof; MVP hardening only.
 */
public final class TokenBucketRateLimiter {

    private final int capacity;
    private final double refillPerNanos;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(int capacity, int refillPerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (refillPerSecond <= 0) throw new IllegalArgumentException("refillPerSecond must be > 0");
        this.capacity = capacity;
        this.refillPerNanos = (double) refillPerSecond / 1_000_000_000d;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * @return true if a token was consumed and the action is allowed
     */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1d) {
            tokens -= 1d;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long delta = now - lastRefillNanos;
        if (delta <= 0) return;
        double add = delta * refillPerNanos;
        if (add > 0d) {
            tokens = Math.min(capacity, tokens + add);
            lastRefillNanos = now;
        }
    }
}
