package com.magenta.io.terminal;

import com.magenta.session.AgentSession;
import org.jline.reader.Candidate;

import java.util.List;

/**
 * Provides completion candidates for commands/arguments.
 * Session-aware for context-specific suggestions.
 */
@FunctionalInterface
public interface CompletionProvider {

    /**
     * Provide completion candidates based on current session.
     *
     * @param session Current agent session (for context)
     * @return List of completion candidates
     */
    List<Candidate> provide(AgentSession session);

    /**
     * No-op provider (no completions).
     */
    CompletionProvider NONE = (session) -> List.of();
}
