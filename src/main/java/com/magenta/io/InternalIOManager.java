package com.magenta.io;

import com.magenta.security.SecurityFilter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Internal IOManager for agent-to-agent communication.
 * Uses queues instead of terminal I/O for inter-agent messaging.
 */
public class InternalIOManager implements IOManager {

    private final Queue<String> inputQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> outputQueue = new ConcurrentLinkedQueue<>();
    private SecurityFilter securityFilter;

    public InternalIOManager() {
        this.securityFilter = SecurityFilter.identity(); // Default: no filtering
    }

    @Override
    public SecurityFilter securityFilter() {
        return securityFilter;
    }

    @Override
    public void setSecurityFilter(SecurityFilter filter) {
        this.securityFilter = filter;
    }

    // === Input (InputPipe) ===

    @Override
    public Command read(String prompt) {
        String input = inputQueue.poll();
        if (input == null) {
            return null; // No input available
        }
        Command cmd = Command.parse(input);
        // Apply security filter
        cmd = securityFilter.inputFilter().apply(cmd, this);
        return cmd;
    }

    /**
     * Add input to the queue for processing.
     */
    public void enqueueInput(String input) {
        inputQueue.offer(input);
    }

    // === Output (OutputPipe) ===

    @Override
    public void print(String text) {
        String filtered = securityFilter.outputFilter().apply(text);
        outputQueue.offer(filtered);
    }

    @Override
    public void println(String text) {
        String filtered = securityFilter.outputFilter().apply(text);
        outputQueue.offer(filtered + "\n");
    }

    /**
     * Read all output from the queue.
     */
    public String readOutput() {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = outputQueue.poll()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Peek at output without removing it from the queue.
     */
    public String peekOutput() {
        return outputQueue.peek();
    }

    /**
     * Clear all output from the queue.
     */
    public void clearOutput() {
        outputQueue.clear();
    }

    @Override
    public ResponseHandler createResponseHandler(Integer agentColor, int delayMs) {
        // Internal communication doesn't use delays or colors
        return new Writer(this, null);
    }

    @Override
    public void close() {
        inputQueue.clear();
        outputQueue.clear();
    }
}
