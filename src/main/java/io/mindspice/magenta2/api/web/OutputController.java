package io.mindspice.magenta2.api.web;

import java.util.ArrayList;
import java.util.List;

import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
public class OutputController {
    private final OutputArtifactService outputArtifactService;
    private final JobService jobService;

    public OutputController(OutputArtifactService outputArtifactService, JobService jobService) {
        this.outputArtifactService = outputArtifactService;
        this.jobService = jobService;
    }

    @GetMapping("/api/outputs")
    public List<RunOutputArtifact> query(@RequestParam(required = false) String agentId,
                                         @RequestParam(required = false) String jobId,
                                         @RequestParam(required = false) String projectId,
                                         @RequestParam(required = false) String runId,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) Integer limit) {
        if (StringUtils.hasText(runId)) {
            return outputArtifactService.query(runId, null, type, limit);
        }
        if (StringUtils.hasText(jobId)) {
            try {
                return artifactsForJobs(List.of(jobService.getDefinition(jobId)), type, limit);
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
        }
        if (StringUtils.hasText(agentId) || StringUtils.hasText(projectId)) {
            return artifactsForJobs(jobService.listDefinitions(agentId, projectId, null), type, limit);
        }
        return outputArtifactService.query(null, null, type, limit);
    }

    private List<RunOutputArtifact> artifactsForJobs(List<JobDefinition> jobs, String type, Integer limit) {
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<RunOutputArtifact> artifacts = new ArrayList<>();
        for (JobDefinition job : jobs) {
            for (String runId : jobService.outputRunIds(job.id())) {
                artifacts.addAll(outputArtifactService.query(runId, null, type, max));
                if (artifacts.size() >= max) {
                    return artifacts.subList(0, max);
                }
            }
        }
        return artifacts;
    }
}
