package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.session.SessionMessage;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextManagerCompactionIntegrationTest {

    @Test
    void summarizeCompactionCreatesSummaryAndRetainsRecentMessages() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new SessionMessage.SystemMsg("system"));
        for (int i = 0; i < 10; i++) {
            context.append(new SessionMessage.UserMsg("message-" + i + "-xxxxxxxxxxxxxxxxxxxxxxxx"));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                60,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        AtomicReference<List<SessionMessage>> summarizedInput = new AtomicReference<>(List.of());
        contextManager.compactIfNeeded(
                UUID.randomUUID(),
                context,
                modelConfig,
                messages -> {
                    summarizedInput.set(new ArrayList<>(messages));
                    return "summary text";
                }
        );

        List<SessionMessage> compacted = context.snapshot();
        assertThat(summarizedInput.get()).hasSize(4);
        assertThat(compacted.getFirst()).isEqualTo(new SessionMessage.SystemMsg("system"));
        assertThat(compacted.get(1)).isInstanceOf(SessionMessage.SummaryMsg.class);
        SessionMessage.SummaryMsg summary = (SessionMessage.SummaryMsg) compacted.get(1);
        assertThat(summary.content()).isEqualTo("summary text");
        assertThat(summary.sourceTag()).startsWith("session:");
        assertThat(compacted).hasSize(8);
    }

    @Test
    void summarizeCompactionFallsBackToRollingWindowWhenSummaryIsBlank() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new SessionMessage.SystemMsg("system"));
        for (int i = 0; i < 10; i++) {
            context.append(new SessionMessage.UserMsg("message-" + i + "-xxxxxxxxxxxxxxxxxxxxxxxx"));
        }

        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "m",
                "provider",
                "model",
                "http://localhost",
                2048,
                2048,
                60,
                0.0,
                "summarize",
                "cl100k_base",
                false,
                false,
                true
        );

        contextManager.compactIfNeeded(UUID.randomUUID(), context, modelConfig, messages -> "   ");

        List<SessionMessage> compacted = context.snapshot();
        assertThat(compacted).isNotEmpty();
        assertThat(compacted.getFirst()).isEqualTo(new SessionMessage.SystemMsg("system"));
        assertThat(compacted.stream().anyMatch(SessionMessage.SummaryMsg.class::isInstance)).isFalse();
        assertThat(compacted.size()).isLessThan(11);
    }

    @Test
    void rollingWindowRespectsTargetTokensWhenAssistantContainsLargeToolArgs() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new SessionMessage.SystemMsg("system"));
        context.append(new SessionMessage.AssistantMsg(
                "",
                List.of(new SessionMessage.ToolCall("id-1", "read_file", "{\"blob\":\"" + "x".repeat(1000) + "\"}"))
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
}
