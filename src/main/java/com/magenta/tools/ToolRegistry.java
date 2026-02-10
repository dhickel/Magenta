package com.magenta.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool provider registry using service locator pattern.
 * Supports dynamic registration and composition.
 */
public class ToolRegistry {
    private final Map<String, ToolProvider> providers = new ConcurrentHashMap<>();

    public void register(String name, ToolProvider provider) {
        providers.put(name, provider);
    }

    public Optional<ToolProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public List<Object> instantiateTools(List<String> toolNames, ToolContext context) {
        if (toolNames == null) {
            return List.of();
        }
        return toolNames.stream()
            .map(this::get)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(provider -> provider.create(context))
            .toList();
    }
}
