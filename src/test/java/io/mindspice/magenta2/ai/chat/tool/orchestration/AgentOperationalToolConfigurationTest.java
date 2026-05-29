package io.mindspice.magenta2.ai.chat.tool.orchestration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.avatar.dashboard.DashboardWidgetRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgentOperationalToolConfigurationTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void exposesOperationalToolsThroughMethodProvider() {
        AgentOperationalTools tools = new AgentOperationalTools(mock(AgentOperationalToolService.class), new ObjectMapper());
        ToolCallbackProvider provider = new AgentOperationalToolConfiguration().agentOperationalToolCallbackProvider(tools);

        List<String> names = Arrays.stream(provider.getToolCallbacks())
            .map(callback -> callback.getToolDefinition().name())
            .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder(
            "agent_workspace_status",
            "agent_workspace_links",
            "agent_project_release_workspace",
            "agent_queue_list",
            "agent_assignment_get",
            "agent_assignment_cancel",
            "agent_assignment_pause",
            "agent_assignment_resume",
            "agent_assignment_delete",
            "agent_assignment_requeue_workspace_blocked",
            "agent_assignment_diagnostics",
            "agent_assignment_transcript",
            "agent_inbox_list",
            "agent_inbox_send",
            "agent_inbox_mark_read",
            "agent_inbox_mark_handled",
            "agent_schedule_list",
            "agent_schedule_save",
            "agent_schedule_toggle",
            "agent_schedule_delete",
            "agent_job_list",
            "agent_job_get",
            "agent_job_submit_run",
            "agent_job_run_list",
            "agent_job_run_cancel",
            "agent_job_outputs",
            "agent_project_list",
            "agent_project_get",
            "agent_project_members",
            "agent_project_workspace_status",
            "agent_project_events",
            "agent_output_list",
            "agent_output_read",
            "avatar_system_overview",
            "avatar_agent_list",
            "avatar_agent_status",
            "avatar_assignment_list",
            "avatar_assignment_cancel",
            "avatar_assignment_pause",
            "avatar_assignment_resume",
            "avatar_assignment_requeue_workspace_blocked",
            "avatar_project_list",
            "avatar_project_members",
            "avatar_project_release_workspace",
            "avatar_job_list",
            "avatar_job_run_list",
            "avatar_job_run_cancel",
            "avatar_schedule_list",
            "avatar_output_list",
            "avatar_output_read"
        );
        assertThat(provider.getToolCallbacks()[0].getToolDefinition().description()).isNotBlank();
    }

    @Test
    void registryResolvesOperationalToolsAndRejectsUnknownNames() {
        AgentOperationalTools tools = new AgentOperationalTools(mock(AgentOperationalToolService.class), new ObjectMapper());
        ToolCallbackProvider provider = new AgentOperationalToolConfiguration().agentOperationalToolCallbackProvider(tools);
        ChatToolRegistry registry = new ChatToolRegistry(List.of(), List.of(provider));

        assertThat(registry.resolveApprovedTools(List.of("agent_queue_list", "avatar_system_overview")))
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("agent_queue_list", "avatar_system_overview");
        assertThatThrownBy(() -> registry.resolveApprovedTools(List.of("agent_missing")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agent_missing");
    }

    @Test
    void phase04DashboardWidgetToolDescriptorsMatchRegisteredAgentOperationalTools() {
        AgentOperationalTools tools = new AgentOperationalTools(mock(AgentOperationalToolService.class), new ObjectMapper());
        ToolCallbackProvider provider = new AgentOperationalToolConfiguration().agentOperationalToolCallbackProvider(tools);
        List<String> registeredNames = Arrays.stream(provider.getToolCallbacks())
            .map(callback -> callback.getToolDefinition().name())
            .toList();

        List<String> declaredPhase04Tools = List.of(
                "agent-status-queue",
                "agent-outputs",
                "agent-files-notes"
            ).stream()
            .flatMap(type -> {
                var descriptor = DashboardWidgetRegistry.defaultRegistry().require(type).toolDescriptor();
                return java.util.stream.Stream.concat(descriptor.readTools().stream(), descriptor.mutationTools().stream());
            })
            .distinct()
            .toList();

        assertThat(declaredPhase04Tools)
            .allMatch(name -> name.startsWith("agent_"))
            .isNotEmpty();
        assertThat(registeredNames).containsAll(declaredPhase04Tools);
    }
}
