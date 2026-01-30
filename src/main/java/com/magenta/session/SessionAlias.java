package com.magenta.session;

import java.util.Objects;

public record SessionAlias(String value) {
    public SessionAlias {
        Objects.requireNonNull(value, "Alias cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be blank");
        }
    }

    public static SessionAlias of(String value) {
        return new SessionAlias(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}