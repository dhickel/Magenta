package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.Command;
import com.magenta.io.IOManager;
import com.magenta.io.TerminalIOManager;

/**
 * Instance-based security manager that enforces security policies for tool execution.
 * Tools call requireToolApproval() before executing potentially dangerous operations.
 */
public class SecurityManager {

    private final SecurityConfig config;

    public SecurityManager(SecurityConfig config) {
        this.config = config;
    }

    /**
     * Check if a tool execution should be allowed.
     * Applies blacklist, whitelist, and user approval logic.
     *
     * @param toolType The type of tool (e.g., "shell", "web_fetch")
     * @param command  The command or arguments being executed
     * @param io       IOManager for interactive approval prompts
     * @return true if allowed, false if blocked
     */
    public boolean requireToolApproval(String toolType, String command, IOManager io) {
        // 1. Blacklist (Blocking)
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (command.contains(blocked)) {
                    printBlocked(command, blocked, io);
                    return false;
                }
            }
        }

        // 2. Whitelist (Auto-Allow)
        if (config.alwaysAllowCommands() != null) {
            for (String allowed : config.alwaysAllowCommands()) {
                // Strict check: command starts with allowed + space or is exact match
                if (command.equals(allowed) || command.startsWith(allowed + " ")) {
                    return true;
                }
            }
        }

        // 3. Approval Check
        if (config.approvalRequiredFor() != null && config.approvalRequiredFor().contains(toolType)) {
            return requestUserApproval(toolType, command, io);
        }

        // Default allow if not configured
        return true;
    }

    /**
     * Create a security filter for future use with I/O filtering.
     * Currently contains placeholder logic; can be extended for input/output filtering.
     */
    public SecurityFilter createFilter(IOManager io) {
        return new SecurityFilter(
            (cmd, ioMgr) -> cmd,  // Pass-through for now
            output -> output,      // Pass-through for now
            (request, ioMgr) -> request  // Unused - tools call requireToolApproval directly
        );
    }

    private void printBlocked(String command, String blockedRule, IOManager io) {
        String msg = "[SECURITY] AUTOMATICALLY BLOCKED: " + command + " (Matches rule: " + blockedRule + ")";
        if (io instanceof TerminalIOManager term) {
            term.securityAlert(msg);
        } else {
            io.println(msg);
        }
    }

    private synchronized boolean requestUserApproval(String toolType, String command, IOManager io) {
        if (io instanceof TerminalIOManager term) {
            term.securityAlert("[SECURITY ALERT] Agent wants to execute:");
        } else {
            io.println("[SECURITY ALERT] Agent wants to execute:");
        }

        io.println("Tool:    " + toolType);
        io.println("Command: " + command);

        Command cmd = io.read("Allow? [y/N]: ");
        if (cmd instanceof Command.Message msg) {
            String response = msg.text();
            return response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes");
        }
        return false;
    }
}
