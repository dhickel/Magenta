package io.mindspice.magenta2.api.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

class TaskControllerTest {

    @Test
    void taskApiIgnoresLegacyDeliverablesAndDoesNotExposeThem() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskController controller = new TaskController(taskService());
        TaskDefinition request = objectMapper.readValue(
            """
                {
                  "title": "Research task",
                  "goal": "Collect notes.",
                  "deliverables": ["Legacy deliverable"],
                  "outputs": [{"name":"research_notes","type":"long_text","description":"Notes.","required":true}],
                  "steps": [{"order":1,"text":"Collect notes."}],
                  "validationCriteria": ["research_notes is present."]
                }
                """,
            TaskDefinition.class
        );

        TaskDefinition response = controller.create(request);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.outputs()).extracting(TaskFieldDefinition::name).containsExactly("research_notes");
        assertThat(json).contains("outputs");
        assertThat(json).doesNotContain("deliverables");
    }

    @Test
    void updateUsesPathIdWithoutDeliverables() throws Exception {
        TaskController controller = new TaskController(taskService());

        TaskDefinition response = controller.update("task-1", new TaskDefinition(
            null,
            "Research task",
            null,
            "Collect notes.",
            null,
            null,
            List.of(),
            null,
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Notes.", true, null, null)),
            List.of(),
            List.of(new TaskStep(1, "Collect notes.")),
            List.of("research_notes is present."),
            null,
            null
        ));

        assertThat(response.id()).isEqualTo("task-1");
        assertThat(response.outputs()).extracting(TaskFieldDefinition::name).containsExactly("research_notes");
    }

    private TaskService taskService() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : Files.readString(Path.of("src/main/resources/schema.sql")).split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        return new TaskService(new TaskRepository(jdbcTemplate, new ObjectMapper()));
    }
}
