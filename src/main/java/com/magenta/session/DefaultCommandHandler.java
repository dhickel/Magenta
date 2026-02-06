package com.magenta.session;

import com.magenta.agent.AgentMessage;
import com.magenta.agent.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.ContextManager;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.StatusBar;
import com.magenta.io.IOManager;
import com.magenta.security.SecurityFilter;
import com.magenta.task.TaskWorkflow;
import com.magenta.task.WorkflowTaskTemplate;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of CommandHandler.
 */
public class DefaultCommandHandler implements CommandHandler {

    @Override
    public void handle(Session session, Command command) {
        var io = session.io();
        switch (command) {
            case Command.Exit() -> session.setExit(true);
            case Command.Help() -> printHelp(io);
            case Command.Clear() -> { /* handled by IOManager */ }
            case Command.History() -> showHistory(session, 20);
            case Command.HistoryShow(int limit) -> showHistory(session, limit);
            case Command.HistorySearch(String query) -> searchHistory(session, query);
            case Command.Agent(String rawArg) -> switchAgent(session, rawArg);
            case Command.Sessions() -> listSessions(io);
            case Command.Agents() -> listAgents(io);
            case Command.Context(String subCmd, String arg) -> handleContext(session, subCmd, arg);
            case Command.WorkflowTask(String subCmd, String arg) -> handleWorkflowTask(session, subCmd, arg);
            case Command.Bash(String bashCmd) -> executeBash(session, bashCmd);
            case Command.Message(String target, String msg) -> sendMessage(session, target, msg);
            case Command.Messages() -> checkMessages(session);
            case Command.Delegate(String target, String template) -> delegateTask(session, target, template);
            case Command.Network() -> showNetwork(io);
            case Command.ConfigShow() -> showConfigSummary(io);
            case Command.ConfigShowSection(String section) -> showConfigSection(io, section);
            case Command.ConfigReload() -> reloadConfig(io);
            case Command.View(String viewName) -> handleView(session, viewName);
            case Command.Dashboard() -> handleView(session, "dashboard");
            case Command.Unknown(String raw) -> io.outputPipe().print("Unknown command: " + raw + "\n");
        }
    }

    private void printHelp(IOManager io) {
        io.outputPipe().print("Available commands:\n");
        io.outputPipe().print("  /exit, /quit, /q - Exit the session\n");
        io.outputPipe().print("  /help, /? - Show this help message\n");
        io.outputPipe().print("  /clear, /cls - Clear the screen\n");
        io.outputPipe().print("  /history [show <n>|search <query>] - View conversation history\n");
        io.outputPipe().print("  /agent <name> [alias] - Switch to a different agent/session\n");
        io.outputPipe().print("  /sessions - List active sessions\n");
        io.outputPipe().print("  /agents - List available agent configurations\n");
        io.outputPipe().print("  /context [status|compact|clear|archive|load] - Manage conversation context\n");
        io.outputPipe().print("  /task [list|show <id>|run <id>|clear|status] - Manage workflow tasks\n");
        io.outputPipe().print("  /config [show <section>|reload] - View or reload configuration\n");
        io.outputPipe().print("  /message <agent> <text> - Send message to agent\n");
        io.outputPipe().print("  /messages - Check for messages from other agents\n");
        io.outputPipe().print("  /delegate <agent> <template> - Delegate task to agent\n");
        io.outputPipe().print("  /network - View agent network status\n");
        io.outputPipe().print("  /view <name> - Switch terminal view (chat, dashboard)\n");
        io.outputPipe().print("  /dashboard - Show dashboard view (shorthand)\n");
        io.outputPipe().print("  !<command> - Execute bash command with security filtering\n");
    }

    private void switchAgent(Session session, String rawArg) {
        try {
            SessionManager sm = SessionManager.getInstance();
            String[] parts = rawArg.trim().split("\\s+", 2);
            String configName = parts[0];
            String aliasStr = parts.length > 1 ? parts[1] : configName;
            SessionAlias alias = SessionAlias.of(aliasStr);

            // Check if alias exists (switch)
            if (sm.getSession(alias) != null) {
                sm.switchToSession(alias);
                return;
            }

            // Validate agent config exists before creating
            var config = ConfigManager.config();
            var agentConfig = config.agents.get(configName);
            if (agentConfig == null) {
                session.io().outputPipe().print("Error: Unknown agent: " + configName + "\n");
                session.io().outputPipe().print("Use /agents to see available agents\n");
                return;
            }

            // Show agent info before creating session
            session.io().outputPipe().print("Creating session for agent: " + configName + "\n");
            session.io().outputPipe().print("─".repeat(60) + "\n");
            session.io().outputPipe().print("Model: " + agentConfig.model().modelName() + "\n");
            int toolCount = agentConfig.tools() != null ? agentConfig.tools().size() : 0;
            String toolsList = agentConfig.tools() != null ? String.join(", ", agentConfig.tools()) : "none";
            session.io().outputPipe().print("Tools: " + toolCount + " (" + toolsList + ")\n");
            session.io().outputPipe().print("Security: configured\n");
            session.io().outputPipe().print("─".repeat(60) + "\n");

            // Create and switch to new session
            sm.createSession(alias, configName);
            sm.switchToSession(alias);

        } catch (IllegalArgumentException e) {
            session.io().outputPipe().print("Error: " + e.getMessage() + "\n");
        } catch (IllegalStateException e) {
            session.io().outputPipe().print("Session switching not available: " + e.getMessage() + "\n");
        }
    }

    private void listSessions(IOManager io) {
        try {
            SessionManager sessionManager = SessionManager.getInstance();
            var sessionAliases = sessionManager.listActiveSessions();
            var currentAlias = sessionManager.getCurrentSessionAlias();

            io.outputPipe().print("Active sessions:\n");
            io.outputPipe().print("─".repeat(80) + "\n");
            io.outputPipe().print(String.format("%-15s %-10s %-15s %-10s\n",
                "Name", "Messages", "Context Tokens", "Current"));
            io.outputPipe().print("─".repeat(80) + "\n");

            if (sessionAliases.isEmpty()) {
                io.outputPipe().print("(none)\n");
            } else {
                for (String aliasStr : sessionAliases) {
                    SessionAlias alias = SessionAlias.of(aliasStr);
                    AgentSession session = sessionManager.getSession(alias);

                    int messageCount = 0;
                    int contextTokens = 0;

                    if (session != null) {
                        SessionId sessionId = session.sessionId();
                        ContextManager cm = ContextManager.getInstance();
                        Context context = cm.loadContext(sessionId);
                        messageCount = context.getElements().size();
                        contextTokens = context.totalEstimatedTokens();
                    }

                    String marker = aliasStr.equals(currentAlias) ? "*" : "";

                    io.outputPipe().print(String.format("%-15s %-10d %-15s %-10s\n",
                        aliasStr,
                        messageCount,
                        contextTokens > 0 ? contextTokens + " tokens" : "N/A",
                        marker));
                }
            }
            io.outputPipe().print("─".repeat(80) + "\n");
        } catch (IllegalStateException e) {
            io.outputPipe().print("Session management not available: " + e.getMessage() + "\n");
        }
    }

    private void listAgents(IOManager io) {
        try {
            var config = ConfigManager.config();
            var agents = config.agents;
            var currentAlias = SessionManager.getInstance().getCurrentSessionAlias();

            io.outputPipe().print("Available agents:\n");
            io.outputPipe().print("─".repeat(80) + "\n");
            io.outputPipe().print(String.format("%-15s %-15s %-8s %-12s\n",
                "Name", "Model", "Tools", "Security"));
            io.outputPipe().print("─".repeat(80) + "\n");

            for (var entry : agents.entrySet()) {
                var agent = entry.getValue();
                int toolCount = agent.tools() != null ? agent.tools().size() : 0;
                String marker = entry.getKey().equals(currentAlias) ? " *" : "";

                io.outputPipe().print(String.format("%-15s %-15s %-8d %-12s%s\n",
                    entry.getKey(),
                    truncate(agent.model().modelName(), 15),
                    toolCount,
                    "configured",
                    marker));
            }
            io.outputPipe().print("─".repeat(80) + "\n");
        } catch (IllegalStateException e) {
            io.outputPipe().print("Session management not available: " + e.getMessage() + "\n");
        }
    }

    private void handleContext(Session session, String subCmd, String arg) {
        IOManager io = session.io();

        // Context commands only work with AgentSession
        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("Context commands only available in agent sessions\n");
            return;
        }

        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);

        ContextLimits limits = new ContextLimits(
            agentSession.agent().config().model().maxContext(),
            agentSession.agent().config().model().compactThreshold()
        );

        switch (subCmd) {
            case "status" -> showContextStatus(io, context, limits, cm);
            case "compact" -> compactContext(io, context, limits, cm);
            case "clear" -> clearContext(io, context);
            case "archive" -> archiveContext(io, context, arg, cm);
            case "load" -> loadContext(io, sessionId, arg, limits, cm);
            default -> io.outputPipe().print("Unknown context subcommand: " + subCmd +
                "\nAvailable: status, compact, clear, archive <key>, load <key>\n");
        }
    }

    private void showContextStatus(IOManager io, Context context, ContextLimits limits, ContextManager cm) {
        var stats = cm.getStats(context, limits);
        io.outputPipe().print("Context Status:\n");
        io.outputPipe().print("  " + stats.toSummary() + "\n");
    }

    private void compactContext(IOManager io, Context context, ContextLimits limits, ContextManager cm) {
        int beforeTokens = context.totalEstimatedTokens();
        int beforeElements = context.getElements().size();

        cm.forceCompact(context, limits);

        int afterTokens = context.totalEstimatedTokens();
        int afterElements = context.getElements().size();

        io.outputPipe().print(String.format(
            "Context compacted: %d → %d elements, %d → %d tokens (saved %d tokens)\n",
            beforeElements, afterElements,
            beforeTokens, afterTokens,
            beforeTokens - afterTokens
        ));
    }

    private void clearContext(IOManager io, Context context) {
        int elementCount = context.getElements().size();
        context.setElements(java.util.List.of());
        io.outputPipe().print("Context cleared. Removed " + elementCount + " elements.\n");
    }

    private void archiveContext(IOManager io, Context context, String key, ContextManager cm) {
        if (key == null || key.isBlank()) {
            io.outputPipe().print("Usage: /context archive <key>\n");
            return;
        }

        cm.archiveContext(key, context);
        io.outputPipe().print("Context archived with key: " + key + " (" +
            context.getElements().size() + " elements, " +
            context.totalEstimatedTokens() + " tokens)\n");
    }

    private void loadContext(IOManager io, SessionId sessionId, String key, ContextLimits limits, ContextManager cm) {
        if (key == null || key.isBlank()) {
            io.outputPipe().print("Usage: /context load <key>\n");
            return;
        }

        var archived = cm.retrieveArchivedContext(key);
        if (archived.isEmpty()) {
            io.outputPipe().print("No archived context found for key: " + key + "\n");
            return;
        }

        String summaryText = "Loaded context '" + key + "' with " +
            archived.get().getElements().size() + " elements.";

        var summary = new com.magenta.context.ContextElement.Summary(
            summaryText, key, archived.get().getElements()
        );

        cm.append(sessionId, summary, limits);
        io.outputPipe().print(summaryText + "\n");
    }

    private void executeBash(Session session, String command) {
        IOManager io = session.io();

        // Only works with AgentSession (has SecurityFilter)
        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("Bash commands only available in agent sessions\n");
            return;
        }

        // Create ToolExecutionRequest for security filtering
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("bash")
            .arguments(command)
            .build();

        // Apply security filter - Optional.empty() = allowed, Optional.of(reason) = blocked
        SecurityFilter filter = agentSession.securityFilter();
        var blocked = filter.toolFilter().apply(request, io);

        if (blocked.isPresent()) {
            io.outputPipe().print("[FILTERED] " + blocked.get() + "\n");
            return;
        }

        // Execute bash command
        executeBashCommand(io, command);
    }

    private void handleWorkflowTask(Session session, String subCommand, String arg) {
        IOManager io = session.io();

        // Workflow task commands only work with AgentSession
        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("Workflow task commands only available in agent sessions\n");
            return;
        }

        var templates = ConfigManager.config().taskTemplates();

        switch (subCommand) {
            case "list" -> listTaskTemplates(io, templates);
            case "show" -> showTaskTemplate(io, templates, arg);
            case "run" -> {
                if (arg.isEmpty()) {
                    io.outputPipe().print("Usage: /task run <template>\n");
                    return;
                }
                try {
                    // For alpha: run with empty parameters (interactive prompting is future work)
                    TaskWorkflow task = createTaskFromTemplate(templates, arg, java.util.Map.of());
                    agentSession.setWorkflowTask(task);
                    io.outputPipe().print("Workflow task activated: " + task.name() + "\n");
                    io.outputPipe().print("Task will apply on next message. Clear context to start fresh: /context clear\n");
                } catch (IllegalArgumentException e) {
                    io.outputPipe().print("Error: " + e.getMessage() + "\n");
                }
            }
            case "clear" -> {
                agentSession.setWorkflowTask(null);
                io.outputPipe().print("Workflow task cleared.\n");
            }
            case "status" -> {
                TaskWorkflow task = agentSession.currentWorkflowTask();
                if (task == null) {
                    io.outputPipe().print("No active workflow task.\n");
                } else {
                    io.outputPipe().print("Active task: " + task.name() + "\n");
                    io.outputPipe().print("Type: " + task.type() + "\n");
                    io.outputPipe().print("Tools: " + String.join(", ", task.requiredTools()) + "\n");
                }
            }
            default -> io.outputPipe().print("Unknown task command. Try: list, show <id>, run <id>, clear, status\n");
        }
    }

    private void listTaskTemplates(IOManager io, java.util.Map<String, WorkflowTaskTemplate> templates) {

        if (templates.isEmpty()) {
            io.outputPipe().print("No task templates defined\n");
            return;
        }

        io.outputPipe().print("Available task templates:\n");
        io.outputPipe().print("─".repeat(80) + "\n");
        io.outputPipe().print(String.format("%-20s %-25s %-15s %s\n",
            "ID", "Name", "Type", "Parameters"));
        io.outputPipe().print("─".repeat(80) + "\n");

        for (var entry : templates.entrySet()) {
            var template = entry.getValue();
            int paramCount = template.parameterSpecs().size();
            String params = paramCount == 0 ? "none" : String.valueOf(paramCount);

            io.outputPipe().print(String.format("%-20s %-25s %-15s %s\n",
                entry.getKey(),
                truncate(template.name(), 25),
                template.type(),
                params));
        }
        io.outputPipe().print("─".repeat(80) + "\n");
    }

    private void showTaskTemplate(IOManager io, java.util.Map<String, WorkflowTaskTemplate> templates, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            io.outputPipe().print("Error: Template ID required\nUsage: /task show <id>\n");
            return;
        }

        var template = templates.get(templateId);
        if (template == null) {
            io.outputPipe().print("Error: Template not found: " + templateId + "\n");
            io.outputPipe().print("Use /task list to see available templates\n");
            return;
        }

        io.outputPipe().print("Task Template: " + templateId + "\n");
        io.outputPipe().print("─".repeat(60) + "\n");
        io.outputPipe().print("Name: " + template.name() + "\n");
        io.outputPipe().print("Type: " + template.type() + "\n");
        io.outputPipe().print("Description: " + template.description() + "\n\n");

        io.outputPipe().print("Required Tools:\n");
        for (String tool : template.requiredTools()) {
            io.outputPipe().print("  - " + tool + "\n");
        }
        io.outputPipe().print("\n");

        io.outputPipe().print("Parameters:\n");
        if (template.parameterSpecs().isEmpty()) {
            io.outputPipe().print("  (none)\n");
        } else {
            for (var param : template.parameterSpecs().entrySet()) {
                var spec = param.getValue();
                String required = spec.required() ? "required" : "optional";
                io.outputPipe().print(String.format("  - %s (%s, %s)\n",
                    param.getKey(), required, spec.type()));
            }
        }
        io.outputPipe().print("\n");

        io.outputPipe().print("Task Prompt:\n");
        io.outputPipe().print(template.taskPrompt() + "\n");
        io.outputPipe().print("─".repeat(60) + "\n");
    }

    private void executeBashCommand(IOManager io, String command) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        CommandLine cmdLine = CommandLine.parse("/bin/bash");
        cmdLine.addArgument("-c");
        cmdLine.addArgument(command, false);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);

        // Timeout of 60 seconds
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        executor.setWatchdog(watchdog);

        try {
            int exitValue = executor.execute(cmdLine);
            io.outputPipe().print("Exit Code: " + exitValue + "\n");
            io.outputPipe().print("Output:\n" + outputStream.toString() + "\n");
        } catch (IOException e) {
            io.outputPipe().print("Error executing command: " + e.getMessage() + "\n");
            String output = outputStream.toString();
            if (!output.isEmpty()) {
                io.outputPipe().print("Partial output:\n" + output + "\n");
            }
        }
    }

    private void sendMessage(Session session, String targetAgent, String message) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().outputPipe().print("Messaging only available in agent sessions\n");
            return;
        }

        try {
            AgentNetwork network = AgentNetwork.getInstance();
            String currentAlias = agentSession.alias().value();
            network.sendMessage(currentAlias, targetAgent, message);
            session.io().outputPipe().print("Message sent to " + targetAgent + "\n");
        } catch (Exception e) {
            session.io().outputPipe().print("Error: " + e.getMessage() + "\n");
        }
    }

    private void checkMessages(Session session) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().outputPipe().print("Messaging only available in agent sessions\n");
            return;
        }

        try {
            AgentNetwork network = AgentNetwork.getInstance();
            String currentAlias = agentSession.alias().value();
            java.util.List<AgentMessage> messages = network.getMessages(currentAlias);

            IOManager io = session.io();
            if (messages.isEmpty()) {
                io.outputPipe().print("No messages.\n");
                return;
            }

            io.outputPipe().print("You have " + messages.size() + " message(s):\n\n");
            for (AgentMessage msg : messages) {
                io.outputPipe().print("From: " + msg.from() + " [" + msg.type() + "]\n");
                io.outputPipe().print("Content: " + msg.content() + "\n");

                if (msg instanceof AgentMessage.Delegation delegation) {
                    io.outputPipe().print("Task: " + delegation.task().name() + "\n");
                }

                io.outputPipe().print("\n");
            }
        } catch (Exception e) {
            session.io().outputPipe().print("Error: " + e.getMessage() + "\n");
        }
    }

    private void delegateTask(Session session, String targetAgent, String templateKey) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().outputPipe().print("Task delegation only available in agent sessions\n");
            return;
        }

        try {
            var templates = ConfigManager.config().taskTemplates();
            TaskWorkflow task = createTaskFromTemplate(templates, templateKey, java.util.Map.of());

            AgentNetwork network = AgentNetwork.getInstance();
            String currentAlias = agentSession.alias().value();
            network.delegateTask(currentAlias, targetAgent, task);

            session.io().outputPipe().print("Task '" + task.name() + "' delegated to " + targetAgent + "\n");
        } catch (Exception e) {
            session.io().outputPipe().print("Error: " + e.getMessage() + "\n");
        }
    }

    private TaskWorkflow createTaskFromTemplate(java.util.Map<String, WorkflowTaskTemplate> templates,
                                                 String templateKey, java.util.Map<String, Object> parameters) {
        WorkflowTaskTemplate template = templates.get(templateKey);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateKey);
        }
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        return template.instantiate(id, parameters);
    }

    private void showNetwork(IOManager io) {
        try {
            AgentNetwork network = AgentNetwork.getInstance();
            var agents = network.listRegisteredAgents();

            if (agents.isEmpty()) {
                io.outputPipe().print("No agents in network.\n");
                return;
            }

            io.outputPipe().print("Agent Network Status:\n");
            for (SessionMeta meta : agents) {
                String alias = meta.sessionAlias().value();
                int msgCount = network.getMessageCount(alias);
                String msgInfo = msgCount > 0 ? " (" + msgCount + " messages)" : "";
                io.outputPipe().print("  " + alias + msgInfo + "\n");
            }
        } catch (Exception e) {
            io.outputPipe().print("Error: " + e.getMessage() + "\n");
        }
    }

    // === History Commands ===

    private void showHistory(Session session, int limit) {
        var io = session.io();

        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("History not available for this session type\n");
            return;
        }

        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);
        List<ContextElement> elements = context.getElements();

        if (elements.isEmpty()) {
            io.outputPipe().print("No conversation history\n");
            return;
        }

        int size = elements.size();
        int start = Math.max(0, size - limit);
        io.outputPipe().print("Recent conversation history (last " + (size - start) + " messages):\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (int i = start; i < size; i++) {
            ContextElement element = elements.get(i);
            String role = switch (element) {
                case ContextElement.User ignored -> "User";
                case ContextElement.Agent ignored -> "Agent";
                case ContextElement.System ignored -> "System";
                case ContextElement.Tool ignored -> "Tool";
                case ContextElement.Summary ignored -> "Summary";
            };

            String content = element.content();
            String preview = content.length() > 100 ? content.substring(0, 97) + "..." : content;
            io.outputPipe().print(String.format("[%d] %s: %s\n", i + 1, role, preview));
        }
        io.outputPipe().print("─".repeat(60) + "\n");
    }

    private void searchHistory(Session session, String query) {
        var io = session.io();

        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("History not available for this session type\n");
            return;
        }

        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);
        List<ContextElement> elements = context.getElements();

        String lowerQuery = query.toLowerCase();
        List<Integer> matches = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).content().toLowerCase().contains(lowerQuery)) {
                matches.add(i);
            }
        }

        if (matches.isEmpty()) {
            io.outputPipe().print("No matches found for: " + query + "\n");
            return;
        }

        io.outputPipe().print("Found " + matches.size() + " matches for '" + query + "':\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (int idx : matches) {
            ContextElement element = elements.get(idx);
            String role = switch (element) {
                case ContextElement.User ignored -> "User";
                case ContextElement.Agent ignored -> "Agent";
                case ContextElement.System ignored -> "System";
                case ContextElement.Tool ignored -> "Tool";
                case ContextElement.Summary ignored -> "Summary";
            };
            io.outputPipe().print(String.format("[%d] %s: %s\n", idx + 1, role, element.content()));
            io.outputPipe().print("─".repeat(60) + "\n");
        }
    }

    // === Enhanced Task Commands ===

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    // === Enhanced Agent/Session Commands ===
    // (listAgents and listSessions are already updated below)

    // === Config Commands ===

    private void showConfigSummary(IOManager io) {
        var config = ConfigManager.config();

        io.outputPipe().print("Configuration Summary:\n");
        io.outputPipe().print("─".repeat(60) + "\n");
        io.outputPipe().print("Agents:     " + config.agents.size() + "\n");
        io.outputPipe().print("Models:     " + config.models.size() + "\n");
        io.outputPipe().print("Endpoints:  " + config.endpoints.size() + "\n");
        io.outputPipe().print("Securities: " + config.securities.size() + "\n");
        io.outputPipe().print("Colors:     " + config.colorConfigs.size() + "\n");
        io.outputPipe().print("Tasks:      " + config.taskTemplates().size() + " templates\n");
        io.outputPipe().print("\n");
        io.outputPipe().print("Config file: " + System.getProperty("user.dir") + "/config.json\n");
        io.outputPipe().print("─".repeat(60) + "\n");
        io.outputPipe().print("Use '/config show <section>' to view details\n");
        io.outputPipe().print("Sections: agents, models, endpoints, securities, colors, tasks\n");
    }

    private void showConfigSection(IOManager io, String section) {
        var config = ConfigManager.config();

        switch (section.toLowerCase()) {
            case "agents" -> showAgentsConfig(io, config);
            case "models" -> showModelsConfig(io, config);
            case "endpoints" -> showEndpointsConfig(io, config);
            case "securities" -> showSecuritiesConfig(io, config);
            case "colors" -> showColorsConfig(io, config);
            case "tasks" -> showTasksConfig(io, config);
            default -> {
                io.outputPipe().print("Unknown section: " + section + "\n");
                io.outputPipe().print("Available: agents, models, endpoints, securities, colors, tasks\n");
            }
        }
    }

    private void showAgentsConfig(IOManager io, Config config) {
        io.outputPipe().print("Agents Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (var entry : config.agents.entrySet()) {
            var agent = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Model: " + agent.model().modelName() + "\n");
            io.outputPipe().print("  Tools: " + (agent.tools() != null ? String.join(", ", agent.tools()) : "none") + "\n");
            io.outputPipe().print("  Security: " + agent.security().approvalRequiredFor() + "\n");
            if (agent.colors() != null) {
                io.outputPipe().print("  Colors: configured\n");
            }
            io.outputPipe().print("  Cursor: \"" + agent.cursor() + "\"\n\n");
        }
    }

    private void showModelsConfig(IOManager io, Config config) {
        io.outputPipe().print("Models Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (var entry : config.models.entrySet()) {
            var model = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Model Name: " + model.modelName() + "\n");
            io.outputPipe().print("  Max Tokens: " + model.maxTokens() + "\n");
            io.outputPipe().print("  Max Context: " + model.maxContext() + "\n");
            io.outputPipe().print("  Temperature: " + model.temperature() + "\n\n");
        }
    }

    private void showEndpointsConfig(IOManager io, Config config) {
        io.outputPipe().print("Endpoints Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (var entry : config.endpoints.entrySet()) {
            var endpoint = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Type: " + endpoint.getClass().getSimpleName() + "\n");
            io.outputPipe().print("  Timeout: " + endpoint.timeoutSeconds() + "s\n\n");
        }
    }

    private void showSecuritiesConfig(IOManager io, Config config) {
        io.outputPipe().print("Securities Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (var entry : config.securities.entrySet()) {
            var security = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Approval Required: " + security.approvalRequiredFor() + "\n");
            io.outputPipe().print("  Always Allow: " + security.alwaysAllowCommands() + "\n");
            io.outputPipe().print("  Blocked: " + security.blockedCommands() + "\n\n");
        }
    }

    private void showColorsConfig(IOManager io, Config config) {
        io.outputPipe().print("Colors Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        for (var entry : config.colorConfigs.entrySet()) {
            var colors = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Error: " + colors.error() + "\n");
            io.outputPipe().print("  Warning: " + colors.warning() + "\n");
            io.outputPipe().print("  Success: " + colors.success() + "\n");
            io.outputPipe().print("  Info: " + colors.info() + "\n");
            io.outputPipe().print("  Agent: " + colors.agent() + "\n");
            io.outputPipe().print("  Prompt: " + colors.prompt() + "\n\n");
        }
    }

    private void showTasksConfig(IOManager io, Config config) {
        io.outputPipe().print("Task Templates Configuration:\n");
        io.outputPipe().print("─".repeat(60) + "\n");

        var templates = config.taskTemplates();
        if (templates.isEmpty()) {
            io.outputPipe().print("No task templates defined\n");
            return;
        }

        for (var entry : templates.entrySet()) {
            var template = entry.getValue();
            io.outputPipe().print(entry.getKey() + ":\n");
            io.outputPipe().print("  Name: " + template.name() + "\n");
            io.outputPipe().print("  Type: " + template.type() + "\n");
            io.outputPipe().print("  Required Tools: " + String.join(", ", template.requiredTools()) + "\n");
            io.outputPipe().print("  Parameters: " + template.parameterSpecs().size() + "\n\n");
        }
    }

    private void handleView(Session session, String viewName) {
        IOManager io = session.io();

        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("Views only available in agent sessions\n");
            return;
        }

        TerminalView view = switch (viewName.toLowerCase()) {
            case "chat" -> new TerminalView.Chat();
            case "dashboard" -> createDashboard();
            default -> {
                io.outputPipe().print("Unknown view: " + viewName + "\n");
                io.outputPipe().print("Available views: chat, dashboard\n");
                yield null;
            }
        };

        if (view != null) {
            agentSession.setView(view);
        }
    }

    private TerminalView createDashboard() {
        return TerminalView.builder()
            .header(ViewComponent.title("=== Magenta Dashboard ==="))
            .header(ViewComponent.blank())
            .content(new TerminalView.Dashboard())
            .footer(ViewComponent.separator())
            .footer(ViewComponent.styled(
                "Commands: /view chat | /exit-dashboard | /help",
                org.jline.utils.AttributedStyle.DEFAULT.faint()
            ))
            .statusBar(StatusBar::aligned, TerminalView.StatusPosition.BOTTOM_RIGHT)
            .build();
    }

    private void reloadConfig(IOManager io) {
        io.outputPipe().print("Config reload is not yet implemented\n");
        io.outputPipe().print("Note: Config hot-reload requires session restart to take effect\n");
        io.outputPipe().print("Consider restarting Magenta to apply config changes\n");
    }
}