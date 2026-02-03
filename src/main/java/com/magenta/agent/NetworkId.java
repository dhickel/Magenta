package com.magenta.agent;

import java.util.UUID;

public record NetworkId(UUID value) {
    public static com.magenta.session.SessionId random() {
        return new com.magenta.session.SessionId(UUID.randomUUID());
    }

    public static com.magenta.session.SessionId of(String uuidString) {
        return new com.magenta.session.SessionId(UUID.fromString(uuidString));
    }

    public String toString() {
        return value.toString();
    }
}

