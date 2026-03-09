package io.mindspice.magenta.runtime.context;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.compaction.CompactionStrategy;
import io.mindspice.magenta.runtime.persistence.CommonCommandResults;
import io.mindspice.magenta.runtime.persistence.SessionContextCommand;
import io.mindspice.magenta.runtime.persistence.SessionContextResult;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ContextManager {

    private final Function<SessionContextCommand, SessionContextResult> contextBridge;

    public ContextManager() {
        this(null);
    }

    public ContextManager(Function<SessionContextCommand, SessionContextResult> contextBridge) {
        this.contextBridge = contextBridge;
    }

    public Context newContext(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return new Context();
        }
        return newContext(List.of(systemPrompt));
    }

    public Context newContext(List<String> systemPrompts) {
        Context context = new Context();
        List<String> prompts = systemPrompts == null ? List.of() : List.copyOf(systemPrompts);
        for (String systemPrompt : prompts) {
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                context.append(new ContextElement.SystemMsg(systemPrompt));
            }
        }
        return context;
    }

    public Context copyContext(Context source) {
        Context copy = new Context();
        copy.appendAll(source.snapshot());
        return copy;
    }

    public Context loadContext(Context sourceOrNull, String systemPrompt) {
        return loadContext(UUID.randomUUID(), sourceOrNull, systemPrompt == null ? List.of() : List.of(systemPrompt));
    }

    public Context loadContext(UUID sessionId, Context sourceOrNull, List<String> systemPrompts) {
        Context context;
        if (sourceOrNull != null) {
            context = copyContext(sourceOrNull);
        } else {
            context = loadPersistedOrNew(sessionId, systemPrompts);
        }

        attachMutationListener(sessionId, context);
        return context;
    }

    public void initializeSessionPersistence(Session session) {
        Objects.requireNonNull(session, "session");
        if (contextBridge == null) {
            return;
        }

        List<ContextElement> snapshot = session.context().snapshot();
        SessionContextResult result = contextBridge.apply(new SessionContextCommand.InitializeSession(
                session.sessionId().toString(),
                session.agentId(),
                session.alias(),
                countLeadingSystemPrompts(snapshot),
                snapshot
        ));
        ensureSuccess(result, "Failed to initialize session persistence");
    }

    public int countLeadingSystemPrompts(List<ContextElement> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (ContextElement message : messages) {
            if (message instanceof ContextElement.SystemMsg) {
                count++;
                continue;
            }
            break;
        }
        return count;
    }

    public void storeContext(Context context) {
        Objects.requireNonNull(context, "context");
        // Persistence is mutation-driven through session-scoped context listeners.
    }

    public void compactIfNeeded(
            UUID sessionId,
            Context context,
            RuntimeConfig.ModelConfig modelConfig,
            Function<List<ContextElement>, String> summarizer
    ) {
        List<ContextElement> snapshot = context.snapshot();
        String tokenizerEncoding = modelConfig.tokenizerEncodingOrDefault();
        int tokens = SessionTokenEstimator.estimate(snapshot, tokenizerEncoding);
        if (tokens <= modelConfig.compactThreshold()) {
            return;
        }

        CompactionStrategy strategy = CompactionStrategy.forName(modelConfig.compactionStrategyOrDefault(), summarizer);
        List<ContextElement> compacted = strategy.run(sessionId, snapshot, modelConfig.compactThreshold(), tokenizerEncoding);
        context.replaceAll(compacted);
    }

    private Context loadPersistedOrNew(UUID sessionId, List<String> systemPrompts) {
        if (contextBridge == null || sessionId == null) {
            return newContext(systemPrompts);
        }

        SessionContextResult loaded = contextBridge.apply(new SessionContextCommand.LoadActiveContext(sessionId.toString()));
        if (loaded instanceof SessionContextResult.ActiveContextLoaded active && !active.messages().isEmpty()) {
            Context context = new Context();
            context.appendAll(active.messages());
            return context;
        }

        if (loaded instanceof CommonCommandResults.Failure) {
            return newContext(systemPrompts);
        }

        return newContext(systemPrompts);
    }

    private void attachMutationListener(UUID sessionId, Context context) {
        if (sessionId == null || contextBridge == null) {
            return;
        }

        context.setMutationListener(mutation -> {
            SessionContextResult result = switch (mutation) {
                case Context.Mutation.Append append -> contextBridge.apply(new SessionContextCommand.AppendMessage(
                        sessionId.toString(),
                        append.message()
                ));
                case Context.Mutation.AppendAll appendAll -> contextBridge.apply(new SessionContextCommand.AppendMessages(
                        sessionId.toString(),
                        appendAll.messages()
                ));
                case Context.Mutation.ReplaceAll replaceAll -> contextBridge.apply(new SessionContextCommand.ReplaceActiveContext(
                        sessionId.toString(),
                        replaceAll.messages(),
                        countLeadingSystemPrompts(replaceAll.messages())
                ));
            };
            ensureSuccess(result, "Failed to persist context mutation");
        });
    }

    private void ensureSuccess(SessionContextResult result, String prefix) {
        if (result instanceof CommonCommandResults.Failure failure) {
            throw new IllegalStateException(prefix + ": " + failure.code() + " - " + failure.message());
        }
    }
}
