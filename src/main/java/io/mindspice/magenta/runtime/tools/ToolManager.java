package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.persistence.DatabaseService;
import io.mindspice.magenta.runtime.persistence.ToolCommand;
import io.mindspice.magenta.runtime.persistence.ToolCommandResult;
import io.mindspice.magenta.runtime.security.ToolSecurityDescriptor;
import io.mindspice.magenta.runtime.tools.builtin.AnnotatedBuiltInToolCatalog;
import io.mindspice.magenta.runtime.tools.builtin.FileTools;
import io.mindspice.magenta.runtime.tools.builtin.HistoryTools;
import io.mindspice.magenta.runtime.tools.builtin.ShellTools;
import io.mindspice.magenta.runtime.tools.builtin.SqliteTools;
import io.mindspice.magenta.runtime.tools.builtin.TodoTools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class ToolManager {

    private static final int DEFAULT_MAX_TOOL_OUTPUT_BYTES = 32_768;
    private static final int DEFAULT_MAX_FILE_READ_LINES = 200;
    private static final int DEFAULT_MAX_SQL_ROWS = 500;

    private final Map<String, Function<ToolRequest, ToolResult>> handlersByTool;
    private final Map<String, ToolSpecification> toolSpecificationsByName;
    private final Map<String, ToolSecurityDescriptor> securityDescriptorsByName;
    private final ToolExecutionSettings settings;

    public ToolManager(Map<String, Function<ToolRequest, ToolResult>> handlersByTool) {
        this(
                new ToolExecutionSettings(
                        Path.of("").toAbsolutePath().normalize(),
                        DEFAULT_MAX_TOOL_OUTPUT_BYTES,
                        DEFAULT_MAX_FILE_READ_LINES,
                        DEFAULT_MAX_SQL_ROWS,
                        true
                ),
                handlersByTool,
                Map.of(),
                Map.of()
        );
    }

    private ToolManager(
            ToolExecutionSettings settings,
            Map<String, Function<ToolRequest, ToolResult>> handlersByTool,
            Map<String, ToolSpecification> toolSpecificationsByName,
            Map<String, ToolSecurityDescriptor> securityDescriptorsByName
    ) {
        this.settings = settings;
        this.handlersByTool = handlersByTool == null ? Map.of() : Map.copyOf(handlersByTool);
        this.toolSpecificationsByName = toolSpecificationsByName == null ? Map.of() : Map.copyOf(toolSpecificationsByName);
        this.securityDescriptorsByName = securityDescriptorsByName == null ? Map.of() : Map.copyOf(securityDescriptorsByName);
    }

    public static ToolManager empty() {
        return new ToolManager(Map.of());
    }

    public static ToolManager withBuiltIns(RuntimeConfig runtimeConfig) {
        DatabaseService databaseService = new DatabaseService(runtimeConfig.workspaceRoot());
        return withBuiltIns(runtimeConfig, databaseService::execute, AnnotatedBuiltInToolCatalog.DelegationSupport.unsupported());
    }

    public static ToolManager withBuiltIns(
            RuntimeConfig runtimeConfig,
            AnnotatedBuiltInToolCatalog.DelegationSupport delegationSupport
    ) {
        DatabaseService databaseService = new DatabaseService(runtimeConfig.workspaceRoot());
        return withBuiltIns(runtimeConfig, databaseService::execute, delegationSupport);
    }

    public static ToolManager withBuiltIns(
            RuntimeConfig runtimeConfig,
            Function<ToolCommand, ToolCommandResult> toolCommandBridge,
            AnnotatedBuiltInToolCatalog.DelegationSupport delegationSupport
    ) {
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        Objects.requireNonNull(toolCommandBridge, "toolCommandBridge");

        ToolExecutionSettings settings = new ToolExecutionSettings(
                runtimeConfig.workspaceRoot(),
                runtimeConfig.maxToolOutputBytes(),
                runtimeConfig.maxFileReadLines(),
                runtimeConfig.maxSqlRows(),
                !runtimeConfig.security().devYoloOverride()
        );

        FileTools fileTools = new FileTools(settings);
        ShellTools shellTools = new ShellTools(settings);
        SqliteTools sqliteTools = new SqliteTools(settings);
        TodoTools todoTools = new TodoTools(settings, toolCommandBridge);
        HistoryTools historyTools = new HistoryTools(toolCommandBridge);
        AnnotatedBuiltInToolCatalog catalog = new AnnotatedBuiltInToolCatalog(
                fileTools,
                shellTools,
                sqliteTools,
                todoTools,
                historyTools,
                runtimeConfig.agentsById(),
                delegationSupport
        );
        AnnotatedRegistry annotatedRegistry = discoverAnnotatedRegistry(catalog);

        return new ToolManager(
                settings,
                annotatedRegistry.handlersByName(),
                annotatedRegistry.specificationsByName(),
                catalog.securityDescriptorsByName()
        );
    }

    public ToolResult execute(ToolRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.toolCall() == null) {
            return new ToolResult(
                    "",
                    "",
                    ToolPayloads.payload("failed", "validation_error", "Missing tool call", null),
                    true
            );
        }

        String toolName = request.toolCall().name() == null ? "" : request.toolCall().name().trim();
        if (toolName.isEmpty()) {
            return normalizeResult(request, ToolPayloads.failure(request, "validation_error", "Tool name is blank", null, true));
        }

        Function<ToolRequest, ToolResult> handler = handlersByTool.get(toolName);
        if (handler == null) {
            return normalizeResult(request, ToolResult.notHandled(request.toolCall()));
        }

        ToolResult result;
        try {
            result = handler.apply(request);
        } catch (Throwable t) {
            ObjectNode data = ToolPayloads.mapper().createObjectNode();
            data.put("errorType", t.getClass().getSimpleName());
            result = ToolPayloads.failure(request, "handler_exception", "Tool execution failed", data, true);
        }

        if (result == null) {
            result = ToolResult.notHandled(request.toolCall());
        }
        return normalizeResult(request, result);
    }

    private ToolResult normalizeResult(ToolRequest request, ToolResult raw) {
        String toolCallId = isBlank(raw.toolCallId()) ? request.toolCall().id() : raw.toolCallId();
        String toolName = isBlank(raw.toolName()) ? request.toolCall().name() : raw.toolName();

        String content = raw.content() == null ? "" : raw.content();
        String normalizedPayload = ToolPayloads.normalizePayload(content);

        int byteCount = normalizedPayload.getBytes(StandardCharsets.UTF_8).length;
        if (byteCount > settings.maxToolOutputBytes()) {
            ObjectNode data = ToolPayloads.mapper().createObjectNode();
            data.put("maxBytes", settings.maxToolOutputBytes());
            data.put("actualBytes", byteCount);
            return new ToolResult(
                    toolCallId,
                    toolName,
                    ToolPayloads.payload("failed", "output_too_large", "Tool output exceeded maxToolOutputBytes", data),
                    true
            );
        }

        return new ToolResult(toolCallId, toolName, normalizedPayload, raw.handled());
    }

    public List<ToolSpecification> toolSpecificationsFor(Iterable<String> toolIds) {
        if (toolSpecificationsByName.isEmpty()) {
            return List.of();
        }
        if (toolIds == null) {
            return List.copyOf(toolSpecificationsByName.values());
        }

        Set<String> seen = new LinkedHashSet<>();
        List<ToolSpecification> output = new ArrayList<>();
        for (String toolId : toolIds) {
            if (isBlank(toolId)) {
                continue;
            }
            String normalized = toolId.trim();
            if ("*".equals(normalized)) {
                return List.copyOf(toolSpecificationsByName.values());
            }
            if (!seen.add(normalized)) {
                continue;
            }
            ToolSpecification specification = toolSpecificationsByName.get(normalized);
            if (specification != null) {
                output.add(specification);
            }
        }
        return List.copyOf(output);
    }

    public Map<String, ToolSecurityDescriptor> securityDescriptorsByName() {
        return securityDescriptorsByName;
    }

    private static AnnotatedRegistry discoverAnnotatedRegistry(Object toolCatalog) {
        Objects.requireNonNull(toolCatalog, "toolCatalog");

        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(toolCatalog);
        Map<String, ToolSpecification> specsByName = new LinkedHashMap<>();
        for (ToolSpecification specification : specifications) {
            specsByName.put(specification.name(), specification);
        }

        Map<String, Function<ToolRequest, ToolResult>> handlersByName = new LinkedHashMap<>();
        for (Method method : toolCatalog.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            method.setAccessible(true);
            Tool tool = method.getAnnotation(Tool.class);
            String toolName = tool.name() == null || tool.name().isBlank() ? method.getName() : tool.name();

            if (handlersByName.putIfAbsent(toolName, request -> invokeAnnotated(toolCatalog, method, request)) != null) {
                throw new IllegalStateException("Duplicate @Tool handler name: " + toolName);
            }
        }

        return new AnnotatedRegistry(Map.copyOf(handlersByName), Map.copyOf(specsByName));
    }

    private static ToolResult invokeAnnotated(Object target, Method method, ToolRequest request) {
        Objects.requireNonNull(request, "request");
        JsonNode args = parseArgsObject(request.toolCall().argumentsJson());
        if (args == null) {
            return ToolPayloads.failure(request, "validation_error", "Tool arguments must be a JSON object", null, true);
        }

        Object[] invocationArgs = new Object[method.getParameterCount()];
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.isAnnotationPresent(ToolMemoryId.class)) {
                invocationArgs[i] = injectToolContextArg(parameter, request);
                continue;
            }

            String argName = parameter.getName();
            JsonNode node = args.get(argName);
            P annotation = parameter.getAnnotation(P.class);
            boolean required = annotation == null || annotation.required();

            if (node == null || node.isNull()) {
                if (required) {
                    return ToolPayloads.failure(
                            request,
                            "validation_error",
                            "Missing required argument: " + argName,
                            null,
                            true
                    );
                }
                invocationArgs[i] = defaultValue(parameter.getType());
                continue;
            }

            try {
                invocationArgs[i] = ToolPayloads.mapper().convertValue(
                        node,
                        ToolPayloads.mapper().getTypeFactory().constructType(parameter.getParameterizedType())
                );
            } catch (IllegalArgumentException conversionError) {
                return ToolPayloads.failure(
                        request,
                        "validation_error",
                        "Invalid argument type for: " + argName,
                        null,
                        true
                );
            }
        }

        try {
            Object result = method.invoke(target, invocationArgs);
            if (result == null) {
                return ToolResult.notHandled(request.toolCall());
            }
            if (result instanceof ToolResult toolResult) {
                return toolResult;
            }
            return ToolResult.handled(request.toolCall().id(), request.toolCall().name(), String.valueOf(result));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Annotated tool invocation failed", cause);
        } catch (Exception e) {
            throw new IllegalStateException("Annotated tool invocation failed", e);
        }
    }

    private static JsonNode parseArgsObject(String argsJson) {
        if (isBlank(argsJson)) {
            return ToolPayloads.mapper().createObjectNode();
        }
        try {
            JsonNode node = ToolPayloads.mapper().readTree(argsJson);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object injectToolContextArg(Parameter parameter, ToolRequest request) {
        Class<?> type = parameter.getType();
        if (ToolRequest.class.isAssignableFrom(type)) {
            return request;
        }
        if (String.class.equals(type)) {
            return request.sessionId();
        }
        return defaultValue(type);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (int.class.equals(type)) {
            return 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (float.class.equals(type)) {
            return 0.0f;
        }
        if (double.class.equals(type)) {
            return 0.0d;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        return null;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record AnnotatedRegistry(
            Map<String, Function<ToolRequest, ToolResult>> handlersByName,
            Map<String, ToolSpecification> specificationsByName
    ) {}
}
