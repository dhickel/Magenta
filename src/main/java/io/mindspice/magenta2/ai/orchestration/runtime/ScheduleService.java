package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScheduleService {
    private final OrchestrationRuntimeRepository repository;
    private final AgentProfileService agentProfileService;
    private final AssignmentService assignmentService;
    private final OrchestrationEventService eventService;
    private final boolean schedulesEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduleService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        AssignmentService assignmentService,
        OrchestrationEventService eventService,
        @org.springframework.beans.factory.annotation.Value("${magenta.features.schedules-enabled:false}") boolean schedulesEnabled
    ) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.assignmentService = assignmentService;
        this.eventService = eventService;
        this.schedulesEnabled = schedulesEnabled;
    }

    public ScheduleService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        AssignmentService assignmentService,
        OrchestrationEventService eventService
    ) {
        this(repository, agentProfileService, assignmentService, eventService, true);
    }

    public List<AgentSchedule> schedules(String agentId) {
        agentProfileService.get(agentId);
        return repository.findSchedulesForAgent(agentId);
    }

    public AgentSchedule schedule(String agentId, String scheduleId) {
        agentProfileService.get(agentId);
        AgentSchedule schedule = repository.findSchedule(scheduleId)
            .orElseThrow(() -> new IllegalStateException("schedule not found"));
        if (!agentId.equals(schedule.agentId())) {
            throw new IllegalStateException("schedule not found");
        }
        return schedule;
    }

    public AgentSchedule save(String agentId, AgentSchedule schedule) {
        agentProfileService.get(agentId);
        ZoneId zoneId = zone(schedule.timezone());
        CronExpression cron = cron(schedule.cronExpression());
        Instant nextRun = schedule.nextRunAt() == null
            ? nextRun(cron, zoneId, Instant.now())
            : schedule.nextRunAt();
        return repository.saveSchedule(new AgentSchedule(
            StringUtils.hasText(schedule.id()) ? schedule.id() : UUID.randomUUID().toString(),
            agentId,
            normalize(schedule.jobId()),
            schedule.assignmentTemplate() == null ? Map.of() : schedule.assignmentTemplate(),
            schedule.cronExpression().trim(),
            zoneId.getId(),
            schedule.enabled(),
            nextRun,
            schedule.createdAt(),
            schedule.updatedAt()
        ));
    }

    public AgentSchedule toggle(String agentId, String scheduleId) {
        AgentSchedule current = schedule(agentId, scheduleId);
        return save(agentId, new AgentSchedule(
            current.id(),
            current.agentId(),
            current.jobId(),
            current.assignmentTemplate(),
            current.cronExpression(),
            current.timezone(),
            !current.enabled(),
            null,
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public void delete(String agentId, String scheduleId) {
        agentProfileService.get(agentId);
        if (!repository.deleteScheduleForAgent(agentId, scheduleId)) {
            throw new IllegalStateException("schedule not found");
        }
    }

    @Scheduled(fixedDelayString = "${magenta.orchestration.scheduler-delay-ms:10000}")
    @Transactional
    public void pollDueSchedules() {
        if (!schedulesEnabled) {
            return;
        }
        Instant now = Instant.now();
        for (AgentSchedule schedule : repository.findDueSchedules(now)) {
            CronExpression cron = cron(schedule.cronExpression());
            ZoneId zoneId = zone(schedule.timezone());
            Instant dueAt = schedule.nextRunAt();
            if (dueAt == null) {
                continue;
            }
            String firingId = UUID.randomUUID().toString();
            String assignmentId = UUID.randomUUID().toString();
            if (!repository.createScheduleFiring(firingId, schedule.id(), dueAt, assignmentId)) {
                continue;
            }
            repository.saveSchedule(new AgentSchedule(
                schedule.id(), schedule.agentId(), schedule.jobId(), schedule.assignmentTemplate(),
                schedule.cronExpression(), schedule.timezone(), schedule.enabled(),
                nextRun(cron, zoneId, now), schedule.createdAt(), schedule.updatedAt()
            ));
            eventService.publish(EventType.SCHEDULE_DUE, "SCHEDULE", schedule.id(), Map.of(
                "scheduleId", schedule.id(),
                "agentId", schedule.agentId(),
                "dueAt", dueAt == null ? "" : dueAt.toString()
            ));
            assignmentService.create(assignmentId, requestFromTemplate(schedule));
        }
    }

    private AssignmentRequest requestFromTemplate(AgentSchedule schedule) {
        Map<String, Object> template = schedule.assignmentTemplate();
        @SuppressWarnings("unchecked")
        Map<String, Object> input = template.get("input") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
        return new AssignmentRequest(
            text(template, "agentId", schedule.agentId()),
            text(template, "jobId", schedule.jobId()),
            text(template, "jobItemId", null),
            AssignmentType.valueOf(text(template, "assignmentType", AssignmentType.JOB_RUN.name())),
            integer(template, "priority", 0),
            text(template, "modelOverride", null),
            text(template, "workspaceId", null),
            input
        );
    }

    private CronExpression cron(String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        if (!CronExpression.isValidExpression(expression.trim())) {
            throw new IllegalArgumentException("invalid cronExpression");
        }
        return CronExpression.parse(expression.trim());
    }

    private ZoneId zone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.systemDefault();
        }
        return ZoneId.of(timezone.trim());
    }

    private Instant nextRun(CronExpression cron, ZoneId zoneId, Instant after) {
        ZonedDateTime next = cron.next(ZonedDateTime.ofInstant(after, zoneId));
        if (next == null) {
            throw new IllegalArgumentException("cronExpression has no next run");
        }
        return next.toInstant();
    }

    private String text(Map<String, Object> values, String key, String fallback) {
        Object value = values == null ? null : values.get(key);
        return value == null ? fallback : value.toString();
    }

    private Integer integer(Map<String, Object> values, String key, int fallback) {
        Object value = values == null ? null : values.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
