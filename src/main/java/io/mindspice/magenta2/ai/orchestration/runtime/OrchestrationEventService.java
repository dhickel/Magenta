package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrchestrationEventService {
    private final OrchestrationRuntimeRepository repository;
    private final AssignmentService assignmentService;
    private final boolean reactionsEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    public OrchestrationEventService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        @org.springframework.beans.factory.annotation.Value("${magenta.features.reactions-enabled:false}") boolean reactionsEnabled
    ) {
        this.repository = repository;
        this.assignmentService = assignmentService;
        this.reactionsEnabled = reactionsEnabled;
    }

    public OrchestrationEventService(OrchestrationRuntimeRepository repository, AssignmentService assignmentService) {
        this(repository, assignmentService, true);
    }

    @Transactional
    public OrchestrationEvent publish(EventType eventType, String sourceType, String sourceId, Map<String, Object> payload) {
        OrchestrationEvent event = repository.saveEvent(new OrchestrationEvent(
            UUID.randomUUID().toString(), eventType, sourceType, sourceId, payload == null ? Map.of() : payload,
            null, null
        ));
        handle(event);
        return event;
    }

    @Transactional
    public void handle(OrchestrationEvent event) {
        if (!reactionsEnabled) {
            repository.saveEvent(new OrchestrationEvent(
                event.id(), event.eventType(), event.sourceType(), event.sourceId(),
                event.payload(), event.createdAt(), Instant.now()
            ));
            return;
        }
        for (AgentEventReaction reaction : repository.findEnabledReactions(event.eventType())) {
            if (!matches(reaction.filter(), event.payload())) {
                continue;
            }
            if (reaction.actionType() == ReactionActionType.ENQUEUE_ASSIGNMENT) {
                assignmentService.create(requestFromTemplate(reaction.agentId(), reaction.assignmentTemplate()));
            }
        }

        // Auto-resume WAITING assignments when inbox message arrives for the owning agent
        if (event.eventType() == EventType.INBOX_MESSAGE_RECEIVED) {
            String agentId = stringValue(event.payload(), "toAgentId");
            if (StringUtils.hasText(agentId)) {
                var waitingAssignments = repository.findWaitingAssignmentsForAgent(agentId);
                for (var assignment : waitingAssignments) {
                    assignmentService.resume(assignment.id());
                }
            }
        }

        repository.saveEvent(new OrchestrationEvent(
            event.id(), event.eventType(), event.sourceType(), event.sourceId(), event.payload(),
            event.createdAt(), Instant.now()
        ));
    }

    private boolean matches(Map<String, Object> filter, Map<String, Object> payload) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> values = payload == null ? Map.of() : payload;
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object actual = values.get(entry.getKey());
            if (actual == null || !actual.toString().equals(String.valueOf(entry.getValue()))) {
                return false;
            }
        }
        return true;
    }

    private AssignmentRequest requestFromTemplate(String agentId, Map<String, Object> template) {
        return AssignmentTemplateParser.reactionRequest(agentId, template);
    }

    private String stringValue(Map<String, Object> values, String key) {
        if (values == null || values.get(key) == null) {
            return null;
        }
        return values.get(key).toString();
    }

}
