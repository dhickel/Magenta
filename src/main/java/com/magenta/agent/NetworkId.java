package com.magenta.agent;

import java.util.UUID;

public record NetworkId(UUID value) {
    public static NetworkId random() {
        return new NetworkId(UUID.randomUUID());
    }

    public static NetworkId of(String uuidString) {
        return new NetworkId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
