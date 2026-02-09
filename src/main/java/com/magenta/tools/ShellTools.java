package com.magenta.tools;

import com.magenta.io.IOManager;
import com.magenta.io.terminal.BashExecutor;
import dev.langchain4j.agent.tool.Tool;

public class ShellTools {

    private final IOManager io;

    public ShellTools(IOManager io) {
        this.io = io;
    }

    @Tool("Execute a shell command. Use with EXTREME CAUTION. Only run commands you understand and that are safe.")
    public String runShellCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Error: Command cannot be empty.";
        }

        BashExecutor.BashResult result = BashExecutor.execute(command);
        if (!result.error().isEmpty()) {
            return "Error executing command: " + result.error() + "\nOutput:\n" + result.output();
        }
        return "Exit Code: " + result.exitCode() + "\nOutput:\n" + result.output();
    }
}
