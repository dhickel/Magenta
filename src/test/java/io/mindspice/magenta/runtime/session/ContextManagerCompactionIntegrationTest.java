package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextManagerCompactionIntegrationTest {

    @Test
    void summarizeCompactionExcludesSystemAndRetainsRecentTail() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemMsg("system"));
        context.append(new ContextElement.SystemMsg("task"));
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
        assertThat(summarizedInput.get()).allMatch(message -> !(message instanceof ContextElement.SystemMsg));
        assertThat(compacted.getFirst()).isEqualTo(new ContextElement.SystemMsg("system"));
        assertThat(compacted.get(1)).isEqualTo(new ContextElement.SystemMsg("task"));
        assertThat(compacted.get(2)).isInstanceOf(ContextElement.SummaryMsg.class);
        List<ContextElement> preservedTail = compacted.subList(3, compacted.size());
        assertThat(preservedTail).hasSizeGreaterThanOrEqualTo(10);
        assertThat(preservedTail.getFirst())
                .matches(message -> message instanceof ContextElement.UserMsg || message instanceof ContextElement.InboundMsg);
        assertThat(preservedTail).isEqualTo(original.subList(original.size() - preservedTail.size(), original.size()));
    }

    @Test
    void summarizeCompactionNoopsWhenReductionIsTooSmall() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemMsg("system"));
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
    void rollingWindowRespectsTargetTokensWhenAssistantContainsLargeToolArgs() {
        ContextManager contextManager = new ContextManager();
        Context context = new Context();
        context.append(new ContextElement.SystemMsg("system"));
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
}
