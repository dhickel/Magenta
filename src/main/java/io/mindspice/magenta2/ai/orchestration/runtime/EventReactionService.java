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

    public AgentEventReaction reaction(String agentId, String reactionId) {
        agentProfileService.get(agentId);
        AgentEventReaction reaction = repository.findReaction(reactionId)
            .orElseThrow(() -> new IllegalStateException("reaction not found"));
        if (!agentId.equals(reaction.agentId())) {
            throw new IllegalStateException("reaction not found");
        }
        return reaction;
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

    public AgentEventReaction toggle(String agentId, String reactionId) {
        AgentEventReaction current = reaction(agentId, reactionId);
        return save(agentId, new AgentEventReaction(
            current.id(),
            current.agentId(),
            current.eventType(),
            current.filter(),
            current.actionType(),
            current.assignmentTemplate(),
            !current.enabled(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public void delete(String agentId, String reactionId) {
        agentProfileService.get(agentId);
        if (!repository.deleteReactionForAgent(agentId, reactionId)) {
            throw new IllegalStateException("reaction not found");
        }
    }
}
