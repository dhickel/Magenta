package com.magenta.io;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * IOManager for agent-to-agent communication using queues.
 * Does not support colors.
 */
public class InternalIOManager extends AbstractIOManager {

    private final Queue<String> inputQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> outputQueue = new ConcurrentLinkedQueue<>();

    public InternalIOManager() {
        super();

        // Initialize pipes (raw I/O, no filtering - IOManager defaults handle that)
        this.inputPipe = this::readRaw;
        this.outputPipe = this::printRaw;
    }

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        // No-op for internal communication
    }

    /**
     * Raw input reading (no security filtering - handled by IOManager defaults).
     */
    private Message.Input readRaw(String prompt) {
        String raw = inputQueue.poll();
        return raw != null ? Message.input(raw) : Message.input("");
    }

    /**
     * Raw output writing (no security filtering - handled by IOManager defaults).
     */
    private void printRaw(Message message) {
        outputQueue.offer(message.content());  // Just queue the content
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
