package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.persistence.DatabaseService;
import io.mindspice.magenta.runtime.persistence.SessionContextCommand;
import io.mindspice.magenta.runtime.persistence.SessionContextResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContextManagerCompactionIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void summarizeCompactionExcludesSystemAndRetainsRecentTail() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        context.append(new ContextElement.SystemTaskMsg("task"));
        for (int i = 0; i < 20; i++) {
            context.append(new ContextElement.UserMsg("user-" + i + "-" + "x".repeat(220)));
            context.append(new ContextElement.AssistantMsg("assistant-" + i + "-" + "y".repeat(120), List.of()));
        }
        List<ContextElement> original = context.snapshot();

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                1200,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        AtomicReference<List<ContextElement>> summarizedInput = new AtomicReference<>(List.of());
        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> {
                    summarizedInput.set(new ArrayList<>(messages));
                    return "summary text";
                }
        );

        List<ContextElement> compacted = context.snapshot();
        assertThat(outcome).isPresent();
        assertThat(summarizedInput.get()).allMatch(message -> !(message instanceof ContextElement.SystemElement));
        assertThat(compacted.getFirst()).isEqualTo(new ContextElement.SystemCoreMsg("system"));
        assertThat(compacted.get(1)).isEqualTo(new ContextElement.SystemTaskMsg("task"));
        assertThat(compacted.get(2)).isInstanceOf(ContextElement.SummaryMsg.class);
        List<ContextElement> preservedTail = compacted.subList(3, compacted.size());
        assertThat(preservedTail).hasSizeGreaterThanOrEqualTo(10);
        assertThat(preservedTail.getFirst())
                .matches(message -> message instanceof ContextElement.UserMsg || message instanceof ContextElement.InboundMsg);
        assertThat(preservedTail).isEqualTo(original.subList(original.size() - preservedTail.size(), original.size()));
    }

    @Test
    void summarizeCompactionPreservesStateSystemMessageOutsideSummary() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        context.append(new ContextElement.SystemStateMsg("{\"kind\":\"state_snapshot\",\"version\":1,\"toolUsage\":{}}"));
        for (int i = 0; i < 18; i++) {
            context.append(new ContextElement.UserMsg("user-" + i + "-" + "x".repeat(180)));
            context.append(new ContextElement.AssistantMsg("assistant-" + i + "-" + "y".repeat(120), List.of()));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                1000,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> "summary text"
        );

        assertThat(outcome).isPresent();
        List<ContextElement> compacted = context.snapshot();
        List<ContextElement.SystemStateMsg> stateMessages = compacted.stream()
                .filter(ContextElement.SystemStateMsg.class::isInstance)
                .map(ContextElement.SystemStateMsg.class::cast)
                .toList();
        assertThat(stateMessages).hasSize(1);

        assertThat(compacted.get(2)).isInstanceOf(ContextElement.SummaryMsg.class);
        ContextElement.SummaryMsg summary = (ContextElement.SummaryMsg) compacted.get(2);
        assertThat(summary.content()).contains("summary text");
    }

    @Test
    void summarizeCompactionNoopsWhenReductionIsTooSmall() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        for (int i = 0; i < 6; i++) {
            context.append(new ContextElement.UserMsg("message-" + i + "-xxxxxxxxxxxxxxxxxxxxxxxx"));
        }
        List<ContextElement> original = context.snapshot();

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                20,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> "tiny"
        );

        assertThat(outcome).isEmpty();
        assertThat(context.snapshot()).isEqualTo(original);
    }

    @Test
    void summarizeCompactionUsesDeterministicFallbackWhenModelSummaryIsBlank() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        context.append(new ContextElement.AssistantMsg("status checkpoint", List.of()));
        for (int i = 0; i < 40; i++) {
            context.append(new ContextElement.AssistantMsg("", List.of()));
            context.append(new ContextElement.ToolMsg(
                    "call-" + i,
                    "todo_update",
                    "{\"status\":\"ok\",\"data\":{\"todo\":{\"todoId\":\"todo-1\",\"status\":\"done\"}},\"note\":\""
                            + "x".repeat(900) + "\"}"
            ));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                3000,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> "   "
        );

        assertThat(outcome).isPresent();
        List<ContextElement> compacted = context.snapshot();
        assertThat(compacted.get(1)).isInstanceOf(ContextElement.SummaryMsg.class);
        ContextElement.SummaryMsg summary = (ContextElement.SummaryMsg) compacted.get(1);
        assertThat(summary.content()).contains("Errors: summary_model_empty");
        assertThat(summary.content()).contains("repetitive_tool_pattern");
        assertThat(summary.content()).contains("Next: Refresh TODO state");
    }

    @Test
    void summarizeCompactionDoesNotSkipSummarizationWhenFirstNonSystemIsUser() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        context.append(new ContextElement.UserMsg("Continue with the task."));
        for (int i = 0; i < 28; i++) {
            context.append(new ContextElement.AssistantMsg("", List.of()));
            context.append(new ContextElement.ToolMsg(
                    "call-u-" + i,
                    "todo_update",
                    "{\"status\":\"ok\",\"data\":{\"todo\":{\"todoId\":\"todo-u\",\"status\":\"open\"}},\"note\":\""
                            + "y".repeat(600) + "\"}"
            ));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                2500,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> "summary text"
        );

        assertThat(outcome).isPresent();
        List<ContextElement> compacted = context.snapshot();
        assertThat(compacted.get(1)).isInstanceOf(ContextElement.SummaryMsg.class);
    }

    @Test
    void summarizeCompactionShrinksHeavyRecentToolPayloadsBeforeFallback() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        for (int i = 0; i < 22; i++) {
            context.append(new ContextElement.UserMsg("user-" + i + "-" + "x".repeat(140)));
            context.append(new ContextElement.AssistantMsg("assistant-" + i + "-" + "y".repeat(90), List.of()));
        }
        for (int i = 0; i < 8; i++) {
            context.append(new ContextElement.AssistantMsg("", List.of()));
            context.append(new ContextElement.ToolMsg(
                    "call-heavy-" + i,
                    "sqlite_query",
                    "{\"status\":\"ok\",\"code\":\"ok\",\"message\":\"Query completed\",\"data\":{\"rows\":[{\"content\":\""
                            + "z".repeat(9_000)
                            + "\"}]}}"
            ));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                24_000,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> "summary text"
        );

        assertThat(outcome).isPresent();
        assertThat(outcome.get().summarizedCount()).isGreaterThan(0);
        assertThat(outcome.get().preservedRecentCount()).isLessThanOrEqualTo(10);

        List<ContextElement> compacted = context.snapshot();
        assertThat(compacted.get(1)).isInstanceOf(ContextElement.SummaryMsg.class);
        assertThat(SessionTokenEstimator.estimate(compacted)).isLessThanOrEqualTo(modelConfig.compactThreshold());
        assertThat(compacted.stream()
                .filter(ContextElement.ToolMsg.class::isInstance)
                .map(ContextElement.ToolMsg.class::cast)
                .anyMatch(ContextElement.ToolMsg::contentTruncated))
                .isTrue();
    }

    @Test
    void maxContextGuardCompactsWhenThresholdCompactionDoesNotRun() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        for (int i = 0; i < 18; i++) {
            context.append(new ContextElement.UserMsg("user-" + i + "-" + "x".repeat(140)));
            context.append(new ContextElement.AssistantMsg("assistant-" + i + "-" + "y".repeat(90), List.of()));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                320,
                4096,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.enforceMaxContext(
                UUID.randomUUID(),
                context,
                modelConfig
        );

        assertThat(outcome).isPresent();
        assertThat(outcome.get().strategy()).isEqualTo("max_context_guard");
        int estimated = SessionTokenEstimator.estimate(context.snapshot(), modelConfig.tokenizerEncodingOrDefault());
        assertThat(estimated).isLessThanOrEqualTo(modelConfig.maxContext());
    }

    @Test
    void maxContextGuardLeavesOverLimitContextWhenOnlySystemMessageCanBeKept() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system-" + "x".repeat(6000)));

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                64,
                4096,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.enforceMaxContext(
                UUID.randomUUID(),
                context,
                modelConfig
        );

        assertThat(outcome).isEmpty();
        int estimated = SessionTokenEstimator.estimate(context.snapshot(), modelConfig.tokenizerEncodingOrDefault());
        assertThat(estimated).isGreaterThan(modelConfig.maxContext());
    }

    @Test
    void rollingWindowRespectsTargetTokensWhenAssistantContainsLargeToolArgs() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("system"));
        context.append(new ContextElement.AssistantMsg(
                "",
                List.of(new ContextElement.ToolCall("id-1", "read_file", "{\"blob\":\"" + "x".repeat(1000) + "\"}"))
        ));

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                5,
                0.0,
                "rolling_window",
                "cl100k_base",
                false,
                false,
                true
        );

        contextManager.compactIfNeeded(UUID.randomUUID(), context, modelConfig, messages -> "unused");
        assertThat(SessionTokenEstimator.estimate(context.snapshot())).isLessThanOrEqualTo(5);
    }

    @Test
    void compactionPersistsCoreAgentAndTaskSystemPrompts() {
        DatabaseService databaseService = new DatabaseService(tempDir);
        ContextManager contextManager = new ContextManager(databaseService::execute);
        UUID sessionId = UUID.randomUUID();

        Context context = contextManager.loadContext(
                sessionId,
                null,
                List.of(
                        new ContextElement.SystemCoreMsg("Core system prompt"),
                        new ContextElement.SystemAgentMsg("Agent system prompt"),
                        new ContextElement.SystemTaskMsg("Task system prompt")
                )
        );
        for (int i = 0; i < 28; i++) {
            context.append(new ContextElement.UserMsg("user-" + i + "-" + "x".repeat(220)));
            context.append(new ContextElement.AssistantMsg("assistant-" + i + "-" + "y".repeat(140), List.of()));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                1200,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        Optional<ContextManager.CompactionOutcome> outcome = contextManager.compactIfNeeded(
                sessionId,
                context,
                modelConfig,
                messages -> "summary text"
        );

        assertThat(outcome).isPresent();
        assertThat(context.snapshot()).startsWith(
                new ContextElement.SystemCoreMsg("Core system prompt"),
                new ContextElement.SystemAgentMsg("Agent system prompt"),
                new ContextElement.SystemTaskMsg("Task system prompt")
        );

        SessionContextResult loaded = databaseService.execute(new SessionContextCommand.LoadActiveContext(sessionId.toString()));
        assertThat(loaded).isInstanceOf(SessionContextResult.ActiveContextLoaded.class);
        SessionContextResult.ActiveContextLoaded active = (SessionContextResult.ActiveContextLoaded) loaded;
        assertThat(active.sysPromptAmount()).isEqualTo(3);
        assertThat(active.messages()).startsWith(
                new ContextElement.SystemCoreMsg("Core system prompt"),
                new ContextElement.SystemAgentMsg("Agent system prompt"),
                new ContextElement.SystemTaskMsg("Task system prompt")
        );
    }
}
