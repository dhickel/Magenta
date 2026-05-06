package io.mindspice.magenta2.ai.chat.workflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskFieldDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRepository;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.task.TaskStep;
import io.mindspice.magenta2.ai.chat.task.TaskValueType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowServiceTest {

    @Test
    void workflowRunsSequentiallyAndMapsPriorOutputsIntoInputs() throws Exception {
        Services services = services();
        TaskDefinition first = services.taskService().saveTask(task(
            "Research",
            List.of(new TaskFieldDefinition("topic", TaskValueType.STRING, "Topic.", true, null, "SQLite")),
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Notes.", true, null, "notes"))
        ));
        TaskDefinition second = services.taskService().saveTask(task(
            "Summarize",
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Notes.", true, null, null)),
            List.of(new TaskFieldDefinition("structured_summary", TaskValueType.LONG_TEXT, "Summary.", true, null, "summary"))
        ));

        WorkflowDefinition workflow = services.workflowService().saveWorkflow(new WorkflowDefinition(
            null,
            "Research workflow",
            "Two steps.",
            List.of(
                new WorkflowStep("step_1", first.id(), List.of(
                    new WorkflowInputBinding("topic", WorkflowBindingKind.LITERAL, "SQLite", null, null)
                )),
                new WorkflowStep("step_2", second.id(), List.of(
                    new WorkflowInputBinding("research_notes", WorkflowBindingKind.STEP_OUTPUT, null, "step_1", "research_notes")
                ))
            ),
            null,
            null
        ));

        WorkflowRun run = services.workflowService().runSynchronously(workflow.id());

        assertThat(run.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(run.stepRuns()).hasSize(2);
        assertThat(run.stepRuns().get(1).inputValues()).containsKey("research_notes");
        assertThat(run.finalOutputs()).containsKey("structured_summary");
    }

    @Test
    void missingRequiredInputBlocksRunAndTypeMismatchIsWarningOnly() throws Exception {
        Services services = services();
        TaskDefinition first = services.taskService().saveTask(task(
            "Count",
            List.of(),
            List.of(new TaskFieldDefinition("count", TaskValueType.NUMBER, "Count.", true, null, null))
        ));
        TaskDefinition second = services.taskService().saveTask(task(
            "Write",
            List.of(new TaskFieldDefinition("notes", TaskValueType.LONG_TEXT, "Notes.", true, null, null)),
            List.of(new TaskFieldDefinition("report", TaskValueType.LONG_TEXT, "Report.", true, null, null))
        ));

        WorkflowDefinition workflow = services.workflowService().saveWorkflow(new WorkflowDefinition(
            null,
            "Warning workflow",
            null,
            List.of(
                new WorkflowStep("step_1", first.id(), List.of()),
                new WorkflowStep("step_2", second.id(), List.of(
                    new WorkflowInputBinding("notes", WorkflowBindingKind.STEP_OUTPUT, null, "step_1", "count")
                ))
            ),
            null,
            null
        ));

        assertThat(services.workflowService().compatibilityWarnings(workflow))
            .contains("Type mismatch: step_1.count is number but step_2.notes expects long_text");
        assertThat(services.workflowService().runSynchronously(workflow.id()).status())
            .isEqualTo(WorkflowRunStatus.COMPLETED);

        WorkflowDefinition missing = services.workflowService().saveWorkflow(new WorkflowDefinition(
            null,
            "Missing workflow",
            null,
            List.of(new WorkflowStep("a", first.id(), List.of()), new WorkflowStep("b", second.id(), List.of())),
            null,
            null
        ));
        assertThat(services.workflowService().runSynchronously(missing.id()).status()).isEqualTo(WorkflowRunStatus.FAILED);
    }

    private TaskDefinition task(String title, List<TaskFieldDefinition> inputs, List<TaskFieldDefinition> outputs) {
        return new TaskDefinition(
            null,
            title,
            null,
            "Goal",
            null,
            null,
            inputs,
            null,
            outputs,
            List.of(),
            List.of(new TaskStep(1, "Do the work.")),
            List.of("Output exists."),
            null,
            null
        );
    }

    private Services services() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        TaskService taskService = new TaskService(new TaskRepository(jdbcTemplate, objectMapper));
        WorkflowService workflowService = new WorkflowService(new WorkflowRepository(jdbcTemplate, objectMapper), taskService);
        return new Services(taskService, workflowService);
    }

    private JdbcTemplate jdbcTemplate() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : Files.readString(Path.of("src/main/resources/schema.sql")).split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        return jdbcTemplate;
    }

    private record Services(TaskService taskService, WorkflowService workflowService) {
    }
}
