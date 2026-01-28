package com.magenta.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writer with smooth character-by-character output.
 * Queues tokens and displays them with a configurable delay.
 */
public class SmoothWriter extends Writer {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final int charDelayMs;
    private volatile boolean done = false;

    public SmoothWriter(IOManager io, Integer colorCode, int charDelayMs) {
        super(io, colorCode);
        this.charDelayMs = charDelayMs;
        startDisplayThread();
    }

    private void startDisplayThread() {
        Thread.startVirtualThread(() -> {
            try {
                while (!(done && queue.isEmpty())) {
                    String token = queue.poll(50, TimeUnit.MILLISECONDS);
                    if (token != null) {
                        for (char c : token.toCharArray()) {
                            if (colorCode != null) {
                                io.print(String.valueOf(c), colorCode);
                            } else {
                                io.print(String.valueOf(c));
                            }
                            Thread.sleep(charDelayMs);
                        }
                    }
                }
                io.println("");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });
    }

    @Override
    public void write(String token) {
        appendBuffer(token);
        queue.offer(token);
    }

    @Override
    public void complete() {
        done = true;
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void error(Throwable t) {
        done = true;
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        io.error("Error: " + t.getMessage());
    }
}
