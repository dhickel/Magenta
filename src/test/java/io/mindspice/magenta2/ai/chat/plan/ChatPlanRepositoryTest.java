package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.List;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPlanRepositoryTest {

    @Test
    void savesAndReplacesConversationPlan() {
        ChatPlanRepository repository = new ChatPlanRepository(jdbcTemplate(), new ObjectMapper());
        ExecutionPlan first = plan("conversation-1", "First", List.of("Inspect", "Implement"));
        repository.save(first);

        repository.save(plan("conversation-1", "Second", List.of("Execute")));

        ExecutionPlan saved = repository.find("conversation-1").orElseThrow();
        assertThat(saved.title()).isEqualTo("Second");
        assertThat(saved.notes()).contains("Plan notes");
        assertThat(saved.assumptions()).containsExactly("Use commands");
        assertThat(saved.deliverables()).containsExactly("Deliverable");
        assertThat(saved.steps()).extracting(PlanStep::text).containsExactly("Execute");
        assertThat(saved.acceptanceCriteria()).containsExactly("Show evidence");
        assertThat(saved.executionEvidence()).containsExactly("Evidence: checked");
    }

    @Test
    void unwrapsLegacyCdataPlanStepsWhenLoading() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatPlanRepository repository = new ChatPlanRepository(jdbcTemplate, new ObjectMapper());
        repository.save(plan("conversation-1", "Plan", List.of("placeholder")));
        jdbcTemplate.update(
            "update ai_chat_plan_steps set step_text = ? where conversation_id = ? and step_order = 1",
            "<![CDATA[**Inspect** the current state and verify output.]]>",
            "conversation-1"
        );

        ExecutionPlan saved = repository.find("conversation-1").orElseThrow();

        assertThat(saved.steps()).extracting(PlanStep::text)
            .containsExactly("**Inspect** the current state and verify output.");
    }

    private ExecutionPlan plan(String conversationId, String title, List<String> steps) {
        Instant now = Instant.now();
        return new ExecutionPlan(
            conversationId,
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            "Goal",
            title,
            "Summary",
            "Plan notes",
            List.of("Deliverable"),
            List.of("Use commands"),
            steps.stream()
                .map(step -> new PlanStep(steps.indexOf(step) + 1, step))
                .toList(),
            List.of("Show evidence"),
            List.of("Evidence: checked"),
            "qwen3",
            PlanPrompt.none(),
            List.of(new PlanAnswer("Question?", "Answer", "Notes", now.toString())),
            3,
            now,
            now
        );
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
