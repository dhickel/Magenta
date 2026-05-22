package io.mindspice.magenta2.ai.chat.service.turn;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolAccessPolicyTest {

    @Test
    void planModeKeepsOperationalToolsOutOfDraftingEvenWithWildcardApproval() {
        ToolAccessPolicy policy = new ToolAccessPolicy(
            new ChatToolRegistry(List.of(
                new NamedToolCallback("file_read"),
                new NamedToolCallback("agent_queue_list"),
                new NamedToolCallback("avatar_system_overview")
            ), List.of()),
            null,
            null
        );

        List<String> toolNames = policy.filterToolsByMode(List.of("*"), PlanMode.PLAN).stream()
            .map(callback -> callback.getToolDefinition().name())
            .toList();

        assertThat(toolNames).containsExactly("file_read");
    }

    @Test
    void normalModeAllowsExplicitOperationalToolsForAgentTurns() {
        ToolAccessPolicy policy = new ToolAccessPolicy(
            new ChatToolRegistry(List.of(
                new NamedToolCallback("agent_queue_list"),
                new NamedToolCallback("avatar_system_overview")
            ), List.of()),
            null,
            null
        );

        List<String> toolNames = policy.filterToolsByMode(
            List.of("agent_queue_list", "avatar_system_overview"), PlanMode.NORMAL
        ).stream()
            .map(callback -> callback.getToolDefinition().name())
            .toList();

        assertThat(toolNames).containsExactly("agent_queue_list", "avatar_system_overview");
    }

    private static final class NamedToolCallback implements ToolCallback {
        private final ToolDefinition definition;

        private NamedToolCallback(String name) {
            this.definition = new DefaultToolDefinition(name, "Named test tool", "{\"type\":\"object\"}");
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return "{\"ok\":true}";
        }
    }
}
