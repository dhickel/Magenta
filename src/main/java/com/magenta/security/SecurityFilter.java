package com.magenta.security;

import com.magenta.io.IOManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Functional security filter using Optional for block/pass semantics.
 * - Optional.empty() = pass through
 * - Optional.of(reason) = blocked with reason
 */
public record SecurityFilter(
    BiFunction<String, IOManager, Optional<String>> inputFilter,
    Function<String, Optional<String>> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, Optional<String>> toolFilter
) {

    /**
     * Identity filter - passes everything through unchanged.
     */
    public static SecurityFilter identity() {
        return new SecurityFilter(
            (input, io) -> Optional.empty(),  // Pass through
            output -> Optional.empty(),        // Pass through
            (req, io) -> Optional.empty()      // Pass through
        );
    }

    /**
     * Chain this filter with another.
     * If first filter blocks, second filter doesn't run.
     */
    public SecurityFilter andThen(SecurityFilter other) {
        return new SecurityFilter(
            (input, io) -> {
                Optional<String> first = this.inputFilter.apply(input, io);
                if (first.isPresent()) return first;
                return other.inputFilter.apply(input, io);
            },
            output -> {
                Optional<String> first = this.outputFilter.apply(output);
                if (first.isPresent()) return first;
                return other.outputFilter.apply(output);
            },
            (req, io) -> {
                Optional<String> first = this.toolFilter.apply(req, io);
                if (first.isPresent()) return first;
                return other.toolFilter.apply(req, io);
            }
        );
    }

    /**
     * Curry the input filter with an IOManager.
     */
    public Function<String, Optional<String>> curriedInputFilter(IOManager io) {
        return input -> this.inputFilter.apply(input, io);
    }

    /**
     * Curry the tool filter with an IOManager.
     */
    public Function<ToolExecutionRequest, Optional<String>> curriedToolFilter(IOManager io) {
        return req -> this.toolFilter.apply(req, io);
    }
}
