package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionParams;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record TerminalUiConfig(
        Session session,
        Rendering rendering,
        Behavior behavior,
        Prompts prompts,
        TerminalUiCallbacks callbacks
) {

    public TerminalUiConfig {
        session = Objects.requireNonNull(session, "session");
        rendering = Objects.requireNonNull(rendering, "rendering");
        behavior = Objects.requireNonNull(behavior, "behavior");
        prompts = Objects.requireNonNull(prompts, "prompts");
        callbacks = callbacks == null ? TerminalUiCallbacks.defaults() : callbacks;
    }

    public static TerminalUiConfig defaults() {
        return new TerminalUiConfig(
                new Session("terminal", SessionParams.ofStreaming(true), RoutingEventLevel.FINAL),
                new Rendering(true, false, true),
                new Behavior(Set.of("/exit", "/quit"), "you> ", "cli-system"),
                new Prompts(true, 240),
                TerminalUiCallbacks.defaults()
        );
    }

    public record Session(
            String alias,
            SessionParams params,
            RoutingEventLevel routingEventLevel
    ) {
        public Session {
            alias = alias == null || alias.isBlank() ? "terminal" : alias.trim();
            params = params == null ? SessionParams.ofStreaming(true) : params;
            routingEventLevel = routingEventLevel == null ? RoutingEventLevel.FINAL : routingEventLevel;
        }
    }

    public record Rendering(
            boolean colorEnabled,
            boolean showTimestamps,
            boolean showStatusBar
    ) {
    }

    public record Behavior(
            Set<String> exitCommands,
            String userPrompt,
            String systemEventSourceId
    ) {
        public Behavior {
            exitCommands = exitCommands == null ? Set.of("/exit", "/quit") : exitCommands.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(v -> v.toLowerCase(Locale.ROOT))
                    .filter(v -> !v.isEmpty())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            userPrompt = userPrompt == null || userPrompt.isBlank() ? "you> " : userPrompt;
            systemEventSourceId = systemEventSourceId == null || systemEventSourceId.isBlank() ? "cli-system" : systemEventSourceId;
        }

        public boolean isExitCommand(String input) {
            if (input == null || input.isBlank()) {
                return false;
            }
            return exitCommands.contains(input.trim().toLowerCase(Locale.ROOT));
        }
    }

    public record Prompts(
            boolean showToolArgsPreview,
            int maxToolArgsPreviewChars
    ) {
        public Prompts {
            maxToolArgsPreviewChars = maxToolArgsPreviewChars <= 0 ? 240 : maxToolArgsPreviewChars;
        }
    }
}
