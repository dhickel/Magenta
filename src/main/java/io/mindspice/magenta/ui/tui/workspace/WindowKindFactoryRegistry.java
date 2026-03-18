package io.mindspice.magenta.ui.tui.workspace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WindowKindFactoryRegistry {
    private final Map<String, WindowKindFactory> factoriesByKind = new LinkedHashMap<>();

    public WindowKindFactoryRegistry() {
    }

    public static WindowKindFactoryRegistry fromFactories(List<WindowKindFactory> factories) {
        WindowKindFactoryRegistry registry = new WindowKindFactoryRegistry();
        if (factories != null) {
            for (WindowKindFactory factory : factories) {
                registry.register(factory);
            }
        }
        return registry;
    }

    public synchronized void register(WindowKindFactory factory) {
        Objects.requireNonNull(factory, "factory");
        String normalizedKind = normalizeKind(factory.kind());
        if (normalizedKind.isBlank()) {
            throw new IllegalStateException("Window factory kind must not be blank");
        }
        if (factoriesByKind.putIfAbsent(normalizedKind, factory) != null) {
            throw new IllegalStateException("Duplicate window factory kind: " + normalizedKind);
        }
    }

    public synchronized WindowKindFactory require(String kind) {
        String normalizedKind = normalizeKind(kind);
        WindowKindFactory factory = factoriesByKind.get(normalizedKind);
        if (factory == null) {
            throw new IllegalStateException("No window factory registered for kind: " + normalizedKind);
        }
        return factory;
    }

    public synchronized boolean contains(String kind) {
        return factoriesByKind.containsKey(normalizeKind(kind));
    }

    public synchronized Set<String> kinds() {
        return Set.copyOf(factoriesByKind.keySet());
    }

    private String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }
}
