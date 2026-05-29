package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;

public record AgentStatusQueueView(
    String sourceLabel,
    String missingBindingMessage,
    AgentProfile agent,
    List<WorkAssignment> assignments,
    List<InboxMessage> inbox
) {
    public AgentStatusQueueView {
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        inbox = inbox == null ? List.of() : List.copyOf(inbox);
    }

    public boolean missingBinding() {
        return missingBindingMessage != null && !missingBindingMessage.isBlank();
    }
}
