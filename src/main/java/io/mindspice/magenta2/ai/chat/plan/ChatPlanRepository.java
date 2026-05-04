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
                select conversation_id, mode, status, goal, title, summary, notes,
                       planning_task, deliverables_json, inputs_json, outputs_json, assumptions_json,
                       acceptance_criteria_json, execution_evidence_json, validation_feedback_json,
                       pre_planning_model, execution_model, pending_questions_json, pending_question_index,
                       plan_start_message_order, final_message, created_at, updated_at
                from ai_chat_plans
                where conversation_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ExecutionPlan(
                    rs.getString("conversation_id"),
                    PlanMode.valueOf(rs.getString("mode")),
                    PlanStatus.valueOf(rs.getString("status")),
                    rs.getString("planning_task"),
                    rs.getString("goal"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("notes"),
                    stringList(rs.getString("deliverables_json")),
                    stringList(rs.getString("inputs_json")),
                    stringList(rs.getString("outputs_json")),
                    stringList(rs.getString("assumptions_json")),
                    steps(rs.getString("conversation_id")),
                    stringList(rs.getString("acceptance_criteria_json")),
                    stringList(rs.getString("execution_evidence_json")),
                    stringList(rs.getString("validation_feedback_json")),
                    rs.getString("pre_planning_model"),
                    rs.getString("execution_model"),
                    stringList(rs.getString("pending_questions_json")),
                    rs.getInt("pending_question_index"),
                    rs.getInt("plan_start_message_order"),
                    rs.getString("final_message"),
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
                    conversation_id, mode, status, planning_task, goal, title, summary, notes,
                    deliverables_json, inputs_json, outputs_json, assumptions_json,
                    acceptance_criteria_json, execution_evidence_json, validation_feedback_json,
                    pre_planning_model, execution_model, pending_questions_json, pending_question_index,
                    plan_start_message_order, final_message, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(conversation_id) do update set
                    mode = excluded.mode,
                    status = excluded.status,
                    planning_task = excluded.planning_task,
                    goal = excluded.goal,
                    title = excluded.title,
                    summary = excluded.summary,
                    notes = excluded.notes,
                    deliverables_json = excluded.deliverables_json,
                    inputs_json = excluded.inputs_json,
                    outputs_json = excluded.outputs_json,
                    assumptions_json = excluded.assumptions_json,
                    acceptance_criteria_json = excluded.acceptance_criteria_json,
                    execution_evidence_json = excluded.execution_evidence_json,
                    validation_feedback_json = excluded.validation_feedback_json,
                    pre_planning_model = excluded.pre_planning_model,
                    execution_model = excluded.execution_model,
                    pending_questions_json = excluded.pending_questions_json,
                    pending_question_index = excluded.pending_question_index,
                    plan_start_message_order = excluded.plan_start_message_order,
                    final_message = excluded.final_message,
                    updated_at = excluded.updated_at
                """,
            plan.conversationId(),
            plan.mode().name(),
            plan.status().name(),
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            stringListJson(plan.deliverables()),
            stringListJson(plan.inputs()),
            stringListJson(plan.outputs()),
            stringListJson(plan.assumptions()),
            stringListJson(plan.acceptanceCriteria()),
            stringListJson(plan.executionEvidence()),
            stringListJson(plan.validationFeedback()),
            plan.prePlanningModel(),
            plan.executionModel(),
            stringListJson(plan.pendingQuestions()),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            createdAt.toString(),
            updatedAt.toString()
        );

        jdbcTemplate.update("delete from ai_chat_plan_steps where conversation_id = ?", plan.conversationId());
        for (PlanStep step : plan.steps()) {
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
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            plan.pendingQuestions(),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
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
                planning_task text,
                goal text,
                title text,
                summary text,
                notes text,
                deliverables_json text,
                inputs_json text,
                outputs_json text,
                assumptions_json text,
                acceptance_criteria_json text,
                execution_evidence_json text,
                validation_feedback_json text,
                pre_planning_model text,
                execution_model text,
                pending_questions_json text,
                pending_question_index integer not null default 0,
                plan_start_message_order integer not null,
                created_at text not null,
                updated_at text not null
            )
            """);
        List<String> columns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('ai_chat_plans')",
            String.class
        );
        addColumn(columns, "planning_task", "alter table ai_chat_plans add column planning_task text");
        addColumn(columns, "notes", "alter table ai_chat_plans add column notes text");
        addColumn(columns, "deliverables_json", "alter table ai_chat_plans add column deliverables_json text");
        addColumn(columns, "inputs_json", "alter table ai_chat_plans add column inputs_json text");
        addColumn(columns, "outputs_json", "alter table ai_chat_plans add column outputs_json text");
        addColumn(columns, "assumptions_json", "alter table ai_chat_plans add column assumptions_json text");
        addColumn(columns, "acceptance_criteria_json", "alter table ai_chat_plans add column acceptance_criteria_json text");
        addColumn(columns, "execution_evidence_json", "alter table ai_chat_plans add column execution_evidence_json text");
        addColumn(columns, "validation_feedback_json", "alter table ai_chat_plans add column validation_feedback_json text");
        addColumn(columns, "pre_planning_model", "alter table ai_chat_plans add column pre_planning_model text");
        addColumn(columns, "execution_model", "alter table ai_chat_plans add column execution_model text");
        addColumn(columns, "pending_questions_json", "alter table ai_chat_plans add column pending_questions_json text");
        addColumn(columns, "pending_question_index", "alter table ai_chat_plans add column pending_question_index integer not null default 0");
        addColumn(columns, "final_message", "alter table ai_chat_plans add column final_message text");
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

    private void addColumn(List<String> columns, String column, String ddl) {
        if (!columns.contains(column)) {
            jdbcTemplate.execute(ddl);
        }
    }
}
