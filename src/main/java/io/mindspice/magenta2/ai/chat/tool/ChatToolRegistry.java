package io.mindspice.magenta2.ai.chat.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class ChatToolRegistry {
    private final Map<String, ToolCallback> toolsByName;

    public ChatToolRegistry(List<ToolCallback> toolCallbacks, List<ToolCallbackProvider> toolCallbackProviders) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (toolCallbacks != null) {
            callbacks.addAll(toolCallbacks);
        }
        if (toolCallbackProviders != null) {
            toolCallbackProviders.stream()
                .filter(provider -> provider.getToolCallbacks() != null)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .forEach(callbacks::add);
        }
        this.toolsByName = callbacks.stream()
            .collect(Collectors.toUnmodifiableMap(
                callback -> callback.getToolDefinition().name(),
                Function.identity(),
                (left, right) -> left
            ));
    }

    public List<ToolCallback> resolveApprovedTools(List<String> approvedToolNames) {
        if (approvedToolNames == null || approvedToolNames.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> resolved = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String toolName : approvedToolNames) {
            ToolCallback callback = toolsByName.get(toolName);
            if (callback == null) {
                unknown.add(toolName);
            } else {
                resolved.add(callback);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Approved tool names are not registered: " + String.join(", ", unknown));
        }
        return List.copyOf(resolved);
    }
}
