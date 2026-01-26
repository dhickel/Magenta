package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;

import com.magenta.security.SecurityManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ShellTools {

    @Tool("Execute a shell command. Use with EXTREME CAUTION. Only run commands you understand and that are safe.")
    public String runShellCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Error: Command cannot be empty.";
        }

        // Security Check
        if (!SecurityManager.requireApproval("shell", command)) {
            return "Error: Command execution denied by user or security policy.";
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        CommandLine cmdLine = CommandLine.parse("/bin/bash");
        cmdLine.addArgument("-c");
        cmdLine.addArgument(command, false); // false = handle quoting manually if needed, but here we pass the whole string

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
