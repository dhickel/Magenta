package com.magenta.tools;

import com.magenta.context.ContextManager;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.session.SessionId;
import dev.langchain4j.agent.tool.Tool;

import java.util.Optional;

public class ContextTools {
    private final SessionId sessionId;
    private final ContextLimits limits;

    public ContextTools(SessionId sessionId, ContextLimits limits) {
        this.sessionId = sessionId;
        this.limits = limits;
    }

    @Tool("View current context statistics including size, token usage, and whether compaction is needed.")
    public String viewContextStats() {
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);
        var stats = cm.getStats(context, limits);
        return stats.toSummary();
    }

    @Tool("Manually trigger context compaction to reduce token usage.")
    public String compactContext() {
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);

        int beforeTokens = context.totalEstimatedTokens();
        int beforeElements = context.getElements().size();

        cm.forceCompact(context, limits);

        int afterTokens = context.totalEstimatedTokens();
        int afterElements = context.getElements().size();

        return String.format(
            "Context compacted: %d → %d elements, %d → %d tokens (saved %d tokens)",
            beforeElements, afterElements,
            beforeTokens, afterTokens,
            beforeTokens - afterTokens
        );
    }

    @Tool("Clear the current context completely. Use with caution - this cannot be undone!")
    public String clearContext() {
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);
        int elementCount = context.getElements().size();
        context.setElements(java.util.List.of());
        return "Context cleared. Removed " + elementCount + " elements.";
    }

    @Tool("Archive the current active context with a specific key for later retrieval.")
    public String archiveCurrentContext(String key) {
        ContextManager cm = ContextManager.getInstance();
        Context current = cm.loadContext(sessionId);
        cm.archiveContext(key, current);
        return "Context archived with key: " + key + " (" + current.getElements().size() + " elements, "
            + current.totalEstimatedTokens() + " tokens)";
    }

    @Tool("Retrieve an archived context by key and append a summary of it to the current context.")
    public String retrieveContext(String key) {
        ContextManager cm = ContextManager.getInstance();
        Optional<Context> archived = cm.retrieveArchivedContext(key);
        if (archived.isEmpty()) {
            return "No context found for key: " + key;
        }

        String summaryText = "Loaded context '" + key + "' with " + archived.get().getElements().size() + " elements.";
        ContextElement summary = new ContextElement.Summary(summaryText, key, archived.get().getElements());

        cm.append(sessionId, summary, limits);
        return summaryText;
    }

    @Tool("Append a specific note or fact to the context explicitly.")
    public String rememberFact(String fact) {
        ContextManager.getInstance().append(sessionId, new ContextElement.User("Remember: " + fact), limits);
        return "Fact stored in context.";
    }
}