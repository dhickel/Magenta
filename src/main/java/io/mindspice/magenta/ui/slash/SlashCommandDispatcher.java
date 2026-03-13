package io.mindspice.magenta.ui.slash;

import io.mindspice.magenta.ui.render.UiRenderer;

import java.util.List;
import java.util.Objects;

public final class SlashCommandDispatcher {

    private final SlashCommandParser parser;
    private final SlashCommandRegistry registry;
    private final UiRenderer renderer;

    public SlashCommandDispatcher(SlashCommandRegistry registry, UiRenderer renderer) {
        this.parser = new SlashCommandParser();
        this.registry = Objects.requireNonNull(registry, "registry");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public boolean dispatchIfCommand(String line) {
        SlashCommandParseResult parseResult = parser.parse(line);

        return switch (parseResult) {
            case SlashCommandParseResult.NotCommand ignored -> false;
            case SlashCommandParseResult.ParseError error -> {
                renderer.printError("command parse error: " + error.message());
                yield true;
            }
            case SlashCommandParseResult.Parsed parsed -> {
                runCommand(parsed.invocation());
                yield true;
            }
        };
    }

    private void runCommand(SlashCommandInvocation invocation) {
        SlashCommandSpec spec = registry.find(invocation.name()).orElse(null);
        if (spec == null) {
            renderer.printError("unknown command: /" + invocation.name());
            return;
        }

        List<String> args = invocation.args();
        int minArity = spec.action().minArity();
        int maxArity = spec.action().maxArity();
        if (args.size() < minArity || args.size() > maxArity) {
            renderer.printError("usage: " + spec.usage());
            return;
        }

        try {
            switch (spec.action()) {
                case SlashCommandAction.ZeroArg zeroArg -> zeroArg.handler().run();
                case SlashCommandAction.OneArg oneArg -> oneArg.handler().accept(args.getFirst());
                case SlashCommandAction.OptionalOneArg optionalOneArg ->
                        optionalOneArg.handler().accept(args.isEmpty() ? "" : args.getFirst());
                case SlashCommandAction.TwoArg twoArg -> twoArg.handler().accept(args.get(0), args.get(1));
                case SlashCommandAction.ThreeArg threeArg -> threeArg.handler().accept(args.get(0), args.get(1), args.get(2));
            }
        } catch (Exception e) {
            renderer.printError("command failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
