package com.magenta.tools;

import com.magenta.Magenta;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.session.SessionAlias;
import com.magenta.session.SessionId;

/**
 * Context for tool instantiation.
 */
public record ToolContext(
    IOManager io,
    SessionId sessionId,
    ContextLimits limits,
    SessionAlias alias,
    Magenta magenta
) {}
