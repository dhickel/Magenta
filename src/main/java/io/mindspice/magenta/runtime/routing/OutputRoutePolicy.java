package io.mindspice.magenta.runtime.routing;

import java.util.Set;

public record OutputRoutePolicy(
        Set<OutputRoutingEvent.Kind> eventKinds,
        Set<String> sourceAllowlist,
        Set<String> tagAllowlist
) {

    public OutputRoutePolicy {
        eventKinds = eventKinds == null ? Set.of() : Set.copyOf(eventKinds);
        sourceAllowlist = sourceAllowlist == null ? Set.of() : Set.copyOf(sourceAllowlist);
        tagAllowlist = tagAllowlist == null ? Set.of() : Set.copyOf(tagAllowlist);
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

        if (!eventKinds.isEmpty() && !eventKinds.contains(event.kind())) {
            return false;
        }
        if (!sourceAllowlist.isEmpty() && !sourceAllowlist.contains(event.source())) {
            return false;
        }
        if (!tagAllowlist.isEmpty()) {
            Set<String> eventTags = event.tags();
            return eventTags != null && eventTags.stream().anyMatch(tagAllowlist::contains);
        }

        return true;
    }

    public boolean requestsPartialTokens() {
        return eventKinds.isEmpty() || eventKinds.contains(OutputRoutingEvent.Kind.PARTIAL);
    }

    public static final class Builder {
        private Set<OutputRoutingEvent.Kind> eventKinds = Set.of();
        private Set<String> sourceAllowlist = Set.of();
        private Set<String> tagAllowlist = Set.of();

        public Builder eventKinds(Set<OutputRoutingEvent.Kind> eventKinds) {
            this.eventKinds = eventKinds == null ? Set.of() : Set.copyOf(eventKinds);
            return this;
        }

        public Builder sourceAllowlist(Set<String> sourceAllowlist) {
            this.sourceAllowlist = sourceAllowlist == null ? Set.of() : Set.copyOf(sourceAllowlist);
            return this;
        }

        public Builder tagAllowlist(Set<String> tagAllowlist) {
            this.tagAllowlist = tagAllowlist == null ? Set.of() : Set.copyOf(tagAllowlist);
            return this;
        }

        public OutputRoutePolicy build() {
            return new OutputRoutePolicy(eventKinds, sourceAllowlist, tagAllowlist);
        }
    }
}
