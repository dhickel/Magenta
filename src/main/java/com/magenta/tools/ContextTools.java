package com.magenta.tools;

import com.magenta.context.manager.ContextManager;
import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;
import com.magenta.context.policy.ContextLimits;
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

    @Tool("Archive the current active context with a specific key for later retrieval.")
    public String archiveCurrentContext(String key) {
        ContextManager cm = ContextManager.getInstance();
        Context current = cm.loadContext(sessionId);
        cm.archiveContext(key, current);
        return "Context archived with key: " + key;
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
        return "Fact stored.";
    }
}