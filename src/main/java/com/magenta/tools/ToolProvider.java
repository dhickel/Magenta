package com.magenta.tools;

import com.magenta.domain.TodoService;
import com.magenta.io.IOManager;
import com.magenta.memory.VectorStoreService;
import com.magenta.security.SecurityManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for instantiating tools based on configuration.
 * Centralizes tool creation and dependency injection.
 */
public class ToolProvider {

    private final TodoService todoService;
    private final VectorStoreService vectorStoreService;
    private final SecurityManager securityManager;

    public ToolProvider(TodoService todoService, VectorStoreService vectorStoreService,
                       SecurityManager securityManager) {
        this.todoService = todoService;
        this.vectorStoreService = vectorStoreService;
        this.securityManager = securityManager;
    }

    /**
     * Create tools based on a list of tool names from configuration.
     * Injects IOManager for context-aware security.
     */
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
            case "delegate" -> new DelegateTool(io, this);
            default -> {
                System.err.println("Warning: Unknown tool: " + toolName);
                yield null;
            }
        };
    }
}
