package io.mindspice.magenta2.api.web.selector;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySelectorComponentsTest {
    private final EntitySelectorComponents components = new EntitySelectorComponents();

    @Test
    void selectorRendersSearchAndValidationHooks() {
        String html = components.selector(
            new EntitySelectorConfig("workspaceId", EntityKind.WORKSPACE, "ws-1",
                "Workspace", "optional workspace", false, Map.of()),
            new EntityOption("workspace", "ws-1", "Workspace One", "projects/p1/workspace", "PROJECT", true)
        ).render();

        assertThat(html).contains("class=\"entity-selector");
        assertThat(html).contains("name=\"workspaceId\"");
        assertThat(html).contains("hx-get=\"/selectors/workspace/options?name=workspaceId&amp;required=false");
        assertThat(html).contains("hx-trigger=\"keyup changed delay:300ms, focus\"");
        assertThat(html).contains("hx-include=\"closest .entity-selector\"");
        assertThat(html).contains("/selectors/workspace/validate?name=workspaceId&amp;required=false");
        assertThat(html).contains("Selected: Workspace One");
    }

    @Test
    void selectorContextInputsAndUrlsAreNamespaced() {
        String html = components.selector(
            new EntitySelectorConfig("agentId", EntityKind.AGENT, null,
                "Agent", "agent", true, Map.of("agentId", "owner-agent", "projectId", "project-1")),
            null
        ).render();

        assertThat(html).contains("name=\"agentId\"");
        assertThat(html).contains("name=\"selectorContext.agentId\" value=\"owner-agent\"");
        assertThat(html).contains("name=\"selectorContext.projectId\" value=\"project-1\"");
        assertThat(html).contains("selectorContext.agentId=owner-agent");
        assertThat(html).contains("selectorContext.projectId=project-1");
    }

    @Test
    void requiredSelectorMarksInputRequired() {
        String html = components.selector(
            new EntitySelectorConfig("targetId", EntityKind.TARGET, null,
                "Target", "target ID", true, Map.of()),
            null
        ).render();

        assertThat(html).contains("required=\"required\"");
        assertThat(html).contains("Required");
    }

    @Test
    void optionsUseServerSelectedFragmentSwap() {
        String html = components.options(EntityKind.JOB, "jobId", true, List.of(
            new EntityOption("job", "job-1", "Nightly Job", "2 items", "DRAFT", true)
        )).render();

        assertThat(html).contains("hx-get=\"/selectors/job/selected?name=jobId&amp;value=job-1&amp;required=true\"");
        assertThat(html).contains("hx-target=\"#entity-selector-job-jobId\"");
        assertThat(html).contains("Nightly Job");
        assertThat(html).contains("job-1");
    }

    @Test
    void optionsCarryNamespacedContextToSelectedFragment() {
        String html = components.options(EntityKind.JOB, "jobId", true, List.of(
            new EntityOption("job", "job-1", "Nightly Job", "2 items", "DRAFT", true)
        ), Map.of("agentId", "agent-1"), "Job", "job").render();

        assertThat(html).contains("selectorContext.agentId=agent-1");
        assertThat(html).doesNotContain("&amp;agentId=agent-1");
    }
}
