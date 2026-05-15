# Final Alpha Docker Validation

## Scope
Finalized the multi-day Docker-backed alpha validation loop after correcting the model host to `192.168.1.112`, clearing stale Playwright Chrome state, and rerunning live model-backed task execution through the managed Podman container.

## Findings
- `mvn test` previously passed with 430 tests, 0 failures, 0 errors after the Docker context/output hardening changes.
- Spring Boot startup passed with Docker disabled and with Docker enabled against `unix:///run/user/1000/podman/podman.sock`.
- `http://192.168.1.112:11434/api/tags` returned Ollama model tags.
- Qwen-backed Docker task run `e24ef1be-2286-4b73-9759-c46f69166e77` completed through assignment `ec0d0b45-50f8-496b-8a79-82eaa126f453`.
- The run registered `hello.txt` and `result.json` artifacts under agent `f90e3d3a-e1e1-4cfe-a0b1-a4b428fea496` with run type `TASK_RUN`.
- Host readback returned `Alpha Docker final validation` and `{"ok": true}` from the agent run output directory.
- Output download endpoints returned HTTP 200 and the expected file bodies.
- Browser validation with isolated repo-local Playwright loaded `/dashboard`, `/agents`, `/outputs`, `/plans`, `/workflows`, and `/settings`; `/outputs` exposed the run evidence.

## Risk Assessment
The original alpha blockers around Docker-backed task output routing, workflow approval rejection, workflow task no-op behavior, and output content access are resolved for the validated surfaces. Remaining risk is limited to the separately logged Docker stop status mismatch, where the stop fragment can report stale container status.

## Recommendations
- Archive the four fixed DEFECT reports from the Docker-backed alpha loop.
- Keep `2026-05-14-docker-stop-status-mismatch` open for lifecycle-control follow-up.
- Keep non-DeepSeek model endpoints pointed at `192.168.1.112:11434`.

## Follow-ups
- Add a focused Docker lifecycle regression for stop status consistency.
- Consider exposing the current run output path as `MAGENTA_OUTPUT_DIR` if model behavior remains sensitive to prompt wording.
