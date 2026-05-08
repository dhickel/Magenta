# Scope

Alpha milestone simplification, smell, and refactor-target review. This pass intentionally identifies more targets than should be fixed immediately. Ratings:

- `P0`: must fix before alpha exposure
- `P1`: should fix soon, strong alpha-hardening candidate
- `P2`: worthwhile cleanup
- `P3`: likely defer or skip unless the feature becomes alpha-facing

# Findings

| Rating | Target | Evidence | Recommendation |
|---|---|---|---|
| P0 | Server-generated frontend has unsafe raw HTML/JS injection points. | `FrontendController.java:1211`, `:1214`, `:1217`, `:1273`, `:1274`, `:1276` interpolate task/workflow names, ids, descriptions, and binding JSON into `innerHTML` and attributes. | Move task/workflow UI to static JS using DOM APIs and `textContent`, or escape every interpolated field. Treat as alpha blocker if UI is exposed. |
| P1 | Runtime settings are not wired into `ContextManagementAdvisor`. | `ChatBeanConfig.java:31-49` manually constructs the advisor with the constructor that passes `null` for `RuntimeSettingsService`, despite the advisor supporting it at `ContextManagementAdvisor.java:82-101`. | Inject `RuntimeSettingsService` into the bean method and add a test that compaction model/context buffer changes affect context management. |
| P1 | `ChatService` is too broad. | `ChatService.java` spans chat, stream locking, plan lifecycle, task execution, tool loop, audit, title jobs, model/tool policy, and retry recovery. | Extract focused collaborators around tool-loop execution, chat request resolution, plan/task execution bridges, and session/history operations. |
| P1 | Plan/task mutation uses long-record copy patterns. | `ExecutionPlan` is a 24-field record; `PlanService` and `TaskService` repeatedly reconstruct large records, e.g. `PlanService.java:716`, `PlanService.java:809`, `TaskService.java:442`. | Add local withers/builders or smaller nested aggregates so changes are less error-prone. |
| P1 | Schema ownership is fragmented. | `schema.sql` defines some tables while repositories create/alter many others. `schema.sql` omits `planning_model`; repositories add runtime/orchestration tables. | Decide on central schema/migrations or repository bootstrapping. For alpha, centralize enough that clean installs and upgraded installs are testable. |
| P1 | SSE plumbing is duplicated and inconsistent. | `ChatController.java:90`, `TaskController.java:135`, `WorkflowController.java:95`, `AgentOrchestrationController.java:141` all hand-roll emitter/subscription/error logic. | Extract a small SSE helper or return `Flux<ServerSentEvent<?>>` where practical. Standardize timeout and cancellation behavior. |
| P1 | Shell tool accepts raw command strings and hand-parses quoting. | `AgentShellTools.java:23` exposes `String command`; `AgentShellToolService.java:125-178` implements a shell-like parser. | Replace with `executable` plus `args` list while keeping allowlist and cwd confinement. |
| P1 | Workflows are publicly exposed but still prototype-shaped. | `WorkflowService.java:51-54` enforces two or three linear steps; UI uses raw JSON bindings in `FrontendController.java:1274`; final outputs are always last step outputs at `WorkflowService.java:157-160`. | Either productize the workflow UX/API semantics or hide the workflow UI/API behind a dev/experimental flag for alpha. |
| P2 | Dead command compatibility code remains. | `/commands` supports `new` and `plan`, but `handleSwitch`, `handleClear`, and unused argument helpers remain in `ChatController.java:445-482` and `:559-591`. | Remove dead handlers or restore route support intentionally. |
| P2 | Unused core utilities appear stale. | `src/main/java/io/mindspice/magenta2/core/util/Option.java` and `src/main/java/io/mindspice/magenta2/core/DataService.java` appear definition-only in current source searches. | Delete if truly unused, or replace with `Optional`/current data-root services. |
| P2 | Stream event DTO is a nullable union. | `ChatStreamEvent` carries mutually exclusive nullable fields via factories. | Use typed event payload records or add an explicit event type with event-specific payload. |
| P2 | Audit writes duplicate sequence logic and silently swallow errors. | `AuditRepository.nextSequence` plus per-event insert methods; failures are debug-only. | Consider a DB-generated sequence per conversation or unique constraint plus retry; raise log level for audit failures if audit is alpha-relevant. |
| P3 | Schedule/event reaction templates are untyped map DSLs. | `ScheduleService` and `OrchestrationEventService` convert untyped templates into assignment requests. | Defer unless schedules/reactions are alpha-facing; otherwise keep hidden and avoid expanding generic map DSL behavior. |
| P3 | Workflow v1 restriction may be acceptable if explicitly documented. | Two/three linear steps are simple and easy to reason about. | Keep if alpha goal is narrow, but label as v1 in API/UI docs or hide until productized. |

# Risk Assessment

The largest simplification risks are not cosmetic. The current raw frontend can create injection problems; runtime settings can mislead users if context management ignores them; schema drift can make clean installs differ from upgraded installs; `ChatService` breadth raises the cost of every bug fix.

The codebase is still small enough to correct these without a major rewrite. The most valuable refactors are the ones that reduce behavioral ambiguity: settings wiring, schema policy, SSE standardization, and shell/web tool contracts.

# Recommendations

1. Fix P0 frontend interpolation before alpha exposure.
2. Wire `RuntimeSettingsService` into `ContextManagementAdvisor` and test it.
3. Standardize schema ownership and add clean-database startup tests.
4. Extract SSE support before further stream endpoint work.
5. Split `ChatService` only along proven seams; avoid a broad architectural rewrite.
6. Convert shell tool input from raw command string to structured executable/args.
7. Make an explicit alpha decision for workflows, schedules, and event reactions: productize, hide, or document as experimental.

# Follow-ups

- Add a small refactor plan for `ChatService` before editing it.
- Add frontend escaping tests or Playwright checks for task/workflow names containing HTML special characters.
- Add package dependency checks after moving `PlanMode` or `AgentJobRepository`.
- Update package guides when ownership changes.
