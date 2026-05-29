package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AvatarAssistantTools {
    private final AvatarAssistantToolService service;
    private final ObjectMapper objectMapper;

    public AvatarAssistantTools(AvatarAssistantToolService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "avatar_todo_list", description = "Avatar supervisor only: list Avatar organizer todos as compact JSON.")
    public String avatarTodoList(
        @ToolParam(required = false, description = "Optional status filter: OPEN, IN_PROGRESS, DONE, or CANCELED.")
        String status,
        @ToolParam(required = false, description = "Whether DONE and CANCELED todos should be included. Defaults to false.")
        Boolean includeDone,
        @ToolParam(required = false, description = "Maximum todos to return, bounded to 1..100.")
        Integer limit
    ) {
        return json(service.todoList(status, includeDone, limit));
    }

    @Tool(name = "avatar_todo_upsert", description = "Avatar supervisor only: create or update one Avatar organizer todo.")
    public String avatarTodoUpsert(
        @ToolParam(required = false, description = "Existing todo id to update, or omit to create.")
        String id,
        @ToolParam(required = false, description = "Todo title. Required when creating.")
        String title,
        @ToolParam(required = false, description = "Optional todo notes.")
        String notes,
        @ToolParam(required = false, description = "Todo status: OPEN, IN_PROGRESS, DONE, or CANCELED.")
        String status,
        @ToolParam(required = false, description = "Todo priority: LOW, NORMAL, HIGH, or URGENT.")
        String priority,
        @ToolParam(required = false, description = "Optional ISO-8601 due instant.")
        String dueAt,
        @ToolParam(required = false, description = "Optional linked project id.")
        String linkedProjectId,
        @ToolParam(required = false, description = "Optional linked task id.")
        String linkedTaskId,
        @ToolParam(required = false, description = "Optional linked output id.")
        String linkedOutputId
    ) {
        return json(service.todoUpsert(
            id, title, notes, status, priority, dueAt, linkedProjectId, linkedTaskId, linkedOutputId
        ));
    }

    @Tool(name = "avatar_todo_complete", description = "Avatar supervisor only: mark one Avatar organizer todo complete.")
    public String avatarTodoComplete(@ToolParam(description = "Todo id to complete.") String id) {
        return json(service.todoComplete(id));
    }

    @Tool(name = "avatar_daily_task_list", description = "Avatar supervisor only: list Avatar daily tasks by optional date.")
    public String avatarDailyTaskList(
        @ToolParam(required = false, description = "Optional ISO date such as 2026-05-22.")
        String date,
        @ToolParam(required = false, description = "Whether DONE tasks should be included. Defaults to false.")
        Boolean includeDone,
        @ToolParam(required = false, description = "Maximum tasks to return, bounded to 1..100.")
        Integer limit
    ) {
        return json(service.dailyTaskList(date, includeDone, limit));
    }

    @Tool(name = "avatar_daily_task_upsert", description = "Avatar supervisor only: create or update one Avatar daily task.")
    public String avatarDailyTaskUpsert(
        @ToolParam(required = false, description = "Existing daily task id to update, or omit to create.")
        String id,
        @ToolParam(required = false, description = "ISO date such as 2026-05-22. Required when creating.")
        String date,
        @ToolParam(required = false, description = "Daily task title. Required when creating.")
        String title,
        @ToolParam(required = false, description = "Optional daily task notes.")
        String notes,
        @ToolParam(required = false, description = "Daily task status: PLANNED, ACTIVE, DONE, or SKIPPED.")
        String status,
        @ToolParam(required = false, description = "Optional display/order position.")
        Integer position
    ) {
        return json(service.dailyTaskUpsert(id, date, title, notes, status, position));
    }

    @Tool(name = "avatar_daily_task_complete", description = "Avatar supervisor only: mark one Avatar daily task done.")
    public String avatarDailyTaskComplete(@ToolParam(description = "Daily task id to complete.") String id) {
        return json(service.dailyTaskComplete(id));
    }

    @Tool(name = "avatar_calendar_list", description = "Avatar supervisor only: list local Avatar calendar items as compact JSON.")
    public String avatarCalendarList(
        @ToolParam(required = false, description = "Optional inclusive lower bound ISO-8601 start instant.")
        String startsAfter,
        @ToolParam(required = false, description = "Optional exclusive upper bound ISO-8601 start instant.")
        String startsBefore,
        @ToolParam(required = false, description = "Whether CANCELED items should be included. Defaults to false.")
        Boolean includeCanceled,
        @ToolParam(required = false, description = "Maximum items to return, bounded to 1..100.")
        Integer limit
    ) {
        return json(service.calendarList(startsAfter, startsBefore, includeCanceled, limit));
    }

    @Tool(name = "avatar_calendar_upsert", description = "Avatar supervisor only: create or update one local Avatar calendar item.")
    public String avatarCalendarUpsert(
        @ToolParam(required = false, description = "Existing calendar item id to update, or omit to create.")
        String id,
        @ToolParam(required = false, description = "Calendar item title. Required when creating.")
        String title,
        @ToolParam(required = false, description = "Optional calendar item notes.")
        String notes,
        @ToolParam(required = false, description = "ISO-8601 start instant. Required when creating.")
        String startsAt,
        @ToolParam(required = false, description = "Optional ISO-8601 end instant.")
        String endsAt,
        @ToolParam(required = false, description = "Optional timezone id.")
        String timezone,
        @ToolParam(required = false, description = "Optional location.")
        String location,
        @ToolParam(required = false, description = "Calendar status: SCHEDULED, DONE, or CANCELED.")
        String status
    ) {
        return json(service.calendarUpsert(id, title, notes, startsAt, endsAt, timezone, location, status));
    }

    @Tool(name = "avatar_calendar_delete", description = "Avatar supervisor only: delete one local Avatar calendar item.")
    public String avatarCalendarDelete(@ToolParam(description = "Calendar item id to delete.") String id) {
        return json(service.calendarDelete(id));
    }

    @Tool(name = "avatar_today_plan_get", description = "Avatar supervisor only: read the Today Planner summary with priorities, day map, time blocks, reminders, overdue, and unscheduled tasks.")
    public String avatarTodayPlanGet(
        @ToolParam(required = false, description = "Optional ISO date such as 2026-05-29. Defaults to today.")
        String date
    ) {
        return json(service.todayPlanGet(date));
    }

    @Tool(name = "avatar_today_plan_update", description = "Avatar supervisor only: update in-dashboard Today Planner review metadata or restart the day.")
    public String avatarTodayPlanUpdate(
        @ToolParam(required = false, description = "Optional ISO date such as 2026-05-29. Defaults to today.")
        String date,
        @ToolParam(required = false, description = "Optional daily review notes.")
        String reviewNotes,
        @ToolParam(required = false, description = "Whether to mark this date as restarted.")
        Boolean restart
    ) {
        return json(service.todayPlanUpdate(date, reviewNotes, restart));
    }

    @Tool(name = "avatar_quick_capture", description = "Avatar supervisor only: quickly capture an unscheduled planner task.")
    public String avatarQuickCapture(
        @ToolParam(description = "Task title to capture.")
        String title,
        @ToolParam(required = false, description = "Optional task notes.")
        String notes
    ) {
        return json(service.quickCapture(title, notes));
    }

    @Tool(name = "avatar_day_restart", description = "Avatar supervisor only: non-punitively restart a planner day.")
    public String avatarDayRestart(
        @ToolParam(required = false, description = "Optional ISO date such as 2026-05-29. Defaults to today.")
        String date
    ) {
        return json(service.dayRestart(date));
    }

    @Tool(name = "avatar_tasks_routines_get", description = "Avatar supervisor only: list planner tasks, routines, occurrences, reminders, and recurrence state.")
    public String avatarTasksRoutinesGet(
        @ToolParam(required = false, description = "Maximum tasks/occurrences/reminders to return, bounded to 1..100.")
        Integer limit
    ) {
        return json(service.tasksRoutinesGet(limit));
    }

    @Tool(name = "avatar_task_upsert", description = "Avatar supervisor only: create or update one planner task/routine with optional recurrence.")
    public String avatarTaskUpsert(
        @ToolParam(required = false, description = "Existing planner task id to update, or omit to create.")
        String id,
        @ToolParam(required = false, description = "Task title. Required when creating.")
        String title,
        @ToolParam(required = false, description = "Optional notes.")
        String notes,
        @ToolParam(required = false, description = "Status: PLANNED, ACTIVE, WAITING, DONE, or CANCELLED.")
        String status,
        @ToolParam(required = false, description = "Priority: LOW, NORMAL, HIGH, or URGENT.")
        String priority,
        @ToolParam(required = false, description = "Optional scheduled start instant.")
        String startsAt,
        @ToolParam(required = false, description = "Optional due instant.")
        String dueAt,
        @ToolParam(required = false, description = "Recurrence mode: NONE, DAILY, WEEKLY, MONTHLY, or CRON.")
        String recurrenceMode,
        @ToolParam(required = false, description = "Optional linked project id.")
        String projectId
    ) {
        return json(service.taskUpsert(id, title, notes, status, priority, startsAt, dueAt, recurrenceMode, projectId));
    }

    @Tool(name = "avatar_task_occurrence_update", description = "Avatar supervisor only: skip, snooze, or restart a recurring task occurrence without completing the whole task.")
    public String avatarTaskOccurrenceUpdate(
        @ToolParam(description = "Planner task id.")
        String taskId,
        @ToolParam(description = "Occurrence start ISO instant.")
        String occurrenceStart,
        @ToolParam(description = "Action: SKIPPED, SNOOZED, or RESTARTED.")
        String action,
        @ToolParam(required = false, description = "Optional ISO instant used when action is SNOOZED.")
        String snoozedUntil
    ) {
        return json(service.taskOccurrenceUpdate(taskId, occurrenceStart, action, snoozedUntil));
    }

    @Tool(name = "avatar_calendar_schedule_get", description = "Avatar supervisor only: read merged calendar/schedule entries with events, time blocks, reminders, and recurrence projections.")
    public String avatarCalendarScheduleGet(
        @ToolParam(required = false, description = "Optional ISO start date.")
        String startDate,
        @ToolParam(required = false, description = "Optional ISO end date.")
        String endDate
    ) {
        return json(service.calendarScheduleGet(startDate, endDate));
    }

    @Tool(name = "avatar_timeblock_upsert", description = "Avatar supervisor only: create or update one scheduled planner time block.")
    public String avatarTimeblockUpsert(
        @ToolParam(required = false, description = "Existing time block id to update, or omit to create.")
        String id,
        @ToolParam(description = "ISO date for the block.")
        String date,
        @ToolParam(description = "Time block title.")
        String title,
        @ToolParam(description = "ISO start instant.")
        String startsAt,
        @ToolParam(required = false, description = "Optional ISO end instant.")
        String endsAt,
        @ToolParam(required = false, description = "Optional source type such as task.")
        String sourceType,
        @ToolParam(required = false, description = "Optional linked source id.")
        String sourceId
    ) {
        return json(service.timeblockUpsert(id, date, title, startsAt, endsAt, sourceType, sourceId));
    }

    @Tool(name = "avatar_reminder_upsert", description = "Avatar supervisor only: create or update one in-dashboard reminder record.")
    public String avatarReminderUpsert(
        @ToolParam(required = false, description = "Existing reminder id to update, or omit to create.")
        String id,
        @ToolParam(description = "Reminder title.")
        String title,
        @ToolParam(required = false, description = "Optional reminder notes.")
        String notes,
        @ToolParam(description = "ISO remind-at instant.")
        String remindAt,
        @ToolParam(required = false, description = "Reminder status: OPEN, SNOOZED, DONE, DISMISSED, or CANCELED.")
        String status,
        @ToolParam(required = false, description = "Optional source type such as task or calendar.")
        String sourceType,
        @ToolParam(required = false, description = "Optional linked source id.")
        String sourceId,
        @ToolParam(required = false, description = "Optional ISO snoozed-until instant.")
        String snoozedUntil
    ) {
        return json(service.reminderUpsert(id, title, notes, remindAt, status, sourceType, sourceId, snoozedUntil));
    }

    @Tool(name = "avatar_note_append", description = "Avatar supervisor only: create a note or append text to an existing Avatar note.")
    public String avatarNoteAppend(
        @ToolParam(required = false, description = "Existing note id to append to, or omit to create.")
        String id,
        @ToolParam(required = false, description = "Note title. Used for new notes or retitling an existing note.")
        String title,
        @ToolParam(description = "Text to append to the note.")
        String body,
        @ToolParam(required = false, description = "Optional note tags.")
        List<String> tags
    ) {
        return json(service.noteAppend(id, title, body, tags));
    }

    @Tool(name = "avatar_note_search", description = "Avatar supervisor only: search Avatar notes by title, body, or tag.")
    public String avatarNoteSearch(
        @ToolParam(required = false, description = "Search query. Omit for recent notes.")
        String query,
        @ToolParam(required = false, description = "Whether archived notes should be included. Defaults to false.")
        Boolean includeArchived,
        @ToolParam(required = false, description = "Maximum notes to return, bounded to 1..100.")
        Integer limit
    ) {
        return json(service.noteSearch(query, includeArchived, limit));
    }

    @Tool(name = "avatar_file_note_read", description = "Avatar supervisor only: read one project or Work Area file-backed note through confined services.")
    public String avatarFileNoteRead(
        @ToolParam(description = "Source mode: project or work_area.")
        String source,
        @ToolParam(description = "Project id or Work Area id, matching source.")
        String bindingId,
        @ToolParam(description = "Confined note path to read.")
        String path
    ) {
        return json(service.fileNoteRead(source, bindingId, path));
    }

    @Tool(name = "avatar_file_note_update", description = "Avatar supervisor only: update one project or Work Area file-backed note through confined services.")
    public String avatarFileNoteUpdate(
        @ToolParam(description = "Source mode: project or work_area.")
        String source,
        @ToolParam(description = "Project id or Work Area id, matching source.")
        String bindingId,
        @ToolParam(description = "Confined note path to update.")
        String path,
        @ToolParam(description = "Full replacement note content.")
        String content
    ) {
        return json(service.fileNoteUpdate(source, bindingId, path, content));
    }

    @Tool(name = "avatar_project_context_get", description = "Avatar supervisor only: read typed household/code project context artifacts, notes, outputs, and progress.")
    public String avatarProjectContextGet(
        @ToolParam(description = "Project id to inspect.")
        String projectId
    ) {
        return json(service.projectContextGet(projectId));
    }

    @Tool(name = "avatar_project_artifact_update", description = "Avatar supervisor only: validate and update one typed project artifact JSON file.")
    public String avatarProjectArtifactUpdate(
        @ToolParam(description = "Project id to update.")
        String projectId,
        @ToolParam(description = "Artifact type: goals, materials, contacts, blockers, next-actions, or progress.")
        String artifactType,
        @ToolParam(description = "Full JSON artifact content matching the artifact schema.")
        String content
    ) {
        return json(service.projectArtifactUpdate(projectId, artifactType, content));
    }

    @Tool(name = "avatar_submit_task", description = "Avatar supervisor only: submit an approved task to an agent assignment queue.")
    public String avatarSubmitTask(
        @ToolParam(description = "Task id to run.")
        String taskId,
        @ToolParam(description = "Target agent id.")
        String agentId,
        @ToolParam(required = false, description = "Task input values object.")
        Map<String, Object> inputValues,
        @ToolParam(required = false, description = "Optional source conversation id.")
        String conversationId,
        @ToolParam(required = false, description = "Optional project id for project-scoped execution.")
        String projectId,
        @ToolParam(required = false, description = "Optional compatibility workspace id.")
        String workspaceId,
        @ToolParam(required = false, description = "Optional model override key.")
        String modelOverride,
        @ToolParam(required = false, description = "Optional queue priority. Defaults to runtime behavior.")
        Integer priority
    ) {
        return json(service.submitTask(
            taskId, agentId, inputValues, conversationId, projectId, workspaceId, modelOverride, priority
        ));
    }

    @Tool(name = "avatar_submit_research_assignment", description = "Avatar supervisor only: submit a research-oriented task run to an agent assignment queue.")
    public String avatarSubmitResearchAssignment(
        @ToolParam(description = "Task id to run. The task must already exist.")
        String taskId,
        @ToolParam(description = "Target agent id.")
        String agentId,
        @ToolParam(description = "Research question or objective.")
        String researchQuestion,
        @ToolParam(required = false, description = "Optional instructions for the assigned agent.")
        String instructions,
        @ToolParam(required = false, description = "Optional source or search hints.")
        List<String> sourceHints,
        @ToolParam(required = false, description = "Optional project id for project-scoped execution.")
        String projectId,
        @ToolParam(required = false, description = "Optional compatibility workspace id.")
        String workspaceId,
        @ToolParam(required = false, description = "Optional model override key.")
        String modelOverride,
        @ToolParam(required = false, description = "Optional queue priority. Defaults to runtime behavior.")
        Integer priority
    ) {
        return json(service.submitResearchAssignment(
            taskId, agentId, researchQuestion, instructions, sourceHints, projectId, workspaceId, modelOverride, priority
        ));
    }

    @Tool(name = "avatar_list_outputs", description = "Avatar supervisor only: list output artifacts across agents, projects, jobs, runs, or plans.")
    public String avatarListOutputs(
        @ToolParam(required = false, description = "Optional agent id filter.")
        String agentId,
        @ToolParam(required = false, description = "Optional job id filter.")
        String jobId,
        @ToolParam(required = false, description = "Optional project id filter.")
        String projectId,
        @ToolParam(required = false, description = "Optional workspace id filter.")
        String workspaceId,
        @ToolParam(required = false, description = "Optional run id filter.")
        String runId,
        @ToolParam(required = false, description = "Optional task/plan id filter.")
        String planId,
        @ToolParam(required = false, description = "Optional run type filter such as TASK_RUN or JOB_RUN.")
        String runType,
        @ToolParam(required = false, description = "Optional artifact type filter.")
        String artifactType,
        @ToolParam(required = false, description = "Maximum artifacts to return, bounded to 1..200.")
        Integer limit
    ) {
        return json(service.listOutputs(agentId, jobId, projectId, workspaceId, runId, planId, runType, artifactType, limit));
    }

    @Tool(name = "avatar_read_output", description = "Avatar supervisor only: read bounded UTF-8 content from one output artifact.")
    public String avatarReadOutput(
        @ToolParam(description = "Output artifact id to read.")
        String artifactId,
        @ToolParam(required = false, description = "Maximum bytes to read. Defaults to 65536 and is capped by the server.")
        Long maxBytes
    ) throws Exception {
        return json(service.readOutput(artifactId, maxBytes));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Avatar assistant tool result", exception);
        }
    }
}
