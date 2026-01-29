package com.magenta.security;

import com.magenta.io.IOManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Composable security filter containing method references for I/O and tool filtering.
 * Functional, data-driven approach to security policy enforcement.
 * Input filtering now operates on raw strings before command parsing.
 */
public record SecurityFilter(
    BiFunction<String, IOManager, String> inputFilter,
    Function<String, String> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, ToolExecutionRequest> toolFilter
) {


    public static SecurityFilter identity() {
        return new SecurityFilter(
            (input, io) -> input,
            Function.identity(),
            (tool, io) -> tool
        );
    }


    public SecurityFilter andThen(SecurityFilter other) {
        return new SecurityFilter(
            (input, io) -> other.inputFilter.apply(this.inputFilter.apply(input, io), io),
            this.outputFilter.andThen(other.outputFilter),
            (tool, io) -> other.toolFilter.apply(this.toolFilter.apply(tool, io), io)
        );
    }
}
