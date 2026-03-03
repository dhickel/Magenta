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

    public static InputRoutePolicy defaults() {
        return new InputRoutePolicy(Set.of(), Set.of());
    }

    public boolean allows(SessionInput input) {
        if (input == null) {
            return false;
        }

        if (!allowedSourceIds.isEmpty() && !allowedSourceIds.contains(input.sourceId())) {
            return false;
        }

        return matches(inputFilters, input);
    }

    private static <T> boolean matches(Set<FilterTag<T>> allowedTags, T value) {
        return allowedTags.isEmpty() || allowedTags.stream().anyMatch(tag -> tag.passes(value));
    }
}
