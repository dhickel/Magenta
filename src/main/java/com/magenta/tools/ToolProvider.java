package com.magenta.tools;

/**
 * Tool provider functional interface.
 */
@FunctionalInterface
public interface ToolProvider {
    Object create(ToolContext context);
}
