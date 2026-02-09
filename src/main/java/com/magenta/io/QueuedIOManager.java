package com.magenta.io;

import com.magenta.io.terminal.Writer;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * IOManager that queues output for backgrounded agent sessions.
 * Used when an agent is not in focus but still processing.
 * Output is buffered until the session regains focus and drains the queue.
 */
public class QueuedIOManager extends IOManager {

    private final Queue<String> inputQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> outputQueue = new ConcurrentLinkedQueue<>();

    public QueuedIOManager() {
        super();

        // Initialize pipes with String-based I/O
        this.inputPipe = this::readRaw;
        this.outputPipe = this::printRaw;
    }

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        // No-op for queued communication
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
     * Drain all queued output messages.
     * @return List of queued messages in order
     */
    public List<String> drainOutput() {
        List<String> drained = new ArrayList<>();
        String msg;
        while ((msg = outputQueue.poll()) != null) {
            drained.add(msg);
        }
        return drained;
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
        // Queued communication doesn't use delays or colors
        return new Writer(this);
    }

    @Override
    public void close() {
        inputQueue.clear();
        outputQueue.clear();
    }
}
