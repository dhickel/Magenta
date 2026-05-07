package io.mindspice.magenta2.ai.orchestration.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import org.springframework.stereotype.Service;

@Service
public class EventReactionService {
    private final OrchestrationRuntimeRepository repository;
    private final AgentProfileService agentProfileService;

    public EventReactionService(OrchestrationRuntimeRepository repository, AgentProfileService agentProfileService) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
    }

    public List<AgentEventReaction> reactions(String agentId) {
        agentProfileService.get(agentId);
        return repository.findReactionsForAgent(agentId);
    }

    public AgentEventReaction save(String agentId, AgentEventReaction reaction) {
        agentProfileService.get(agentId);
        if (reaction.eventType() == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (reaction.actionType() == null) {
            throw new IllegalArgumentException("actionType is required");
        }
        if (reaction.actionType() != ReactionActionType.ENQUEUE_ASSIGNMENT) {
            throw new IllegalArgumentException("unsupported actionType: " + reaction.actionType());
        }
        return repository.saveReaction(new AgentEventReaction(
            reaction.id() == null || reaction.id().isBlank() ? UUID.randomUUID().toString() : reaction.id(),
            agentId,
            reaction.eventType(),
            reaction.filter() == null ? Map.of() : reaction.filter(),
            reaction.actionType(),
            reaction.assignmentTemplate() == null ? Map.of() : reaction.assignmentTemplate(),
            reaction.enabled(),
            reaction.createdAt(),
            reaction.updatedAt()
        ));
    }
}
