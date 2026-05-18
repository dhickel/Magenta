package io.mindspice.magenta2.api.web;

import java.time.Instant;

import io.mindspice.magenta2.ai.orchestration.workspaces.AgentWorkspaceStatusService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final ObjectProvider<AgentWorkspaceStatusService> workspaceStatusService;

    public RuntimeController(
        ObjectProvider<AgentWorkspaceStatusService> workspaceStatusService
    ) {
        this.workspaceStatusService = workspaceStatusService;
    }

    /**
     * Returns a summary of the filesystem-backed workspace runtime.
     */
    @GetMapping("/status")
    public RuntimeStatus runtimeStatus() {
        boolean available = workspaceStatusService.getIfAvailable() != null;
        return new RuntimeStatus(
            available,
            available ? "Filesystem workspace runtime available" : "Workspace service unavailable",
            Instant.now()
        );
    }

    public record RuntimeStatus(
        boolean available,
        String message,
        Instant checkedAt
    ) {}
}
