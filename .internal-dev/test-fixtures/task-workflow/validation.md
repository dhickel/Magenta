# Task Workflow Validation Fixture

## Setup

Start Magenta against an isolated database:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-task-workflow-fixture.sqlite'
```

## Assertions

- `/tasks` renders the task editor, typed input/output controls, and generated run form.
- `/workflows` renders the workflow editor, task picker surface, binding textarea, warning area, and run log.
- A standalone run of Task 1 emits terminal SSE event `completed`.
- The Task 1 run stores output key `research_notes`.
- A three-step workflow run emits terminal SSE event `completed`.
- The workflow run stores three step runs.
- Step 2 input values include `research_notes` from Step 1.
- Step 3 input values include `structured_summary` from Step 2.
- Final workflow outputs include `final_report`.

The fixture uses deterministic fallback values, so validation should assert persisted structure and keys rather than depending on live web results.
