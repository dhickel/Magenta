package io.mindspice.magenta2.ai.chat.service.turn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.tool.file.AgentFileToolService;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.workspaces.AgentsMdResolution;
import io.mindspice.magenta2.ai.orchestration.workspaces.AgentsMdResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptContextAssemblerTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearRuntimeContext() {
        OrchestrationTaskContextHolder.clear();
    }

    @Test
    void appendsOrderedAgentsMdLayersWithPrecedenceWording() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("projects/project-1"));
        Path workArea = Files.createDirectories(projectRoot.resolve("workareas/area-a"));
        Files.writeString(projectRoot.resolve("AGENTS.md"), "root-guidance");
        Files.writeString(workArea.resolve("AGENTS.md"), "nested-guidance");
        OrchestrationTaskContextHolder.set(projectContext(projectRoot, workArea, "area-a"));

        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());
        String prompt = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(prompt).contains("## Runtime AGENTS.md Context");
        assertThat(prompt).contains("Explicit user prompts and current task instructions override all AGENTS.md guidance.");
        assertThat(prompt).contains("closest layer wins only on conflicts");
        assertThat(prompt).contains("ancestor guidance remains active");
        assertThat(prompt).contains("Source: AGENTS.md");
        assertThat(prompt).contains("Source: workareas/area-a/AGENTS.md");
        assertThat(prompt).contains("root-guidance");
        assertThat(prompt).contains("nested-guidance");
        assertThat(prompt.indexOf("Source: AGENTS.md"))
            .isLessThan(prompt.indexOf("Source: workareas/area-a/AGENTS.md"));
    }

    @Test
    void switchingWorkAreasDropsStaleNestedLayer() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("projects/project-1"));
        Path areaA = Files.createDirectories(projectRoot.resolve("workareas/area-a"));
        Path areaB = Files.createDirectories(projectRoot.resolve("workareas/area-b"));
        Files.writeString(projectRoot.resolve("AGENTS.md"), "root-guidance");
        Files.writeString(areaA.resolve("AGENTS.md"), "area-a-guidance");
        Files.writeString(areaB.resolve("AGENTS.md"), "area-b-guidance");
        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());

        OrchestrationTaskContextHolder.set(projectContext(projectRoot, areaA, "area-a"));
        String promptA = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        OrchestrationTaskContextHolder.set(projectContext(projectRoot, areaB, "area-b"));
        String promptB = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(promptA).contains("area-a-guidance");
        assertThat(promptA).doesNotContain("area-b-guidance");
        assertThat(promptB).contains("area-b-guidance");
        assertThat(promptB).doesNotContain("area-a-guidance");
    }

    @Test
    void switchingSiblingNestedFilesInSameBoundRootDropsStaleLayer() throws Exception {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace/agent-1"));
        Path nestedA = Files.createDirectories(workspaceRoot.resolve("a"));
        Path nestedB = Files.createDirectories(workspaceRoot.resolve("b"));
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "workspace-root-guidance");
        Files.writeString(nestedA.resolve("AGENTS.md"), "nested-a-guidance");
        Files.writeString(nestedB.resolve("AGENTS.md"), "nested-b-guidance");
        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());

        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            null,
            null,
            "TASK_RUN",
            workspaceRoot.resolve("a/file.txt").toString(),
            workspaceRoot.resolve("runs/run-1/outputs").toString(),
            workspaceRoot.toString(),
            workspaceRoot.resolve("runs/run-1").toString(),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        String promptA = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            null,
            null,
            "TASK_RUN",
            workspaceRoot.resolve("b/file.txt").toString(),
            workspaceRoot.resolve("runs/run-1/outputs").toString(),
            workspaceRoot.toString(),
            workspaceRoot.resolve("runs/run-1").toString(),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        String promptB = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(promptA).contains("workspace-root-guidance");
        assertThat(promptA).contains("nested-a-guidance");
        assertThat(promptA).doesNotContain("nested-b-guidance");
        assertThat(promptB).contains("workspace-root-guidance");
        assertThat(promptB).contains("nested-b-guidance");
        assertThat(promptB).doesNotContain("nested-a-guidance");
    }

    @Test
    void fileToolTargetPathUpdatesAgentsMdContextDuringModelBackedRun() throws Exception {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace/agent-1"));
        Path nestedA = Files.createDirectories(workspaceRoot.resolve("a"));
        Path nestedB = Files.createDirectories(workspaceRoot.resolve("b"));
        Path outputRoot = Files.createDirectories(workspaceRoot.resolve("runs/run-1/outputs"));
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "workspace-root-guidance");
        Files.writeString(nestedA.resolve("AGENTS.md"), "nested-a-guidance");
        Files.writeString(nestedB.resolve("AGENTS.md"), "nested-b-guidance");
        Files.writeString(nestedA.resolve("file.txt"), "a\n");
        Files.writeString(nestedB.resolve("file.txt"), "b\n");
        AgentFileToolService fileTool = new AgentFileToolService(aiConfig());
        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            null,
            null,
            "TASK_RUN",
            workspaceRoot.toString(),
            outputRoot.toString(),
            workspaceRoot.toString(),
            workspaceRoot.resolve("runs/run-1").toString(),
            null,
            null,
            null,
            null,
            null,
            null
        ));

        fileTool.read("workspace/a/file.txt", 1, 10);
        String promptA = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");
        fileTool.read("workspace/b/file.txt", 1, 10);
        String promptB = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(OrchestrationTaskContextHolder.current().activeRuntimePath())
            .isEqualTo("workspace/b/file.txt");
        assertThat(promptA).contains("workspace-root-guidance");
        assertThat(promptA).contains("nested-a-guidance");
        assertThat(promptA).doesNotContain("nested-b-guidance");
        assertThat(promptB).contains("workspace-root-guidance");
        assertThat(promptB).contains("nested-b-guidance");
        assertThat(promptB).doesNotContain("nested-a-guidance");
    }

    @Test
    void omitsAgentsMdContextWhenNoLayersExist() throws Exception {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace/agent-1"));
        OrchestrationTaskContext context = new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            null,
            null,
            "TASK_RUN",
            workspaceRoot.toString(),
            workspaceRoot.resolve("runs/run-1/outputs").toString(),
            workspaceRoot.toString(),
            workspaceRoot.resolve("runs/run-1").toString(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        OrchestrationTaskContextHolder.set(context);

        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());
        String prompt = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(prompt).isEqualTo("base-system-prompt");
        assertThat(prompt).doesNotContain("Runtime AGENTS.md Context");
    }

    @Test
    void ordinaryChatContextDoesNotTriggerResolverLookup() {
        CountingResolver resolver = new CountingResolver();
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            null,
            null,
            null,
            null,
            "conversation-1",
            "CHAT",
            tempDir.toString(),
            tempDir.toString()
        ));
        PromptContextAssembler assembler = assemblerWithResolver(resolver);

        String prompt = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(prompt).isEqualTo("base-system-prompt");
        assertThat(resolver.calls).isZero();
    }

    @Test
    void omitsAgentsMdContextWhenAgentContextHasNoBoundRoot() {
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            null,
            null,
            "TASK_RUN",
            null,
            null
        ));
        PromptContextAssembler assembler = assemblerWithResolver(new AgentsMdResolver());

        String prompt = assembler.mergeModePrompt(PlanMode.NORMAL, "conversation-1");

        assertThat(prompt).isEqualTo("base-system-prompt");
        assertThat(prompt).doesNotContain("Runtime AGENTS.md Context");
    }

    private PromptContextAssembler assemblerWithResolver(AgentsMdResolver resolver) {
        RuntimeSettingsService runtimeSettingsService = mock(RuntimeSettingsService.class);
        when(runtimeSettingsService.defaultSystemPrompt()).thenReturn("base-system-prompt");
        return new PromptContextAssembler(
            null,
            runtimeSettingsService,
            null,
            null,
            null,
            resolver
        );
    }

    private AiConfig aiConfig() {
        return new AiConfig(null, null, null, null, null, null, tempDir, null, null, null);
    }

    private OrchestrationTaskContext projectContext(Path projectRoot, Path workAreaRoot, String workAreaId) {
        return new OrchestrationTaskContext(
            "agent-1",
            "Agent One",
            null,
            "project-1",
            null,
            "TASK_RUN",
            workAreaRoot.toString(),
            workAreaRoot.resolve("outputs").toString(),
            workAreaRoot.toString(),
            workAreaRoot.resolve("runs/run-1").toString(),
            null,
            projectRoot.toString(),
            workAreaId,
            null,
            null,
            null
        );
    }

    private static final class CountingResolver extends AgentsMdResolver {
        int calls = 0;

        @Override
        public Optional<AgentsMdResolution> resolveForContext(
            OrchestrationTaskContext context,
            String activePath
        ) throws IOException {
            calls++;
            return Optional.empty();
        }
    }
}
