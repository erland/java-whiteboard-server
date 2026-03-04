package info.isaksson.erland.whiteboard.ws;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class BoardOpSequencerTest {

    @Test
    void monotonic_per_board() {
        BoardOpSequencer s = new BoardOpSequencer();

        assertEquals(1L, s.next("b1"));
        assertEquals(2L, s.next("b1"));
        assertEquals(1L, s.next("b2"));
        assertEquals(3L, s.next("b1"));
        assertEquals(2L, s.next("b2"));
    }
}
