package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrchestrationEventService {
    private final OrchestrationRuntimeRepository repository;
    private final AssignmentService assignmentService;

    public OrchestrationEventService(OrchestrationRuntimeRepository repository, AssignmentService assignmentService) {
        this.repository = repository;
        this.assignmentService = assignmentService;
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
        for (AgentEventReaction reaction : repository.findEnabledReactions(event.eventType())) {
            if (!matches(reaction.filter(), event.payload())) {
                continue;
            }
            if (reaction.actionType() == ReactionActionType.ENQUEUE_ASSIGNMENT) {
                assignmentService.create(requestFromTemplate(reaction.agentId(), reaction.assignmentTemplate()));
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

    @SuppressWarnings("unchecked")
    private AssignmentRequest requestFromTemplate(String agentId, Map<String, Object> template) {
        Map<String, Object> input = template == null ? Map.of() : (Map<String, Object>) template.getOrDefault("input", Map.of());
        return new AssignmentRequest(
            text(template, "agentId", agentId),
            text(template, "jobId", null),
            text(template, "jobItemId", null),
            AssignmentType.valueOf(text(template, "assignmentType", AssignmentType.REPORT.name())),
            integer(template, "priority", 0),
            text(template, "modelOverride", null),
            text(template, "workspaceId", null),
            input
        );
    }

    private String text(Map<String, Object> values, String key, String fallback) {
        if (values == null || values.get(key) == null) {
            return fallback;
        }
        return values.get(key).toString();
    }

    private Integer integer(Map<String, Object> values, String key, int fallback) {
        if (values == null || values.get(key) == null) {
            return fallback;
        }
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
}
