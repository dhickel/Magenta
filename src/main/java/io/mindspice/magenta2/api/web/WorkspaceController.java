package io.mindspice.magenta2.api.web;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLink;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/{workspaceId}/links")
    public List<WorkspaceLink> links(@PathVariable String workspaceId) {
        try {
            return workspaceService.links(workspaceId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/{workspaceId}/links")
    public WorkspaceLink addLink(@PathVariable String workspaceId, @RequestBody WorkspaceLink link) {
        try {
            return workspaceService.addLink(workspaceId, link);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @DeleteMapping("/{workspaceId}/links/{linkId}")
    public void deleteLink(@PathVariable String workspaceId, @PathVariable String linkId) {
        try {
            workspaceService.deleteLink(workspaceId, linkId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }
}
