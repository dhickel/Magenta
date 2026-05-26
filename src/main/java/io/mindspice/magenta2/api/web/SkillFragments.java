package io.mindspice.magenta2.api.web;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.skills.AgentSkill;
import io.mindspice.magenta2.ai.skills.AgentSkillAssignment;
import io.mindspice.magenta2.ai.skills.AgentSkillDiagnostic;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillFileTree;
import io.mindspice.magenta2.ai.skills.AgentSkillManagementService.SkillFileView;
import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.Header;
import io.mindspice.simplypages.components.Paragraph;
import io.mindspice.simplypages.components.forms.Button;
import io.mindspice.simplypages.core.HtmlTag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SkillFragments {
    private final AgentSkillManagementService managementService;
    private final AgentProfileService agentProfileService;

    public SkillFragments(
        AgentSkillManagementService managementService,
        AgentProfileService agentProfileService
    ) {
        this.managementService = managementService;
        this.agentProfileService = agentProfileService;
    }

    @GetMapping("/skills")
    @ResponseBody
    public String skillsPage() {
        Div root = new Div().withId("skills-shell").withClass("skills-shell")
            .withChild(Header.H2("Skills"))
            .withChild(new Div().withClass("skills-layout")
                .withChild(new Div().withId("skills-list")
                    .hxGet("/skills/_list")
                    .hxTrigger("load")
                    .hxSwap("innerHTML"))
                .withChild(new Div().withId("skills-detail")
                    .withChild(new Paragraph("Select a skill."))));
        return root.render();
    }

    @GetMapping("/skills/_list")
    @ResponseBody
    public String listFragment() {
        List<AgentSkill> skills = managementService.listSkills().skills();
        Div list = new Div().withId("skills-list").withClass("skills-list");
        if (skills.isEmpty()) {
            list.withChild(new Paragraph("No skills discovered."));
            return list.render();
        }
        for (AgentSkill skill : skills) {
            String key = skill.name() == null || skill.name().isBlank() ? skill.directorySlug() : skill.name();
            list.withChild(new Div().withClass("skills-list-row")
                .withChild(new HtmlTag("span").withInnerText(skill.name() + " (" + skill.status().name() + ")"))
                .withChild(Button.create("Open")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/skills/_detail/" + escapePath(key))
                    .withAttribute("hx-target", "#skills-detail")
                    .withAttribute("hx-swap", "innerHTML")));
        }
        return list.render();
    }

    @GetMapping("/skills/_detail/{skillName}")
    @ResponseBody
    public String detailFragment(@PathVariable String skillName) {
        AgentSkill skill = managementService.getSkill(skillName);
        Div detail = new Div().withId("skills-detail").withClass("skills-detail")
            .withChild(Header.H3(skill.name()))
            .withChild(new Paragraph("Status: " + skill.status().name()))
            .withChild(new Paragraph("Directory: " + skill.directorySlug()))
            .withChild(new Paragraph("Description: " + (skill.description() == null ? "None" : skill.description())));

        List<AgentSkillDiagnostic> diagnostics = managementService.diagnostics(skillName);
        Div diagnosticList = new Div().withClass("skills-diagnostics");
        if (diagnostics.isEmpty()) {
            diagnosticList.withChild(new Paragraph("No diagnostics."));
        } else {
            for (AgentSkillDiagnostic diagnostic : diagnostics) {
                diagnosticList.withChild(new Paragraph(
                    diagnostic.severity().name() + " " + diagnostic.code().name() + ": " + diagnostic.message()));
            }
        }
        detail.withChild(diagnosticList);
        detail.withChild(new Div().withClass("skills-detail-actions")
            .withChild(Button.create("Files")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/skills/_files/" + escapePath(skillName))
                .withAttribute("hx-target", "#skills-detail")
                .withAttribute("hx-swap", "innerHTML"))
            .withChild(Button.create("Assignments")
                .withAttribute("type", "button")
                .withAttribute("hx-get", "/skills/_assignments/" + escapePath(skillName))
                .withAttribute("hx-target", "#skills-detail")
                .withAttribute("hx-swap", "innerHTML")));
        return detail.render();
    }

    @GetMapping("/skills/_files/{skillName}")
    @ResponseBody
    public String filesFragment(
        @PathVariable String skillName,
        @RequestParam(defaultValue = ".") String path
    ) {
        SkillFileTree tree = managementService.listFiles(skillName, path);
        Div panel = new Div().withId("skills-detail").withClass("skills-files-panel")
            .withChild(Header.H3("Files: " + tree.path()));
        for (var entry : tree.entries()) {
            Div row = new Div().withClass("skills-file-row")
                .withChild(new HtmlTag("span").withInnerText(entry.path()));
            if (entry.directory()) {
                row.withChild(Button.create("Open")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/skills/_files/" + escapePath(skillName) + "?path=" + escapeQuery(entry.path()))
                    .withAttribute("hx-target", "#skills-detail")
                    .withAttribute("hx-swap", "innerHTML"));
            } else {
                row.withChild(Button.create("View")
                    .withAttribute("type", "button")
                    .withAttribute("hx-get", "/skills/_viewer/" + escapePath(skillName) + "?path=" + escapeQuery(entry.path()))
                    .withAttribute("hx-target", "#skills-detail")
                    .withAttribute("hx-swap", "innerHTML"));
            }
            panel.withChild(row);
        }
        if (tree.entries().isEmpty()) {
            panel.withChild(new Paragraph("No files in this directory."));
        }
        return panel.render();
    }

    @GetMapping("/skills/_viewer/{skillName}")
    @ResponseBody
    public String viewerFragment(
        @PathVariable String skillName,
        @RequestParam String path
    ) {
        SkillFileView view = managementService.viewFile(skillName, path);
        Div panel = new Div().withId("skills-detail").withClass("skills-viewer")
            .withChild(Header.H3(view.path()))
            .withChild(new Paragraph("Kind: " + view.kind() + " • Size: " + view.size()));
        if (view.text()) {
            panel.withChild(new HtmlTag("pre").withInnerText(view.content() == null ? "" : view.content()));
            if (view.warning()) {
                panel.withChild(new Paragraph("File is large; inline content preview is omitted."));
            }
        } else {
            panel.withChild(new Paragraph("File cannot be viewed as text."));
        }
        return panel.render();
    }

    @GetMapping("/skills/_assignments/{skillName}")
    @ResponseBody
    public String assignmentsFragment(@PathVariable String skillName) {
        List<AgentSkillAssignment> assignments = managementService.listAgentAssignments(skillName);
        List<AgentProfile> agents = agentProfileService.list();
        Div panel = new Div().withId("skills-detail").withClass("skills-assignments")
            .withChild(Header.H3("Assignments"));
        if (assignments.isEmpty()) {
            panel.withChild(new Paragraph("No assignments."));
        } else {
            for (AgentSkillAssignment assignment : assignments) {
                panel.withChild(new Paragraph(
                    assignment.targetType().name() + " " + assignment.targetId() + " (" + (assignment.enabled() ? "enabled" : "disabled") + ")"));
            }
        }
        Div agentList = new Div().withClass("skills-agent-list");
        for (AgentProfile agent : agents) {
            agentList.withChild(new Paragraph(agent.id() + " - " + agent.name()));
        }
        panel.withChild(agentList);
        return panel.render();
    }

    private String escapePath(String value) {
        return value.replace(" ", "%20");
    }

    private String escapeQuery(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
