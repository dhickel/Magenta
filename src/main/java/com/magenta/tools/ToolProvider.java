package com.magenta.tools;

import com.magenta.context.policy.ContextLimits;
import com.magenta.domain.TodoService;
import com.magenta.io.IOManager;
import com.magenta.memory.VectorStoreService;
import com.magenta.security.SecurityManager;
import com.magenta.session.SessionId;

import java.util.ArrayList;
import java.util.List;


public class ToolProvider {

    private final TodoService todoService;
    private final VectorStoreService vectorStoreService;
    private final SecurityManager securityManager;
    private final SessionId sessionId;
    private final ContextLimits contextLimits;

    public ToolProvider(TodoService todoService, VectorStoreService vectorStoreService,
                       SecurityManager securityManager, SessionId sessionId, ContextLimits contextLimits) {
        this.todoService = todoService;
        this.vectorStoreService = vectorStoreService;
        this.securityManager = securityManager;
        this.sessionId = sessionId;
        this.contextLimits = contextLimits;
    }


    public Object[] createTools(List<String> toolNames, IOManager io) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new Object[0];
        }

        List<Object> tools = new ArrayList<>();
        for (String toolName : toolNames) {
            Object tool = createTool(toolName, io);
            if (tool != null) {
                tools.add(tool);
            }
        }
        return tools.toArray();
    }

    private Object createTool(String toolName, IOManager io) {
        return switch (toolName.toLowerCase()) {
            case "shell" -> new ShellTools(securityManager, io);
            case "filesystem", "file_system", "fs" -> new FileSystemTools();
            case "web" -> new WebTools(securityManager, io);
            case "todo" -> todoService != null ? new TodoTools(todoService) : null;
            case "knowledge" -> vectorStoreService != null ? new KnowledgeTools(vectorStoreService) : null;
            case "context", "memory" -> new ContextTools(sessionId, contextLimits);
            default -> {
                System.err.println("Warning: Unknown tool: " + toolName);
                yield null;
            }
        };
    }
}
