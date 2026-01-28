package com.magenta.tools;

import com.magenta.io.IOManager;
import com.magenta.security.SecurityManager;
import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ShellTools {

    private final SecurityManager securityManager;
    private final IOManager io;

    public ShellTools(SecurityManager securityManager, IOManager io) {
        this.securityManager = securityManager;
        this.io = io;
    }

    @Tool("Execute a shell command. Use with EXTREME CAUTION. Only run commands you understand and that are safe.")
    public String runShellCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Error: Command cannot be empty.";
        }

        // Security check via instance method
        if (!securityManager.requireToolApproval("shell", command, io)) {
            return "Error: Command execution denied by user or security policy.";
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        CommandLine cmdLine = CommandLine.parse("/bin/bash");
        cmdLine.addArgument("-c");
        cmdLine.addArgument(command, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);

        // Timeout of 60 seconds
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        executor.setWatchdog(watchdog);

        try {
            int exitValue = executor.execute(cmdLine);
            return "Exit Code: " + exitValue + "\nOutput:\n" + outputStream.toString();
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage() + "\nOutput:\n" + outputStream.toString();
        }
    }
}
