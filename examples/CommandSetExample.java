package examples;

import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.session.SystemCommands;

/**
 * Example demonstrating CommandSet composition.
 */
public class CommandSetExample {
    public static void main(String[] args) {
        // Create custom command sets
        CommandSet adminCommands = CommandSet.of(
            Command.of("restart", "Restart the system",
                raw -> raw.startsWith("/restart"),
                (session, raw) -> System.out.println("Restarting...")),

            Command.of("config", "Edit configuration",
                raw -> raw.startsWith("/config"),
                (session, raw) -> System.out.println("Editing config..."))
        );

        CommandSet devCommands = CommandSet.of(
            Command.of("debug", "Toggle debug mode",
                raw -> raw.startsWith("/debug"),
                (session, raw) -> System.out.println("Debug mode toggled")),

            Command.of("profile", "Profile performance",
                raw -> raw.startsWith("/profile"),
                (session, raw) -> System.out.println("Profiling..."))
        );

        // Compose command sets
        CommandSet allCommands = SystemCommands.commands()
            .composedWith(adminCommands)
            .composedWith(devCommands);

        // Print all available commands
        System.out.println("Total commands: " + allCommands.commands().size());
        System.out.println("\nAvailable commands:");
        for (Command cmd : allCommands.commands()) {
            System.out.println("  /" + cmd.name() + " - " + cmd.description());
        }

        // Test parsing (CommandSet handles parsing, not Command)
        var exitCmd = allCommands.parse("/exit");
        System.out.println("\nParsed '/exit': " + exitCmd.map(Command::name).orElse("none"));

        var debugCmd = allCommands.parse("/debug");
        System.out.println("Parsed '/debug': " + debugCmd.map(Command::name).orElse("none"));

        var unknownCmd = allCommands.parse("/notacommand");
        System.out.println("Parsed '/notacommand': " + unknownCmd.map(Command::name).orElse("none"));
    }
}
