package com.magenta.io;

import com.magenta.io.terminal.Writer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * IOManager for agent-to-agent communication using queues.
 * Does not support colors or styling.
 */
public class InternalIOManager extends IOManager {

    private final Queue<String> inputQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> outputQueue = new ConcurrentLinkedQueue<>();

    public InternalIOManager() {
        super();

        // Initialize pipes with String-based I/O
        this.inputPipe = this::readRaw;
        this.outputPipe = this::printRaw;
    }

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        // No-op for internal communication
    }

    /**
     * Raw input reading - returns String from queue.
     */
    private String readRaw(String prompt) {
        String raw = inputQueue.poll();
        return raw != null ? raw : "";
    }

    /**
     * Raw output writing - queues String.
     */
    private void printRaw(String text) {
        outputQueue.offer(text);
    }

    /**
     * Enqueue input for this manager to read.
     */
    public void enqueueInput(String input) {
        inputQueue.offer(input);
    }

    /**
     * Read all queued output.
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
     * Peek at next output without removing.
     */
    public String peekOutput() {
        return outputQueue.peek();
    }

    /**
     * Clear all queued output.
     */
    public void clearOutput() {
        outputQueue.clear();
    }

    @Override
    public ResponseHandler createResponseHandler(Integer agentColor, int delayMs) {
        // Internal communication doesn't use delays or colors
        return new Writer(this);
    }

    @Override
    public void close() {
        inputQueue.clear();
        outputQueue.clear();
    }
}
