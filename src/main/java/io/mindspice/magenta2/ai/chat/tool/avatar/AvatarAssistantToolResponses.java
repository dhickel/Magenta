package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.util.List;

public final class AvatarAssistantToolResponses {
    private AvatarAssistantToolResponses() {
    }

    public record TodoListResponse(List<TodoRecord> todos) {
    }

    public record TodoResponse(TodoRecord todo) {
    }

    public record DailyTaskListResponse(List<DailyTaskRecord> tasks) {
    }

    public record DailyTaskResponse(DailyTaskRecord task) {
    }

    public record CalendarListResponse(List<CalendarRecord> items) {
    }

    public record CalendarResponse(CalendarRecord item) {
    }

    public record TodayPlanResponse(
        String date,
        List<TaskRecord> topPriorities,
        List<TaskRecord> now,
        List<TaskRecord> next,
        List<TaskRecord> later,
        List<TaskRecord> overdue,
        List<TaskRecord> unscheduled,
        List<TimeBlockRecord> timeBlocks,
        List<ReminderRecord> reminders
    ) {
    }

    public record TasksRoutinesResponse(
        List<TaskRecord> tasks,
        List<OccurrenceRecord> occurrences,
        List<ReminderRecord> reminders
    ) {
    }

    public record CalendarScheduleResponse(String startDate, String endDate, List<CalendarEntryRecord> entries) {
    }

    public record TaskResponse(TaskRecord task) {
    }

    public record OccurrenceResponse(OccurrenceRecord occurrence) {
    }

    public record ReminderResponse(ReminderRecord reminder) {
    }

    public record TimeBlockResponse(TimeBlockRecord timeBlock) {
    }

    public record DeletedResponse(String id, boolean deleted) {
    }

    public record NoteListResponse(List<NoteRecord> notes) {
    }

    public record NoteResponse(NoteRecord note) {
    }

    public record AssignmentResponse(AssignmentRecord assignment) {
    }

    public record OutputListResponse(int count, int limit, List<OutputRecord> outputs) {
    }

    public record OutputContentResponse(OutputRecord output, int length, String content) {
    }

    public record TodoRecord(
        String id,
        String title,
        String notes,
        String status,
        String priority,
        String dueAt,
        String linkedProjectId,
        String linkedTaskId,
        String linkedOutputId,
        String updatedAt,
        String completedAt
    ) {
    }

    public record DailyTaskRecord(
        String id,
        String taskDate,
        String title,
        String notes,
        String status,
        int position,
        String updatedAt
    ) {
    }

    public record CalendarRecord(
        String id,
        String title,
        String notes,
        String startsAt,
        String endsAt,
        String timezone,
        String location,
        String status,
        String updatedAt
    ) {
    }

    public record TaskRecord(
        String id,
        String title,
        String notes,
        String status,
        String priority,
        String startsAt,
        String dueAt,
        String recurrenceMode,
        String linkedProjectId,
        String updatedAt,
        String completedAt
    ) {
    }

    public record OccurrenceRecord(
        String id,
        String taskId,
        String occurrenceStart,
        String occurrenceEnd,
        String status,
        String skippedAt,
        String snoozedUntil,
        String restartedAt
    ) {
    }

    public record ReminderRecord(
        String id,
        String title,
        String notes,
        String remindAt,
        String status,
        String sourceType,
        String sourceId,
        String snoozedUntil
    ) {
    }

    public record TimeBlockRecord(
        String id,
        String blockDate,
        String title,
        String startsAt,
        String endsAt,
        String sourceType,
        String sourceId,
        String status
    ) {
    }

    public record CalendarEntryRecord(
        String kind,
        String sourceId,
        String title,
        String startsAt,
        String endsAt,
        String status,
        String meta
    ) {
    }

    public record NoteRecord(
        String id,
        String title,
        String snippet,
        List<String> tags,
        boolean archived,
        String updatedAt
    ) {
    }

    public record AssignmentRecord(
        String id,
        String agentId,
        String assignmentType,
        String status,
        int priority,
        String modelOverride,
        String projectId,
        String workspaceId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        String updatedAt
    ) {
    }

    public record OutputRecord(
        String id,
        String runId,
        String planId,
        String agentId,
        String jobId,
        String jobAssignmentId,
        String jobRunId,
        String projectId,
        String workspaceId,
        String runType,
        String outputName,
        String artifactType,
        String fileName,
        String createdAt
    ) {
    }
}
