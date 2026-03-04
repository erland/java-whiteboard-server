package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TokenBucketRateLimiterTest {

    @Test
    void allows_burst_then_limits() {
        TokenBucketRateLimiter rl = new TokenBucketRateLimiter(3, 1);
        assertTrue(rl.tryConsume());
        assertTrue(rl.tryConsume());
        assertTrue(rl.tryConsume());
        assertFalse(rl.tryConsume());
    }
}
