package io.mindspice.magenta.ui.slash;

import java.util.List;

public record SlashCommandInvocation(
        String name,
        List<String> args
) {
    public SlashCommandInvocation {
        name = name == null ? "" : name;
        args = args == null ? List.of() : List.copyOf(args);
    }
}
