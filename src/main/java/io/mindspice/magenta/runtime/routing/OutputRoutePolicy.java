package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.Objects;
import java.util.Set;

public record OutputRoutePolicy(
        Set<FilterTag<SessionOutput>> outputFilters
) {

    public OutputRoutePolicy {
        Objects.requireNonNull(outputFilters, "outputFilters");
        outputFilters = Set.copyOf(outputFilters);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OutputRoutePolicy defaults() {
        return builder().build();
    }

    public boolean allows(OutputRoutingEvent event) {
        if (event == null) {
            return false;
        }

        return outputFilters.isEmpty() || outputFilters.stream().anyMatch(tag -> tag.passes(event.output()));
    }

    public boolean requestsStreamedOutput() {
        return outputFilters.isEmpty()
               || outputFilters.stream().anyMatch(tag -> tag.passes(new SessionOutput.StreamedOutput(".")));
    }

    public static final class Builder {
        private Set<FilterTag<SessionOutput>> allowedOutputTags = Set.of();

        public Builder allowedOutputTags(Set<FilterTag<SessionOutput>> allowedOutputTags) {
            this.allowedOutputTags = Objects.requireNonNull(allowedOutputTags, "outputFilters");
            return this;
        }

        public OutputRoutePolicy build() {
            return new OutputRoutePolicy(allowedOutputTags);
        }
    }
}
