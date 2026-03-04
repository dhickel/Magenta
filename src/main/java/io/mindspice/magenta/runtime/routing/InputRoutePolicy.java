package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionInput;

import java.util.Set;

public record InputRoutePolicy(
        Set<FilterTag<SessionInput>> inputFilters,
        Set<String> allowedSourceIds
) {
    public InputRoutePolicy {
        inputFilters = inputFilters == null ? Set.of() : Set.copyOf(inputFilters);
        allowedSourceIds = allowedSourceIds == null ? Set.of() : Set.copyOf(allowedSourceIds);
    }

    // Empty filter/source sets are allow-all defaults.
    public static InputRoutePolicy defaults() {
        return new InputRoutePolicy(Set.of(), Set.of());
    }

    public boolean allows(SessionInput input) {
        if (input == null) {
            return false;
        }

        // Source IDs are only restricted when an allow-list is explicitly provided.
        if (!allowedSourceIds.isEmpty() && !allowedSourceIds.contains(input.sourceId())) {
            return false;
        }

        return matches(inputFilters, input);
    }

    private static <T> boolean matches(Set<FilterTag<T>> allowedTags, T value) {
        // Empty tag set means allow-all by type; otherwise require at least one matching tag.
        return allowedTags.isEmpty() || allowedTags.stream().anyMatch(tag -> tag.passes(value));
    }
}
