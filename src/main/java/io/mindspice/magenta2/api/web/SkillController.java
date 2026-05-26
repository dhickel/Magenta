package io.mindspice.magenta2.api.web;

import java.util.Map;

import io.mindspice.magenta2.ai.skills.AgentSkill;
import io.mindspice.magenta2.ai.skills.AgentSkillAssignment;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillApiException;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillCatalog;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillFileEntry;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillFileTree;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillFileView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final AgentSkillManagementService managementService;

    public SkillController(AgentSkillManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    public SkillCatalog listSkills() {
        return managementService.listSkills();
    }

    @PostMapping("/refresh")
    public SkillCatalog refreshSkills() {
        return managementService.refreshSkills();
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public AgentSkill createSkill(@RequestBody CreateSkillRequest request) {
        return managementService.createSkill(request.skillName(), request.description());
    }

    @GetMapping("/{skillName}")
    public AgentSkill skillDetail(@PathVariable String skillName) {
        return managementService.getSkill(skillName);
    }

    @GetMapping("/{skillName}/diagnostics")
    public SkillDiagnosticsResponse diagnostics(@PathVariable String skillName) {
        return new SkillDiagnosticsResponse(managementService.diagnostics(skillName));
    }

    @GetMapping("/{skillName}/files")
    public SkillFileTree fileTree(
        @PathVariable String skillName,
        @RequestParam(defaultValue = ".") String path
    ) {
        return managementService.listFiles(skillName, path);
    }

    @GetMapping("/{skillName}/files/view")
    public SkillFileView fileView(
        @PathVariable String skillName,
        @RequestParam String path
    ) {
        return managementService.viewFile(skillName, path);
    }

    @PutMapping("/{skillName}/files/text")
    public SkillFileView saveTextFile(
        @PathVariable String skillName,
        @RequestParam String path,
        @RequestBody TextSaveRequest request
    ) {
        return managementService.saveText(skillName, path, request.content());
    }

    @PostMapping("/{skillName}/files")
    public SkillFileEntry createTextFile(
        @PathVariable String skillName,
        @RequestBody CreateTextFileRequest request
    ) {
        return managementService.createTextFile(
            skillName,
            request.parentPath(),
            request.fileName(),
            request.content()
        );
    }

    @GetMapping("/{skillName}/assignments")
    public SkillAssignmentsResponse listAssignments(@PathVariable String skillName) {
        return new SkillAssignmentsResponse(managementService.listAgentAssignments(skillName));
    }

    @PostMapping("/{skillName}/assignments/agents/{agentId}")
    public AgentSkillAssignment assignAgent(
        @PathVariable String skillName,
        @PathVariable String agentId,
        @RequestParam(defaultValue = "true") boolean enabled
    ) {
        return managementService.assignToAgent(skillName, agentId, enabled);
    }

    @DeleteMapping("/{skillName}/assignments/agents/{agentId}")
    public Map<String, Object> unassignAgent(
        @PathVariable String skillName,
        @PathVariable String agentId
    ) {
        managementService.unassignFromAgent(skillName, agentId);
        return Map.of("removed", true);
    }

    @ExceptionHandler(SkillApiException.class)
    public ResponseEntity<Map<String, String>> onSkillApiException(SkillApiException exception) {
        return ResponseEntity.status(exception.status())
            .body(Map.of("error", exception.getMessage()));
    }

    public record CreateSkillRequest(String skillName, String description) { }

    public record TextSaveRequest(String content) { }

    public record CreateTextFileRequest(String parentPath, String fileName, String content) { }

    public record SkillDiagnosticsResponse(java.util.List<io.mindspice.magenta2.ai.skills.AgentSkillDiagnostic> diagnostics) { }

    public record SkillAssignmentsResponse(java.util.List<AgentSkillAssignment> assignments) { }
}
