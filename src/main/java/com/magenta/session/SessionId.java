package com.magenta.session;

import java.util.UUID;

public record SessionId(UUID value) {
    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }
    
    public static SessionId of(String uuidString) {
        return new SessionId(UUID.fromString(uuidString));
    }
    
    public String toString() {
        return value.toString();
    }
}
