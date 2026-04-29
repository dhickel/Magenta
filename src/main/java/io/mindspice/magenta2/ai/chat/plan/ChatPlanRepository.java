package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
public class ChatPlanRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatPlanRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        ensureTables();
    }

    public Optional<ExecutionPlan> find(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
                select conversation_id, mode, status, goal, title, summary, notes, assumptions_json,
                       acceptance_criteria_json, execution_evidence_json,
                       plan_start_message_order, created_at, updated_at
                from ai_chat_plans
                where conversation_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String id = rs.getString("conversation_id");
                return Optional.of(new ExecutionPlan(
                    id,
                    PlanMode.valueOf(rs.getString("mode")),
                    PlanStatus.valueOf(rs.getString("status")),
                    rs.getString("goal"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("notes"),
                    stringList(rs.getString("assumptions_json")),
                    steps(id),
                    stringList(rs.getString("acceptance_criteria_json")),
                    stringList(rs.getString("execution_evidence_json")),
                    rs.getInt("plan_start_message_order"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("updated_at"))
                ));
            },
            conversationId
        );
    }

    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
            "select conversation_id from ai_chat_plans order by updated_at desc",
            String.class
        );
    }

    @Transactional
    public ExecutionPlan save(ExecutionPlan plan) {
        Instant createdAt = plan.createdAt() == null ? Instant.now() : plan.createdAt();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
            """
                insert into ai_chat_plans (
                    conversation_id, mode, status, goal, title, summary, notes, assumptions_json,
                    acceptance_criteria_json, execution_evidence_json, plan_start_message_order, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(conversation_id) do update set
                    mode = excluded.mode,
                    status = excluded.status,
                    goal = excluded.goal,
                    title = excluded.title,
                    summary = excluded.summary,
                    notes = excluded.notes,
                    assumptions_json = excluded.assumptions_json,
                    acceptance_criteria_json = excluded.acceptance_criteria_json,
                    execution_evidence_json = excluded.execution_evidence_json,
                    plan_start_message_order = excluded.plan_start_message_order,
                    updated_at = excluded.updated_at
                """,
            plan.conversationId(),
            plan.mode().name(),
            plan.status().name(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            stringListJson(plan.assumptions()),
            stringListJson(plan.acceptanceCriteria()),
            stringListJson(plan.executionEvidence()),
            plan.planStartMessageOrder(),
            createdAt.toString(),
            updatedAt.toString()
        );
        jdbcTemplate.update("delete from ai_chat_plan_steps where conversation_id = ?", plan.conversationId());
        List<PlanStep> steps = plan.steps() == null ? List.of() : plan.steps();
        for (PlanStep step : steps) {
            jdbcTemplate.update(
                """
                    insert into ai_chat_plan_steps (conversation_id, step_order, step_text)
                    values (?, ?, ?)
                    """,
                plan.conversationId(),
                step.order(),
                step.text()
            );
        }
        return new ExecutionPlan(
            plan.conversationId(),
            plan.mode(),
            plan.status(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.assumptions() == null ? List.of() : List.copyOf(plan.assumptions()),
            List.copyOf(steps),
            plan.acceptanceCriteria() == null ? List.of() : List.copyOf(plan.acceptanceCriteria()),
            plan.executionEvidence() == null ? List.of() : List.copyOf(plan.executionEvidence()),
            plan.planStartMessageOrder(),
            createdAt,
            updatedAt
        );
    }

    @Transactional
    public void delete(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        jdbcTemplate.update("delete from ai_chat_plan_steps where conversation_id = ?", conversationId);
        jdbcTemplate.update("delete from ai_chat_plans where conversation_id = ?", conversationId);
    }

    private List<PlanStep> steps(String conversationId) {
        return jdbcTemplate.query(
            """
                select step_order, step_text
                from ai_chat_plan_steps
                where conversation_id = ?
                order by step_order asc
                """,
            (rs, rowNum) -> new PlanStep(rs.getInt("step_order"), rs.getString("step_text")),
            conversationId
        );
    }

    private List<String> stringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse plan list", exception);
        }
    }

    private String stringListJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize plan list", exception);
        }
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
            create table if not exists ai_chat_plans (
                conversation_id text primary key,
                mode text not null,
                status text not null,
                goal text,
                title text,
                summary text,
                notes text,
                assumptions_json text,
                acceptance_criteria_json text,
                execution_evidence_json text,
                plan_start_message_order integer not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        List<String> planColumns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('ai_chat_plans')",
            String.class
        );
        if (!planColumns.contains("notes")) {
            jdbcTemplate.execute("alter table ai_chat_plans add column notes text");
        }
        if (!planColumns.contains("acceptance_criteria_json")) {
            jdbcTemplate.execute("alter table ai_chat_plans add column acceptance_criteria_json text");
        }
        if (!planColumns.contains("execution_evidence_json")) {
            jdbcTemplate.execute("alter table ai_chat_plans add column execution_evidence_json text");
        }
        jdbcTemplate.execute("""
            create table if not exists ai_chat_plan_steps (
                conversation_id text not null,
                step_order integer not null,
                step_text text not null,
                primary key (conversation_id, step_order),
                foreign key (conversation_id) references ai_chat_plans(conversation_id) on delete cascade
            )
            """);
    }
}
