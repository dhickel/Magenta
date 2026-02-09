package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.CommandSet;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityManager;
import com.magenta.task.TodoService;
import com.magenta.tools.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent holds configuration, model, and agent-specific security.
 * Tools are initialized when attached to a session with IOManager context.
 */
public final class Agent {
    private final AgentConfig config;
    private final StreamingChatLanguageModel model;
    private final CommandSet commands;

    // Tool state - initialized when attached to a session
    private List<ToolSpecification> toolSpecs = List.of();
    private Map<String, ToolExecutor> toolExecutors = Map.of();

    public Agent(AgentConfig config) {
        this.config = config;
        this.model = config.model().getAsStreamingChatModel();
        this.commands = CommandSet.empty();
    }

    public AgentConfig config() { return config; }
    public StreamingChatLanguageModel model() { return model; }
    public CommandSet commands() { return commands; }
    public List<ToolSpecification> toolSpecs() { return toolSpecs; }
    public Map<String, ToolExecutor> toolExecutors() { return toolExecutors; }

    /**
     * Initialize tools from config tool names with session context.
     * Call this when the agent is attached to a session with IOManager.
     */
    public void initTools(IOManager io, SessionId sessionId, ContextLimits limits, SessionAlias alias) {
        List<String> toolNames = config.tools();
        if (toolNames == null || toolNames.isEmpty()) {
            return;
        }

        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, ToolExecutor> executors = new HashMap<>();

        for (String toolName : toolNames) {
            Object toolInstance = createToolInstance(toolName, io, sessionId, limits, alias);
            if (toolInstance == null) continue;

            for (Method method : toolInstance.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                    specs.add(spec);
                    executors.put(spec.name(), new DefaultToolExecutor(toolInstance, method));
                }
            }
        }

        this.toolSpecs = List.copyOf(specs);
        this.toolExecutors = Map.copyOf(executors);
    }

    private Object createToolInstance(String toolName, IOManager io, SessionId sessionId,
                                       ContextLimits limits, SessionAlias alias) {
        return switch (toolName) {
            case "shell" -> new ShellTools(io);
            case "web" -> new WebTools();
            case "filesystem" -> new FileSystemTools();
            case "knowledge" -> new KnowledgeTools();
            case "git" -> new GitTools(io);
            case "context" -> new ContextTools(sessionId, limits);
            case "search" -> new SearchTools();
            case "agent" -> new AgentTools(alias.value());
            case "process" -> new ProcessTools();
            case "todo" -> new TodoTools(new TodoService());
            default -> null;
        };
    }

    /**
     * Create a SecurityFilter bound to a specific IOManager.
     * Call this when the agent is attached to a session with IOManager.
     */
    public SecurityFilter createSecurityFilterFor(IOManager io) {
        SecurityManager securityManager = SecurityManager.getInstance();
        securityManager.setConfig(config.security());
        return securityManager.createFilter(io);
    }
}
