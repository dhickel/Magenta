package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRecurrence;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRun;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItem;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItemType;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class JobController {

    private final JobService jobService;
    private final OutputArtifactService outputArtifactService;

    public JobController(JobService jobService, OutputArtifactService outputArtifactService) {
        this.jobService = jobService;
        this.outputArtifactService = outputArtifactService;
    }

    // ════════════════════════════════════════════════════════════════
    //  Job Definitions
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/api/jobs")
    public List<JobDefinition> list(@RequestParam(required = false) String agentId,
                                    @RequestParam(required = false) String projectId,
                                    @RequestParam(required = false) String status) {
        return jobService.listDefinitions(agentId, projectId, status);
    }

    @PostMapping("/api/jobs")
    public JobDefinition create(@RequestBody JobDefinition definition) {
        try {
            return jobService.saveDefinition(definition);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api/jobs/{jobId}")
    public JobDefinition get(@PathVariable String jobId) {
        try {
            return jobService.getDefinition(jobId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/api/jobs/{jobId}")
    public JobDefinition update(@PathVariable String jobId,
                                 @RequestBody JobDefinition definition) {
        try {
            return jobService.saveDefinition(new JobDefinition(
                jobId, definition.ownerAgentId(), definition.projectId(),
                definition.workspaceId(), definition.status(),
                definition.title(), definition.summary(),
                definition.items(), definition.promptProfile(),
                definition.model(), definition.settingsOverrideJson(),
                definition.createdAt(), definition.updatedAt()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api/jobs/{jobId}")
    public void delete(@PathVariable String jobId) {
        jobService.deleteDefinition(jobId);
    }

    @GetMapping("/api/jobs/{jobId}/items")
    public List<JobWorkItem> items(@PathVariable String jobId) {
        try {
            return jobService.listItems(jobId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/api/jobs/{jobId}/items")
    public JobWorkItem addItem(@PathVariable String jobId, @RequestBody JobItemRequest request) {
        try {
            return jobService.addItem(jobId, request.toItem(null));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/api/jobs/{jobId}/items/{itemId}")
    public JobWorkItem updateItem(@PathVariable String jobId,
                                  @PathVariable String itemId,
                                  @RequestBody JobItemRequest request) {
        try {
            return jobService.updateItem(jobId, itemId, request.toItem(itemId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api/jobs/{jobId}/items/{itemId}")
    public void deleteItem(@PathVariable String jobId, @PathVariable String itemId) {
        try {
            jobService.deleteItem(jobId, itemId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Job Runs
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/api/jobs/{jobId}/runs")
    public JobRun startRun(@PathVariable String jobId) {
        try {
            return jobService.startRun(jobId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/api/jobs/{jobId}/runs")
    public List<JobRun> listRuns(@PathVariable String jobId) {
        return jobService.listRuns(jobId);
    }

    @GetMapping("/api/job-runs/{runId}")
    public JobRun getRun(@PathVariable String runId) {
        try {
            return jobService.getRun(runId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/api/jobs/{jobId}/outputs")
    public List<RunOutputArtifact> outputs(@PathVariable String jobId) {
        List<RunOutputArtifact> artifacts = new ArrayList<>();
        for (String runId : jobService.outputRunIds(jobId)) {
            artifacts.addAll(outputArtifactService.artifactsForRun(runId));
        }
        return artifacts;
    }

    @GetMapping("/api/jobs/{jobId}/events")
    public List<JobEventResponse> events(@PathVariable String jobId) {
        return jobService.listRuns(jobId).stream()
            .map(run -> new JobEventResponse(
                run.id(),
                "JOB_RUN_" + run.status().name(),
                run.status().name(),
                run.createdAt(),
                run.updatedAt()
            ))
            .toList();
    }

    @PostMapping("/api/job-runs/{runId}/cancel")
    public JobRun cancelRun(@PathVariable String runId) {
        try {
            return jobService.cancelRun(runId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Recurrence
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/api/jobs/{jobId}/recurrence")
    public JobRecurrence setRecurrence(@PathVariable String jobId,
                                        @RequestBody RecurrenceRequest request) {
        try {
            return jobService.setRecurrence(jobId, request.cronExpression(),
                request.timezone(), request.nextFireTime());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api/jobs/{jobId}/recurrence")
    public Object getRecurrence(@PathVariable String jobId) {
        return jobService.getRecurrence(jobId)
            .map(r -> (Object) r)
            .orElse(Map.of("message", "No recurrence set"));
    }

    // ── DTOs ──

    public record RecurrenceRequest(
        String cronExpression,
        String timezone,
        Instant nextFireTime
    ) {}

    public record JobItemRequest(
        String key,
        JobWorkItemType type,
        JobWorkItemType itemType,
        String planId,
        String workflowId,
        Map<String, Object> inputBindings,
        Integer order,
        Integer itemOrder,
        String modelOverride,
        Integer priority
    ) {
        JobWorkItem toItem(String fallbackKey) {
            return new JobWorkItem(
                key != null && !key.isBlank() ? key : fallbackKey,
                type != null ? type : itemType,
                planId,
                workflowId,
                inputBindings == null ? Map.of() : inputBindings,
                order != null ? order : itemOrder == null ? 0 : itemOrder,
                modelOverride,
                priority
            );
        }
    }

    public record JobEventResponse(
        String id,
        String type,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
