package com.magenta.io;

import com.magenta.security.SecurityFilter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


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

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        // No-op for internal communication
    }

    // === Input (InputPipe) ===

    @Override
    public String read(String prompt) {
        String input = inputQueue.poll();
        if (input == null) {
            return null; // No input available
        }
        // Apply security filter to raw input
        String filtered = securityFilter.inputFilter().apply(input, this);
        return filtered;
    }


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


    public String readOutput() {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = outputQueue.poll()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }


    public String peekOutput() {
        return outputQueue.peek();
    }


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
