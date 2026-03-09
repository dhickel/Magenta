package io.mindspice.magenta.runtime.security;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.tools.ToolRequest;

import java.nio.file.Path;
import java.util.List;

public record ToolSecurityDescriptor(
        List<String> pathKeys,
        boolean requiresPath,
        String defaultPathWhenMissing,
        List<String> commandKeys,
        boolean requiresCommand,
        List<String> urlKeys,
        boolean requiresUrl,
        Validator validator
) {
    public ToolSecurityDescriptor {
        pathKeys = pathKeys == null ? List.of() : List.copyOf(pathKeys);
        defaultPathWhenMissing = defaultPathWhenMissing == null ? "" : defaultPathWhenMissing.trim();
        commandKeys = commandKeys == null ? List.of() : List.copyOf(commandKeys);
        urlKeys = urlKeys == null ? List.of() : List.copyOf(urlKeys);
    }

    public static ToolSecurityDescriptor path(List<String> keys, boolean required) {
        return path(keys, required, null);
    }

    public static ToolSecurityDescriptor path(List<String> keys, boolean required, String defaultPathWhenMissing) {
        return new ToolSecurityDescriptor(keys, required, defaultPathWhenMissing, List.of(), false, List.of(), false, null);
    }

    public static ToolSecurityDescriptor command(List<String> keys, boolean required) {
        return new ToolSecurityDescriptor(List.of(), false, "", keys, required, List.of(), false, null);
    }

    @FunctionalInterface
    public interface Validator {
        SecurityManager.Decision validate(ValidationContext context);
    }

    public record ValidationContext(
            ToolRequest request,
            SecurityManager.ToolPolicy policy,
            RuntimeConfig.SecurityMode mode,
            Path workspaceRoot,
            Path workspaceRootReal
    ) {
    }
}
