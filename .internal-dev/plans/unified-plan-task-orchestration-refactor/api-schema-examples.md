# API and Schema Examples

## Purpose

This file gives concrete wire and schema examples for implementation agents. Use it to avoid inventing incompatible shapes while implementing the phase plans.

## Unified Plan/Task Definition Example

```json
{
  "kind": "TASK_TEMPLATE",
  "status": "APPROVED",
  "title": "Summarize Research Notes",
  "summary": "Condense a note file into an executive summary.",
  "goal": "Produce a concise user-facing summary from provided notes.",
  "deliverables": [
    "A validated summary suitable for the user to read",
    "Evidence that the source file was read"
  ],
  "inputs": [
    {
      "name": "source_notes",
      "type": "file_path",
      "array": false,
      "description": "Path to the notes file inside the mounted workspace.",
      "required": true
    },
    {
      "name": "focus",
      "type": "string",
      "array": true,
      "description": "Optional focus areas the summary should emphasize.",
      "required": false
    }
  ],
  "outputs": [
    {
      "name": "summary_message",
      "type": "user_message",
      "array": false,
      "description": "The complete summary message to hand to the user."
    },
    {
      "name": "summary_file",
      "type": "file_path",
      "array": false,
      "description": "Markdown file containing the same summary."
    }
  ],
  "steps": [
    {"order": 1, "text": "Inspect the source notes file."},
    {"order": 2, "text": "Draft the summary and validate it against the source."},
    {"order": 3, "text": "Write the final summary to the output directory and complete the run."}
  ],
  "validationCriteria": [
    "The summary cites the major decisions or findings from the source file.",
    "The summary is written to both message and file outputs."
  ],
  "promptProfile": "technical_writing"
}
```

## No-Input Runtime Prompt Fragment

When a definition has no inputs, inject this idea into the execution prompt:

```text
No runtime inputs are expected for this plan/task. Begin task execution from the saved goal, deliverables, steps, assumptions, and validation criteria. Do not ask for missing inputs.
```

## No-Output Runtime Prompt Fragment

When a definition has no outputs, inject this idea into the execution prompt:

```text
No structured outputs are expected for this plan/task. Complete only after validating the deliverables and validation criteria. Do not fabricate output values.
```

## Completion Tool Payload Example

```json
{
  "outputValues": {
    "summary_message": "## Summary\n\nThe notes identify...",
    "summary_file": "/workspace/output/summary.md"
  },
  "deliverableEvidence": [
    "Read /workspace/work/source-notes.md",
    "Wrote /workspace/output/summary.md"
  ],
  "finalMessage": "Summary completed and written to the output directory."
}
```

The server must copy or write each output into the run output directory and persist artifact metadata before marking the run complete.

## Workflow Definition Example

```json
{
  "title": "Review Then Implement",
  "summary": "Produce a plan, wait for approval, then run implementation.",
  "nodes": [
    {
      "key": "draft_plan",
      "type": "TASK",
      "planId": "plan-drafting-task",
      "inputBindings": []
    },
    {
      "key": "approve_plan",
      "type": "USER_APPROVAL",
      "messageTemplate": "Approve implementation plan from draft_plan?",
      "resumePolicy": "APPROVE_CONTINUE_REJECT_NEEDS_REVIEW"
    },
    {
      "key": "implement",
      "type": "TASK",
      "planId": "implementation-task",
      "inputBindings": [
        {
          "inputName": "approved_plan",
          "sourceNodeKey": "draft_plan",
          "sourceOutputName": "plan_file"
        }
      ]
    }
  ]
}
```

## Schema Sketch

Use exact SQL names only after implementation review, but keep this shape:

```sql
create table plan_definitions (
    id text primary key,
    kind text not null,
    status text not null,
    title text not null,
    summary text,
    goal text,
    notes text,
    deliverables_json text not null,
    inputs_json text not null,
    outputs_json text not null,
    assumptions_json text not null,
    steps_json text not null,
    validation_criteria_json text not null,
    prompt_profile text,
    planning_model text,
    execution_model text,
    settings_override_json text,
    created_at text not null,
    updated_at text not null
);

create table plan_runs (
    id text primary key,
    plan_id text not null,
    status text not null,
    input_values_json text not null,
    output_values_json text not null,
    plan_snapshot_json text not null,
    temp_workspace_path text,
    output_directory_path text,
    execution_evidence_json text not null,
    validation_feedback_json text not null,
    final_message text,
    error_text text,
    created_at text not null,
    updated_at text not null,
    started_at text,
    completed_at text
);
```

## API Error Examples

- Missing input: `400 Missing required input(s): source_notes`
- Missing output: `400 Missing required output(s): summary_message, summary_file`
- Unknown field type: `400 Unknown plan field type: text`
- Docker unavailable: `503 Docker runtime is required but unavailable: <daemon error>`
- Workspace lease conflict: `409 Workspace is already leased for write access`
- Approval stale response: `409 Waiting message is no longer active`

