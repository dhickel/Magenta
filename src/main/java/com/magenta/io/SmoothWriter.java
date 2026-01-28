package com.magenta.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writer with smooth character-by-character output.
 * Queues tokens and displays them with a configurable delay.
 * Reusable - resets state after complete()/error().
 */
public class SmoothWriter extends Writer {

    private final int charDelayMs;
    private BlockingQueue<String> queue;
    private CountDownLatch finished;
    private volatile boolean done;

    public SmoothWriter(OutputPipe pipe, Integer colorCode, int charDelayMs) {
        super(pipe, colorCode);
        this.charDelayMs = charDelayMs;
        resetState();
    }

    private void resetState() {
        this.queue = new LinkedBlockingQueue<>();
        this.finished = new CountDownLatch(1);
        this.done = false;
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
                                pipe.print(String.valueOf(c), colorCode);
                            } else {
                                pipe.print(String.valueOf(c));
                            }
                            Thread.sleep(charDelayMs);
                        }
                    }
                }
                pipe.println("");
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
        reset();
    }

    @Override
    public void error(Throwable t) {
        done = true;
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pipe.println("Error: " + t.getMessage());
        reset();
    }

    @Override
    protected void reset() {
        super.reset();
        resetState(); // Restart display thread with fresh state
    }
}
