package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.List;

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
        assertThat(saved.notes()).isEqualTo("Plan notes");
        assertThat(saved.steps()).extracting(PlanStep::text).containsExactly("Execute");
        assertThat(saved.assumptions()).containsExactly("Use commands");
        assertThat(saved.acceptanceCriteria()).containsExactly("Show evidence");
        assertThat(saved.executionEvidence()).containsExactly("Evidence: checked");
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
            List.of("Use commands"),
            steps.stream()
                .map(step -> new PlanStep(steps.indexOf(step) + 1, step))
                .toList(),
            List.of("Show evidence"),
            List.of("Evidence: checked"),
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
