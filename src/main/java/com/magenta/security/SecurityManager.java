package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;
import com.magenta.io.Message;
import com.magenta.io.TerminalIOManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;


public class SecurityManager {

    private static SecurityManager instance;
    private SecurityConfig config;

    private SecurityManager() {
        // Default safe config (empty lists to avoid NPEs if not set)
        this.config = new SecurityConfig(
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList()
        );
    }

    public static synchronized SecurityManager getInstance() {
        if (instance == null) {
            instance = new SecurityManager();
        }
        return instance;
    }

    public void setConfig(SecurityConfig config) {
        this.config = config;
    }

    public SecurityConfig getConfig() {
        return config;
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


    public SecurityFilter createFilter(IOManager io) {
        return new SecurityFilter(
            (input, ioMgr) -> filterInput(input, ioMgr),
            this::filterOutput,
            (toolReq, ioMgr) -> filterTool(toolReq, ioMgr)
        );
    }

    private Message filterInput(Message.Input input, IOManager io) {
        String content = input.content();

        // Check blacklist
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (content.contains(blocked)) {
                    return Message.blocked(content, "Contains blocked pattern: " + blocked, Message.FilterType.INPUT);
                }
            }
        }

        // Input is allowed - return as-is
        return input;
    }

    private Message filterOutput(Message.Output output) {
        // For now, just pass through
        // Could add: sanitize sensitive data, content policies, etc.
        return output;
    }

    private Message filterTool(ToolExecutionRequest request, IOManager io) {
        String toolName = request.name();
        String arguments = request.arguments();

        // Check blacklist
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (arguments.contains(blocked)) {
                    return Message.blocked(arguments, "Tool blocked: contains " + blocked, Message.FilterType.TOOL);
                }
            }
        }

        // Check whitelist (auto-allow)
        if (config.alwaysAllowCommands() != null) {
            for (String allowed : config.alwaysAllowCommands()) {
                if (arguments.equals(allowed) || arguments.startsWith(allowed + " ")) {
                    return Message.system("approved");
                }
            }
        }

        // Check if approval required
        if (config.approvalRequiredFor() != null && config.approvalRequiredFor().contains(toolName)) {
            boolean approved = requestUserApproval(toolName, arguments, io);
            if (approved) {
                return Message.system("approved");
            } else {
                return Message.blocked(arguments, "User denied approval", Message.FilterType.TOOL);
            }
        }

        // Default allow
        return Message.system("approved");
    }

    private void printBlocked(String command, String blockedRule, IOManager io) {
        String msg = "[SECURITY] AUTOMATICALLY BLOCKED: " + command + " (Matches rule: " + blockedRule + ")";
        if (io instanceof TerminalIOManager term) {
            term.securityAlert(msg);
        } else {
            io.print(msg + "\n");
        }
    }

    private synchronized boolean requestUserApproval(String toolType, String command, IOManager io) {
        if (io instanceof TerminalIOManager term) {
            term.securityAlert("[SECURITY ALERT] Agent wants to execute:");
        } else {
            io.print("[SECURITY ALERT] Agent wants to execute:\n");
        }

        io.print("Tool:    " + toolType + "\n");
        io.print("Command: " + command + "\n");

        String response = io.read("Allow? [y/N]: ");
        if (response != null) {
            return response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes");
        }
        return false;
    }
}
