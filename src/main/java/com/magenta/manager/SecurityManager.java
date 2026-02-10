package com.magenta.manager;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.InteractivePrompt;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityPolicies;
import com.magenta.security.ToolSecurityPolicy;
import com.magenta.tools.ToolContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class SecurityManager {

    private volatile SecurityConfig config;
    private final Map<String, ToolSecurityPolicy> toolPolicies = new ConcurrentHashMap<>();

    public SecurityManager() {
        // Default safe config (empty lists to avoid NPEs if not set)
        this.config = new SecurityConfig(
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList()
        );
    }

    public void setConfig(SecurityConfig config) {
        this.config = config;

        // Apply file access policy if configured
        if (config.allowedFilePaths() != null && !config.allowedFilePaths().isEmpty()) {
            List<Path> allowed = config.allowedFilePaths().stream()
                .map(java.nio.file.Paths::get)
                .toList();

            ToolSecurityPolicy filePolicy = SecurityPolicies.fileAccessPolicy(allowed);

            // Register for standard file tools
            registerToolPolicy("readFile", filePolicy);
            registerToolPolicy("writeFile", filePolicy);
            registerToolPolicy("deleteFile", filePolicy);
            registerToolPolicy("deleteDirectoryRecursive", filePolicy);
            registerToolPolicy("listDirectory", filePolicy);
            registerToolPolicy("createDirectory", filePolicy);
            registerToolPolicy("searchReplace", filePolicy);
            registerToolPolicy("diff", filePolicy);
        }
    }

    public SecurityConfig getConfig() {
        return config;
    }

    public void registerToolPolicy(String toolName, ToolSecurityPolicy policy) {
        toolPolicies.put(toolName, policy);
    }

    public SecurityFilter createFilter(IOManager io) {
        return createFilter(io, null);
    }

    public SecurityFilter createFilter(IOManager io, ToolContext context) {
        return new SecurityFilter(
            (input, ioMgr) -> filterInput(input),
            this::filterOutput,
            (req, ioMgr) -> filterTool(req, ioMgr, context)
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
    private Optional<String> filterTool(ToolExecutionRequest request, IOManager io, ToolContext context) {
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

        // Tool-specific policy checks
        ToolSecurityPolicy policy = toolPolicies.get(toolName);
        if (policy != null && context != null) {
            Optional<String> policyResult = policy.validate(request, context);
            if (policyResult.isPresent()) return policyResult;
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
