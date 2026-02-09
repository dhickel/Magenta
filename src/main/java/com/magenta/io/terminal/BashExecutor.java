package com.magenta.io.terminal;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility for executing bash commands with consistent timeout and error handling.
 */
public class BashExecutor {
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60 seconds

    public record BashResult(int exitCode, String output, String error) {}

    /**
     * Execute a bash command with default timeout.
     *
     * @param command The command to execute
     * @return BashResult with exit code and output
     */
    public static BashResult execute(String command) {
        return execute(command, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Execute a bash command with custom timeout.
     *
     * @param command The command to execute
     * @param timeoutMs Timeout in milliseconds
     * @return BashResult with exit code and output
     */
    public static BashResult execute(String command, long timeoutMs) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        CommandLine cmdLine = CommandLine.parse("/bin/bash");
        cmdLine.addArgument("-c");
        cmdLine.addArgument(command, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWatchdog(new ExecuteWatchdog(timeoutMs));

        try {
            int exitValue = executor.execute(cmdLine);
            return new BashResult(exitValue, outputStream.toString(), "");
        } catch (IOException e) {
            return new BashResult(-1, outputStream.toString(), e.getMessage());
        }
    }
}
