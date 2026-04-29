package io.mindspice.magenta2.ai.chat.plan;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import io.mindspice.magenta2.ai.chat.repository.SQLiteChatMemoryRepository;

import static org.assertj.core.api.Assertions.assertThat;

class PlanServiceTest {

    @Test
    void exitPlanTrimsMessagesCreatedAfterPlanStarted() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        SQLiteChatMemoryRepository memoryRepository = new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before")));
        service.beginPlan("conversation-1");
        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before"), new UserMessage("during")));

        service.exitPlan("conversation-1");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("before");
        assertThat(service.activePlan("conversation-1")).isEmpty();
    }

    @Test
    void runtimeInstructionsExposeCompactPlanState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        SQLiteChatMemoryRepository memoryRepository = new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

        service.beginPlan("conversation-1");
        service.saveDraftPlan(
            "conversation-1",
            "Add plan mode",
            "Plan Mode",
            "Add streamlined planning.",
            "Do not alter existing command names.",
            List.of("Add state", "Inject prompt"),
            List.of("Use slash commands")
        );
        service.markExecuting("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("Mode: EXECUTE_PLAN")
            .contains("Plan: Plan Mode")
            .contains("Notes: Do not alter existing command names.")
            .contains("1. Add state")
            .contains("Use slash commands");
    }

    @Test
    void planModeInstructionsAreStandalonePlanningPrompt() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        SQLiteChatMemoryRepository memoryRepository = new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

        service.beginPlan("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("You are Magenta in PLAN mode")
            .contains("Begin by asking the user what goal they want to plan")
            .contains("Do not perform the saved plan's implementation work")
            .contains("Shell access is available in plan mode")
            .contains("call plan_save with the clarified goal")
            .contains("notes");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
