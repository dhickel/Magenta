package com.magenta.security;

import com.magenta.io.Command;
import com.magenta.io.IOManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Composable security filter containing method references for I/O and tool filtering.
 * Functional, data-driven approach to security policy enforcement.
 */
public record SecurityFilter(
    BiFunction<Command, IOManager, Command> inputFilter,
    Function<String, String> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, ToolExecutionRequest> toolFilter
) {

    /**
     * Identity filter that passes everything through unchanged.
     */
    public static SecurityFilter identity() {
        return new SecurityFilter(
            (cmd, io) -> cmd,
            Function.identity(),
            (tool, io) -> tool
        );
    }

    /**
     * Compose this filter with another, applying both in sequence.
     */
    public SecurityFilter andThen(SecurityFilter other) {
        return new SecurityFilter(
            (cmd, io) -> other.inputFilter.apply(this.inputFilter.apply(cmd, io), io),
            this.outputFilter.andThen(other.outputFilter),
            (tool, io) -> other.toolFilter.apply(this.toolFilter.apply(tool, io), io)
        );
    }
}
