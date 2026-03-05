package io.mindspice.magenta.ui.slash;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SlashCommandRegistry {

    private final List<SlashCommandSpec> commands;
    private final Map<String, SlashCommandSpec> byName;

    public SlashCommandRegistry(List<SlashCommandSpec> commands) {
        this.commands = commands == null ? List.of() : List.copyOf(commands);
        Map<String, SlashCommandSpec> lookup = new LinkedHashMap<>();
        for (SlashCommandSpec command : this.commands) {
            String normalized = normalize(command.name());
            lookup.put(normalized, command);
            for (String alias : command.aliases()) {
                lookup.put(normalize(alias), command);
            }
        }
        this.byName = Map.copyOf(lookup);
    }

    public static SlashCommandRegistry empty() {
        return new SlashCommandRegistry(List.of());
    }

    public List<SlashCommandSpec> commands() {
        return commands;
    }

    public Optional<SlashCommandSpec> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
