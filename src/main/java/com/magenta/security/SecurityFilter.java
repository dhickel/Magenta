package com.magenta.security;

import com.magenta.io.IOManager;
import com.magenta.io.Message;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Functional security filter that works with Message ADT.
 * Filters accept Messages and return Messages (possibly Filtered).
 */
public record SecurityFilter(
    BiFunction<Message.Input, IOManager, Message> inputFilter,
    Function<Message.Output, Message> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, Message> toolFilter
) {

    /**
     * Identity filter - passes everything through unchanged.
     */
    public static SecurityFilter identity() {
        return new SecurityFilter(
            (input, io) -> input,  // Pass Input through
            output -> output,       // Pass Output through
            (req, io) -> Message.system("approved")  // Tools approved by default
        );
    }

    /**
     * Chain this filter with another.
     * If first filter blocks, second filter doesn't run.
     */
    public SecurityFilter andThen(SecurityFilter other) {
        return new SecurityFilter(
            (input, io) -> {
                Message first = this.inputFilter.apply(input, io);
                if (first.isFiltered()) return first;
                return first instanceof Message.Input inp
                    ? other.inputFilter.apply(inp, io)
                    : first;
            },
            output -> {
                Message first = this.outputFilter.apply(output);
                if (first.isFiltered()) return first;
                return first instanceof Message.Output out
                    ? other.outputFilter.apply(out)
                    : first;
            },
            (req, io) -> {
                Message first = this.toolFilter.apply(req, io);
                if (first.isFiltered()) return first;
                return other.toolFilter.apply(req, io);
            }
        );
    }

    /**
     * Curry the output filter into a simple function.
     */
    public Function<Message.Output, Message> curriedOutputFilter() {
        return this.outputFilter;
    }

    /**
     * Curry the input filter with an IOManager.
     */
    public Function<Message.Input, Message> curriedInputFilter(IOManager io) {
        return input -> this.inputFilter.apply(input, io);
    }

    /**
     * Curry the tool filter with an IOManager.
     */
    public Function<ToolExecutionRequest, Message> curriedToolFilter(IOManager io) {
        return req -> this.toolFilter.apply(req, io);
    }
}
