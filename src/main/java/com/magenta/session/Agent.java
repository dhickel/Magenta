package com.magenta.session;

import com.magenta.Magenta;
import com.magenta.config.Config.AgentConfig;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.CommandSet;
import com.magenta.security.SecurityFilter;
import com.magenta.manager.SecurityManager;
import com.magenta.task.TodoService;
import com.magenta.tools.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private final ChatLanguageModel blockingModel;
    private final CommandSet commands;

    private static final ToolRegistry registry = new ToolRegistry();

    static {
        registry.register("shell", ctx -> new ShellTools(ctx.io()));
        registry.register("web", ctx -> new WebTools());
        registry.register("filesystem", ctx -> new FileSystemTools());
        registry.register("knowledge", ctx -> new KnowledgeTools());
        registry.register("git", ctx -> new GitTools(ctx.io()));
        registry.register("context", ctx -> new ContextTools(ctx.sessionId(), ctx.limits(), ctx.magenta().contextManager()));
        registry.register("search", ctx -> new SearchTools());
        registry.register("agent", ctx -> new AgentTools(ctx.alias().value(), ctx.magenta().agentNetwork(), ctx.magenta().config()));
        registry.register("process", ctx -> new ProcessTools());
        registry.register("todo", ctx -> new TodoTools(new TodoService()));
        registry.register("planning", ctx -> new PlanningTools(ctx.sessionId().toString()));
        registry.register("code-execution", ctx -> {
            CodeExecutionTools tools = new CodeExecutionTools();
            // Apply security policies if security manager is present in context
            SecurityManager secMgr = ctx.magenta().securityManager();
            secMgr.registerToolPolicy("mavenBuild", com.magenta.security.SecurityPolicies.rateLimitPolicy(5));
            secMgr.registerToolPolicy("runTest", com.magenta.security.SecurityPolicies.rateLimitPolicy(10));
            return tools;
        });
    }

    // Tool state - initialized when attached to a session
    private List<ToolSpecification> toolSpecs = List.of();
    private Map<String, ToolExecutor> toolExecutors = Map.of();

    public Agent(AgentConfig config) {
        this.config = config;
        this.model = config.model().getAsStreamingChatModel();
        this.blockingModel = config.model().getAsChatModel();
        this.commands = CommandSet.empty();
    }

    public AgentConfig config() { return config; }
    public StreamingChatLanguageModel model() { return model; }
    public ChatLanguageModel blockingModel() { return blockingModel; }
    public CommandSet commands() { return commands; }
    public List<ToolSpecification> toolSpecs() { return toolSpecs; }
    public Map<String, ToolExecutor> toolExecutors() { return toolExecutors; }

    // Expose registry for testing or dynamic registration
    public static ToolRegistry registry() { return registry; }

    /**
     * Initialize tools from config tool names with session context.
     * Call this when the agent is attached to a session with IOManager.
     */
    public void initTools(IOManager io, SessionId sessionId, ContextLimits limits, SessionAlias alias, Magenta magenta) {
        List<String> toolNames = config.tools();
        if (toolNames == null || toolNames.isEmpty()) {
            return;
        }

        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, ToolExecutor> executors = new HashMap<>();

        ToolContext context = new ToolContext(io, sessionId, limits, alias, magenta);
        List<Object> toolInstances = registry.instantiateTools(toolNames, context);

        for (Object toolInstance : toolInstances) {
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

    /**
     * Create a SecurityFilter bound to a specific IOManager.
     * Call this when the agent is attached to a session with IOManager.
     */
    public SecurityFilter createSecurityFilterFor(IOManager io, SecurityManager securityManager) {
        // Fallback for calls without context (if any)
        securityManager.setConfig(config.security());
        return securityManager.createFilter(io);
    }

    public SecurityFilter createSecurityFilterFor(IOManager io, SecurityManager securityManager, ToolContext context) {
        securityManager.setConfig(config.security());
        return securityManager.createFilter(io, context);
    }
}
