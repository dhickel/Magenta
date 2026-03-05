package io.mindspice.magenta.ui.slash;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class SlashCompleter implements Completer {

    private final Supplier<SlashCommandRegistry> registrySupplier;

    public SlashCompleter(Supplier<SlashCommandRegistry> registrySupplier) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String current = line == null ? "" : line.line();
        if (current == null || !current.startsWith("/")) {
            return;
        }

        SlashCommandRegistry registry = registrySupplier.get();
        if (registry == null) {
            return;
        }

        String[] parts = current.split("\\s+");
        if (parts.length <= 1 && !current.endsWith(" ")) {
            String commandPrefix = parts.length == 0 ? "/" : parts[0];
            for (SlashCommandSpec command : registry.commands()) {
                String token = "/" + command.name();
                if (token.startsWith(commandPrefix)) {
                    candidates.add(new Candidate(token, token, "commands", command.help(), null, null, true));
                }
            }
            return;
        }

        String commandName = parts[0].substring(1);
        SlashCommandSpec spec = registry.find(commandName).orElse(null);
        if (spec == null || spec.argHints().isEmpty()) {
            return;
        }

        int argIndex = Math.max(0, parts.length - 2);
        if (argIndex >= spec.argHints().size()) {
            return;
        }

        String hint = spec.argHints().get(argIndex);
        candidates.add(new Candidate(hint, hint, "args", "argument", null, null, true));
    }
}
