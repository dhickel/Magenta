package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;

import java.lang.management.*;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessTools {

    private static final int MAX_PROCESSES = 50;

    @Tool("List currently running processes with PID, user, and command")
    public String listProcesses() {
        try {
            List<ProcessInfo> processes = ProcessHandle.allProcesses()
                .limit(MAX_PROCESSES)
                .map(ph -> new ProcessInfo(
                    ph.pid(),
                    ph.info().user().orElse("unknown"),
                    ph.info().command().orElse("unknown"),
                    ph.info().arguments().map(args -> String.join(" ", args)).orElse("")
                ))
                .collect(Collectors.toList());

            if (processes.isEmpty()) {
                return "No processes found.";
            }

            StringBuilder output = new StringBuilder();
            output.append(String.format("%-8s %-15s %-50s\n", "PID", "USER", "COMMAND"));
            output.append("-".repeat(73)).append("\n");

            for (ProcessInfo proc : processes) {
                String command = proc.command;
                if (!proc.args.isEmpty()) {
                    command = command + " " + proc.args;
                }
                // Truncate long commands
                if (command.length() > 50) {
                    command = command.substring(0, 47) + "...";
                }

                output.append(String.format("%-8d %-15s %-50s\n",
                    proc.pid,
                    truncate(proc.user, 15),
                    command));
            }

            output.append("\nShowing up to ").append(MAX_PROCESSES).append(" processes.");

            return output.toString();

        } catch (Exception e) {
            return "Error listing processes: " + e.getMessage();
        }
    }

    @Tool("Get system information including OS, architecture, Java version, available processors, and memory")
    public String systemInfo() {
        try {
            StringBuilder info = new StringBuilder();
            info.append("System Information\n");
            info.append("=".repeat(50)).append("\n\n");

            // Operating System
            info.append("Operating System:\n");
            info.append("  Name: ").append(System.getProperty("os.name")).append("\n");
            info.append("  Version: ").append(System.getProperty("os.version")).append("\n");
            info.append("  Architecture: ").append(System.getProperty("os.arch")).append("\n\n");

            // Java Runtime
            info.append("Java Runtime:\n");
            info.append("  Version: ").append(System.getProperty("java.version")).append("\n");
            info.append("  Vendor: ").append(System.getProperty("java.vendor")).append("\n");
            info.append("  Home: ").append(System.getProperty("java.home")).append("\n\n");

            // Hardware
            Runtime runtime = Runtime.getRuntime();
            info.append("Hardware:\n");
            info.append("  Available Processors: ").append(runtime.availableProcessors()).append("\n");
            info.append("  Total Memory: ").append(formatBytes(runtime.totalMemory())).append("\n");
            info.append("  Free Memory: ").append(formatBytes(runtime.freeMemory())).append("\n");
            info.append("  Max Memory: ").append(formatBytes(runtime.maxMemory())).append("\n\n");

            // User
            info.append("User:\n");
            info.append("  Name: ").append(System.getProperty("user.name")).append("\n");
            info.append("  Home: ").append(System.getProperty("user.home")).append("\n");
            info.append("  Working Directory: ").append(System.getProperty("user.dir")).append("\n");

            return info.toString();

        } catch (Exception e) {
            return "Error getting system information: " + e.getMessage();
        }
    }

    @Tool("Monitor current resource usage including CPU load, memory usage, and thread count")
    public String monitorResources() {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("Resource Usage\n");
            stats.append("=".repeat(50)).append("\n\n");

            // CPU Information
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            stats.append("CPU:\n");
            stats.append("  Available Processors: ").append(osBean.getAvailableProcessors()).append("\n");
            stats.append("  System Load Average: ");
            double loadAvg = osBean.getSystemLoadAverage();
            if (loadAvg >= 0) {
                stats.append(String.format("%.2f", loadAvg)).append("\n");
            } else {
                stats.append("Not available\n");
            }

            // Add process CPU load if available (for some JVM implementations)
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                stats.append("  Process CPU Load: ").append(String.format("%.2f%%", sunOsBean.getProcessCpuLoad() * 100)).append("\n");
                stats.append("  System CPU Load: ").append(String.format("%.2f%%", sunOsBean.getSystemCpuLoad() * 100)).append("\n");
            }
            stats.append("\n");

            // Memory Information
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            stats.append("Heap Memory:\n");
            stats.append("  Used: ").append(formatBytes(heapUsage.getUsed())).append("\n");
            stats.append("  Committed: ").append(formatBytes(heapUsage.getCommitted())).append("\n");
            stats.append("  Max: ").append(formatBytes(heapUsage.getMax())).append("\n");
            stats.append("  Usage: ").append(String.format("%.2f%%",
                (double) heapUsage.getUsed() / heapUsage.getMax() * 100)).append("\n\n");

            stats.append("Non-Heap Memory:\n");
            stats.append("  Used: ").append(formatBytes(nonHeapUsage.getUsed())).append("\n");
            stats.append("  Committed: ").append(formatBytes(nonHeapUsage.getCommitted())).append("\n");
            stats.append("  Max: ");
            if (nonHeapUsage.getMax() > 0) {
                stats.append(formatBytes(nonHeapUsage.getMax())).append("\n");
            } else {
                stats.append("Unlimited\n");
            }
            stats.append("\n");

            // Thread Information
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            stats.append("Threads:\n");
            stats.append("  Current Thread Count: ").append(threadBean.getThreadCount()).append("\n");
            stats.append("  Peak Thread Count: ").append(threadBean.getPeakThreadCount()).append("\n");
            stats.append("  Total Started Threads: ").append(threadBean.getTotalStartedThreadCount()).append("\n");
            stats.append("  Daemon Thread Count: ").append(threadBean.getDaemonThreadCount()).append("\n\n");

            // Garbage Collection
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            stats.append("Garbage Collection:\n");
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                stats.append("  ").append(gcBean.getName()).append(":\n");
                stats.append("    Collection Count: ").append(gcBean.getCollectionCount()).append("\n");
                stats.append("    Collection Time: ").append(gcBean.getCollectionTime()).append(" ms\n");
            }

            return stats.toString();

        } catch (Exception e) {
            return "Error monitoring resources: " + e.getMessage();
        }
    }

    // ========== Helper Methods ==========

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    // Simple record to hold process information
    private record ProcessInfo(long pid, String user, String command, String args) {}
}
