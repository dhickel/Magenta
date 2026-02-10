package com.magenta.tools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ToolMetrics {
    private static final Map<String, Metric> metrics = new ConcurrentHashMap<>();

    public static void record(String toolName, long durationMs, boolean success) {
        metrics.computeIfAbsent(toolName, k -> new Metric()).update(durationMs, success);
    }

    public static String getReport() {
        if (metrics.isEmpty()) return "No metrics available.";

        StringBuilder report = new StringBuilder("Tool Metrics:\n");
        metrics.forEach((name, metric) -> {
            long total = metric.totalCalls.get();
            if (total > 0) {
                long avg = metric.totalDurationMs.get() / total;
                report.append(String.format("- %s: %d calls, %d ms avg, %d errors\n",
                    name, total, avg, metric.errorCount.get()));
            }
        });
        return report.toString();
    }

    private static class Metric {
        final AtomicLong totalCalls = new AtomicLong(0);
        final AtomicLong totalDurationMs = new AtomicLong(0);
        final AtomicLong errorCount = new AtomicLong(0);

        void update(long duration, boolean success) {
            totalCalls.incrementAndGet();
            totalDurationMs.addAndGet(duration);
            if (!success) errorCount.incrementAndGet();
        }
    }
}
