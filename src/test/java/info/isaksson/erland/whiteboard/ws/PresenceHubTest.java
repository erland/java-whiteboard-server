package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class PresenceHubTest {

    @Test
    void join_leave_snapshot() {
        PresenceHub hub = new PresenceHub();
        assertEquals(Map.of(), hub.snapshot("b1"));

        var u1 = hub.join("b1", "c1", "alice");
        assertEquals(1, u1.size());

        var u2 = hub.join("b1", "c2", "bob");
        assertEquals(2, u2.size());

        var afterLeave = hub.leave("b1", "c1");
        assertEquals(1, afterLeave.size());

        var afterLeave2 = hub.leave("b1", "c2");
        assertEquals(0, afterLeave2.size());
        assertEquals(Map.of(), hub.snapshot("b1"));
    }
}
