package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.InteractivePrompt;
import com.magenta.io.terminal.TerminalIOManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.io.IOException;
import java.util.Optional;


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
            (input, ioMgr) -> filterInput(input),
            this::filterOutput,
            (toolReq, ioMgr) -> filterTool(toolReq, ioMgr)
        );
    }

    /**
     * Filter input - returns Optional.empty() if allowed, Optional.of(reason) if blocked.
     */
    private Optional<String> filterInput(String content) {
        // Check blacklist
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (content.contains(blocked)) {
                    return Optional.of("Contains blocked pattern: " + blocked);
                }
            }
        }

        // Input is allowed
        return Optional.empty();
    }

    /**
     * Filter output - returns Optional.empty() if allowed, Optional.of(reason) if blocked.
     */
    private Optional<String> filterOutput(String content) {
        // For now, just pass through
        // Could add: sanitize sensitive data, content policies, etc.
        return Optional.empty();
    }

    /**
     * Filter tool - returns Optional.empty() if allowed, Optional.of(reason) if blocked.
     */
    private Optional<String> filterTool(ToolExecutionRequest request, IOManager io) {
        String toolName = request.name();
        String arguments = request.arguments();

        // Check blacklist
        if (config.blockedCommands() != null) {
            for (String blocked : config.blockedCommands()) {
                if (arguments.contains(blocked)) {
                    return Optional.of("Tool blocked: contains " + blocked);
                }
            }
        }

        // Check whitelist (auto-allow)
        if (config.alwaysAllowCommands() != null) {
            for (String allowed : config.alwaysAllowCommands()) {
                if (arguments.equals(allowed) || arguments.startsWith(allowed + " ")) {
                    return Optional.empty();  // Allowed
                }
            }
        }

        // Check if approval required
        if (config.approvalRequiredFor() != null && config.approvalRequiredFor().contains(toolName)) {
            boolean approved = requestUserApproval(toolName, arguments, io);
            if (approved) {
                return Optional.empty();  // Allowed
            } else {
                return Optional.of("User denied approval");
            }
        }

        // Default allow
        return Optional.empty();
    }

    /**
     * Resolve the JLine Terminal from an IOManager, if available.
     * Handles both TerminalIOManager and TerminalIOProxy.
     */
    private Optional<org.jline.terminal.Terminal> resolveTerminal(IOManager io) {
        if (io instanceof TerminalIOManager term) {
            return Optional.of(term.terminal());
        } else if (io instanceof TerminalIOManager.TerminalIOProxy proxy) {
            return Optional.of(proxy.terminal());
        }
        return Optional.empty();
    }

    private void securityAlert(String message, IOManager io) {
        if (io instanceof TerminalIOManager term) {
            term.securityAlert(message);
        } else if (io instanceof TerminalIOManager.TerminalIOProxy proxy) {
            proxy.printStyled(message, com.magenta.io.OutputStyle.SECURITY);
        } else {
            io.println(message);
        }
    }

    private void printBlocked(String command, String blockedRule, IOManager io) {
        securityAlert("[SECURITY] AUTOMATICALLY BLOCKED: " + command
            + " (Matches rule: " + blockedRule + ")", io);
    }

    private synchronized boolean requestUserApproval(String toolType, String command, IOManager io) {
        securityAlert("[SECURITY ALERT] Agent wants to execute:", io);
        io.println("Tool:    " + toolType);
        io.println("Command: " + command);

        // Use interactive confirm prompt if terminal is available
        var terminal = resolveTerminal(io);
        if (terminal.isPresent()) {
            try {
                return new InteractivePrompt(terminal.get())
                    .confirm("Allow execution?")
                    .defaultNo()
                    .show();
            } catch (IOException e) {
                // Fall through to text-based approval
            }
        }

        // Fallback: text-based approval
        String answer = io.read("Allow? [y/N]: ").content();
        if (answer != null && !answer.isEmpty()) {
            return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
        }
        return false;
    }
}
