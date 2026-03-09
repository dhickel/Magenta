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
        Observability observability,
        Security security,
        ToolOutput toolOutput,
        Prompts prompts,
        TerminalUiCallbacks callbacks
) {

    public TerminalUiConfig {
        session = Objects.requireNonNull(session, "session");
        rendering = Objects.requireNonNull(rendering, "rendering");
        behavior = Objects.requireNonNull(behavior, "behavior");
        observability = observability == null ? new Observability(false) : observability;
        security = security == null ? new Security(SecurityEventVisibility.DENIALS_ONLY) : security;
        toolOutput = toolOutput == null ? new ToolOutput(ToolOutputFormat.COMPACT_SUMMARY) : toolOutput;
        prompts = Objects.requireNonNull(prompts, "prompts");
        callbacks = callbacks == null ? TerminalUiCallbacks.defaults() : callbacks;
    }

    public static TerminalUiConfig defaults() {
        return new TerminalUiConfig(
                new Session("terminal", SessionParams.ofStreaming(true), RoutingEventLevel.NONE),
                Rendering.defaults(),
                new Behavior(Set.of("/exit", "/quit"), "you> ", "cli-system"),
                new Observability(false),
                new Security(SecurityEventVisibility.DENIALS_ONLY),
                new ToolOutput(ToolOutputFormat.COMPACT_SUMMARY),
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
            routingEventLevel = routingEventLevel == null ? RoutingEventLevel.NONE : routingEventLevel;
        }
    }

    public record Rendering(
            boolean colorEnabled,
            boolean showTimestamps,
            boolean showStatusBar,
            ColorPalette colors
    ) {
        public Rendering {
            colors = colors == null ? ColorPalette.defaults() : colors;
        }

        public static Rendering defaults() {
            return new Rendering(true, false, true, ColorPalette.defaults());
        }
    }

    public record ColorPalette(
            ColorName system,
            ColorName user,
            ColorName assistant,
            ColorName info,
            ColorName warn,
            ColorName error,
            ColorName muted,
            ColorName defaultColor
    ) {
        public ColorPalette {
            system = system == null ? ColorName.MAGENTA : system;
            user = user == null ? ColorName.CYAN : user;
            assistant = assistant == null ? ColorName.GREEN : assistant;
            info = info == null ? ColorName.CYAN : info;
            warn = warn == null ? ColorName.YELLOW : warn;
            error = error == null ? ColorName.RED : error;
            muted = muted == null ? ColorName.BRIGHT : muted;
            defaultColor = defaultColor == null ? ColorName.DEFAULT : defaultColor;
        }

        public static ColorPalette defaults() {
            return new ColorPalette(
                    ColorName.MAGENTA,
                    ColorName.CYAN,
                    ColorName.GREEN,
                    ColorName.CYAN,
                    ColorName.YELLOW,
                    ColorName.RED,
                    ColorName.BRIGHT,
                    ColorName.DEFAULT
            );
        }
    }

    public enum ColorName {
        DEFAULT,
        BLACK,
        RED,
        GREEN,
        YELLOW,
        BLUE,
        MAGENTA,
        CYAN,
        WHITE,
        BRIGHT
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

    public record Observability(
            boolean routingLogsEnabled
    ) {
    }

    public record Security(
            SecurityEventVisibility eventVisibility
    ) {
        public Security {
            eventVisibility = eventVisibility == null ? SecurityEventVisibility.DENIALS_ONLY : eventVisibility;
        }
    }

    public enum SecurityEventVisibility {
        DENIALS_ONLY,
        ALL,
        OFF
    }

    public record ToolOutput(
            ToolOutputFormat format
    ) {
        public ToolOutput {
            format = format == null ? ToolOutputFormat.COMPACT_SUMMARY : format;
        }
    }

    public enum ToolOutputFormat {
        COMPACT_SUMMARY
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
