package io.mindspice.magenta.ui.slash;

import java.util.List;
import java.util.Objects;

public record SlashCommandSpec(
        String name,
        List<String> aliases,
        String help,
        String usage,
        List<String> argHints,
        SlashCommandAction action
) {

    public SlashCommandSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(help, "help");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(action, "action");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        argHints = argHints == null ? List.of() : List.copyOf(argHints);
    }

    public static SlashCommandSpec zero(
            String name,
            List<String> aliases,
            String help,
            String usage,
            Runnable handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, List.of(), new SlashCommandAction.ZeroArg(handler));
    }

    public static SlashCommandSpec one(
            String name,
            List<String> aliases,
            String help,
            String usage,
            List<String> argHints,
            java.util.function.Consumer<String> handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, argHints, new SlashCommandAction.OneArg(handler));
    }

    public static SlashCommandSpec optionalOne(
            String name,
            List<String> aliases,
            String help,
            String usage,
            List<String> argHints,
            java.util.function.Consumer<String> handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, argHints, new SlashCommandAction.OptionalOneArg(handler));
    }

    public static SlashCommandSpec two(
            String name,
            List<String> aliases,
            String help,
            String usage,
            List<String> argHints,
            java.util.function.BiConsumer<String, String> handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, argHints, new SlashCommandAction.TwoArg(handler));
    }

    public static SlashCommandSpec three(
            String name,
            List<String> aliases,
            String help,
            String usage,
            List<String> argHints,
            TriConsumer<String, String, String> handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, argHints, new SlashCommandAction.ThreeArg(handler));
    }

    public static SlashCommandSpec varArg(
            String name,
            List<String> aliases,
            String help,
            String usage,
            List<String> argHints,
            int minArgs,
            int maxArgs,
            java.util.function.Consumer<List<String>> handler
    ) {
        return new SlashCommandSpec(name, aliases, help, usage, argHints, new SlashCommandAction.VarArg(minArgs, maxArgs, handler));
    }
}
