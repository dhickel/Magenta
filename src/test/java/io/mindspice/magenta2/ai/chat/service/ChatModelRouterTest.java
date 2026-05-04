package io.mindspice.magenta2.ai.chat.service;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelRouterTest {

    @Test
    void resolvesModelByConfigKeyOrRemoteModelName() {
        ChatModelRouter router = new ChatModelRouter(aiConfig(), null, ObservationRegistry.NOOP);

        assertThat(router.remoteModelName("local-qwen")).isEqualTo("qwen3");
        assertThat(router.remoteModelName("qwen3")).isEqualTo("qwen3");
    }

    @Test
    void rejectsUnknownModel() {
        ChatModelRouter router = new ChatModelRouter(aiConfig(), null, ObservationRegistry.NOOP);

        assertThatThrownBy(() -> router.remoteModelName("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown configured model");
    }

    @Test
    void buildsOpenAiCompatibleModel() {
        AiConfig config = new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            Map.of("openai", new ModelConfig("gpt-4o-mini", "https://api.example.test/v1", EndpointType.OPENAI_COMPATIBLE, 128000, false, "test-key")),
            Map.of("magenta", new AgentConfig("openai", "Prompt.", List.of()))
        );
        ChatModelRouter router = new ChatModelRouter(config, null, ObservationRegistry.NOOP);

        assertThat(router.remoteModelName("openai")).isEqualTo("gpt-4o-mini");
        assertThat(router.chatModel("openai")).isNotNull();
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            Map.of(
                "local-qwen", new ModelConfig("qwen3", "http://localhost:11434", EndpointType.OLLAMA, 8192, false, null),
                "local-gemma", new ModelConfig("gemma4", "http://other-host:11434", EndpointType.OLLAMA, 32768, false, null)
            ),
            Map.of("magenta", new AgentConfig("local-qwen", "Prompt.", List.of()))
        );
    }
}
