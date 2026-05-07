package io.mindspice.magenta2.api.web;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEvent;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJob;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobItem;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class OrchestrationJobController {
    private final OrchestrationJobService jobService;
    private final AssignmentService assignmentService;

    public OrchestrationJobController(OrchestrationJobService jobService, AssignmentService assignmentService) {
        this.jobService = jobService;
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<OrchestrationJob> list(@RequestParam String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agentId is required");
        }
        return jobService.jobs(agentId);
    }

    @PostMapping
    public OrchestrationJob create(@RequestBody OrchestrationJob job) {
        try {
            return jobService.save(job);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{jobId}")
    public OrchestrationJob get(@PathVariable String jobId) {
        try {
            return jobService.get(jobId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @GetMapping("/{jobId}/items")
    public List<OrchestrationJobItem> items(@PathVariable String jobId) {
        return jobService.items(jobId);
    }

    @PostMapping("/{jobId}/items")
    public OrchestrationJobItem addItem(@PathVariable String jobId, @RequestBody OrchestrationJobItem item) {
        try {
            return jobService.saveItem(jobId, item);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{jobId}/runs")
    public List<WorkAssignment> runs(@PathVariable String jobId) {
        return jobService.runs(jobId);
    }

    @PostMapping("/{jobId}/runs")
    public WorkAssignment run(@PathVariable String jobId, @RequestBody(required = false) AssignmentRequest request) {
        OrchestrationJob job = jobService.get(jobId);
        AssignmentRequest body = request == null
            ? new AssignmentRequest(job.ownerAgentId(), jobId, null, io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType.JOB_RUN,
                0, null, job.workspaceId(), java.util.Map.of("jobId", jobId))
            : new AssignmentRequest(request.agentId() == null ? job.ownerAgentId() : request.agentId(), jobId, request.jobItemId(),
                request.assignmentType() == null ? io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType.JOB_RUN : request.assignmentType(),
                request.priority(), request.modelOverride(),
                request.workspaceId() == null ? job.workspaceId() : request.workspaceId(), request.input());
        try {
            return assignmentService.create(body);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{jobId}/events")
    public List<OrchestrationEvent> events(@PathVariable String jobId) {
        return jobService.events(jobId);
    }
}
