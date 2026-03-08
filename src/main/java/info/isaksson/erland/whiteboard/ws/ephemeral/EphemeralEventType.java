package info.isaksson.erland.whiteboard.ws.ephemeral;

import java.util.Arrays;
import java.util.Optional;

public enum EphemeralEventType {
    CURSOR("cursor"),
    VIEWPORT("viewport"),
    FOLLOW("follow"),
    PRESENCE_META("presence-meta"),
    REACTION("reaction"),
    TIMER_CONTROL("timer-control"),
    TIMER_STATE("timer-state");

    private final String wireName;

    EphemeralEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<EphemeralEventType> fromWireName(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.wireName.equalsIgnoreCase(value))
                .findFirst();
    }
}
