# Alpha Gate Review v1

## Scope

This is a consolidated alpha-gate review for Magenta2. It combines the current 2026-05-27 dual-agent gate reviews with the closest recent larger alpha source set: the archived `public-alpha-quality-review` package. It also uses the 2026-05-25 alpha quality review for the two still-active GitHub-linked defects and the older 2026-05-08 milestone review as a recurring-theme precursor.

The purpose is to merge overlapping findings, preserve the newer current findings, and retain older alpha lessons without treating archived findings as current proof. This artifact is a review and triage aid, not a remediation plan. It includes mitigation context and prioritized recommendations, but it does not define implementation steps or phase work.

Required workflow context read before consolidation: `AGENTS.md`, `.internal-dev/AGENTS.md`, and `.internal-dev/specifications/AGENTS.md`.

## Source Reports Used

- `.internal-dev/reviews/2026-05-27-alpha-quality-review.md`: primary source for current code-quality, cohesion, maintainability, UI-boundary, schema-migration, optional-wiring, and test-quality findings.
- `.internal-dev/reviews/2026-05-27-alpha-bug-contract-review.md`: primary source for current bug and contract findings across SSE cleanup, interrupt semantics, assignment run names, workflow route semantics, delegation execution, and pending-message ordering.
- `.internal-dev/reviews/2026-05-25-alpha-quality-review.md`: source for the still-active `CANCEL_REQUESTED` late overwrite and conversation queue executor rejection defects, plus the explicitly remediated governance drift item.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/final-readiness-review.md`: recent large alpha source used for historical release-gate themes, risk clusters, validation-confidence concerns, and older blocker categories.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/bug-ledger.md`: recent large alpha source used for the archived blocker/remediation inventory and to identify older findings that should be treated as stale unless revalidated.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/remediation-handoff.md`: recent large alpha source used for historical risk grouping and validation expectations only; its phase structure is not reused as a plan here.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-api-web.md`: domain source for archived web/API, workflow XSS, direct-run, and route-boundary concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-chat-plan-task.md`: domain source for archived chat, plan, task, direct execution, transcript, and SSE event concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-frontend-static.md`: domain source for archived frontend/static, HTMX, JS island, stale target, and workflow graph concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-orchestration-runtime.md`: domain source for archived assignment lifecycle, runtime lease, schedule/reaction, and filesystem allocation concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-persistence-schema.md`: domain source for archived schema drift, warm-DB, workspace lease, inbox ownership, and orphan-schema concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-test-harness.md`: domain source for archived public REST/SSE coverage, Spring web coverage, SQLite fixture, and Playwright harness gaps.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-workflow.md`: domain source for archived workflow authoring/runtime, empty workflow, direct-run, JS island, and XSS concerns.
- `.internal-dev/reviews/.archive/public-alpha-quality-review/domain-workspaces-tools-outputs.md`: domain source for archived workspace, shell/file/web tool confinement, output materialization, and stale runtime/UI concerns.
- `.internal-dev/reviews/.archive/2026-05-08-alpha-consolidated-milestone-review.md`: older precursor source for recurring architecture, controller/service cohesion, schema ownership, streaming semantics, security/tooling, and validation themes.
- `.internal-dev/changelogs/2026-05-18-public-alpha-remediation-final-validation.md`: staleness context only. It records that the archived public-alpha remediation suite later passed integrated validation, so archived findings are not treated as current unless a 2026-05-27 or 2026-05-25 source re-found them.
- `.internal-dev/changelogs/2026-05-18-public-alpha-remediation-plan-suite.md`: staleness context only. It records that the archived large review became a remediation suite, so its findings are useful historical context but need current revalidation.

## Executive Assessment

Magenta2 is not cleanly through the alpha gate on the current evidence. The blocker-class risks are concentrated in runtime lifecycle correctness, workflow execution semantics, assignment/run contract consistency, and queue/cancellation state integrity. These are not cosmetic: they can strand active turns, make interrupt controls misleading, fabricate completed workflow evidence, lose ordering guarantees, or let late runtime writes overwrite user cancellation intent.

The quality risks are different but still important for alpha reliability. `OrchestrationController` and `ChatService` are now broad coordination surfaces whose size and optional dependency patterns make narrow fixes harder to reason about. Several controller and repository boundaries still mix rendering, prompt construction, schema migration, compatibility behavior, and runtime policy in ways that reduce test signal and make regressions more likely.

The archived `public-alpha-quality-review` package is highly relevant because it shows the same broad release-gate categories: public web/API boundaries, submit-to-agent execution contracts, workflow correctness, schema/runtime persistence, tool/security confinement, and test harness coverage. However, it is archived and later remediation validation exists, so those older findings are carried forward only as historical context unless current 2026-05-27 or 2026-05-25 evidence re-established the issue.

Current blocker-class themes: SSE/active-turn cleanup, `CANCEL_REQUESTED` late overwrite, conversation queue rejection recovery, `PASS_THROUGH` workflow semantics, `DELEGATION` fake completion, and missing `runDisplayName` on task-run entry points. Hardening/refactor themes: controller/service cohesion, optional wiring, raw maintained UI fragments, repository migration observability, Avatar failure masking, and Spring/web/SSE/concurrency test coverage.

## Active Findings

### Runtime Lifecycle And Streaming

#### Finding: SSE error callbacks can leave active chat or plan execution registered

- Issue: Stream terminal cleanup is split so `onError` records a transport disconnect but does not dispose the subscription guard or complete the active turn. Cleanup is currently tied to `onCompletion` conditions.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 1; `2026-05-08-alpha-consolidated-milestone-review.md` streaming state semantics; archived `domain-chat-plan-task.md` plan-run SSE concerns as historical context only.
- Targets: `ChatController`, `SseStreamLifecycle`, `ActiveTurnRegistry`, chat SSE streams, saved-plan execution streams.
- Evidence Summary: The current review reports that `domainCleanup` disposes the guard and completes active turns, `onCompletion` conditionally calls cleanup, and `onError` only records the disconnect. Active plan execution conflicts remain until registry completion removes them.
- Impact: A client disconnect or stream transport error can leave an active turn registered, block later plan execution with false active-execution conflicts, keep model work running after the client is gone, and make final state depend on servlet callback ordering.
- Severity: high
- Confidence/Staleness: medium confidence as a current 2026-05-27 finding. Archived SSE concerns are historical context and are not proof of this exact defect.
- Related Issue: https://github.com/dhickel/Magenta/issues/14
- Mitigation Context: Treat terminal lifecycle behavior as a release-gate contract. Any remediation should preserve explicit distinctions between disconnect, timeout, model failure, user cancel, and domain completion.

#### Finding: `CANCEL_REQUESTED` assignments can be overwritten by late lease-owner writes

- Issue: Assignment state transitions can still accept late completion, failure, waiting, or checkpoint-like writes after cancellation has been requested.
- Source Findings: `2026-05-25-alpha-quality-review.md` Finding 1; archived `domain-orchestration-runtime.md` cancellation notes as historical context only.
- Targets: `OrchestrationRuntimeRepository.saveAssignmentIfLeaseOwner`, `OrchestrationRunnerService`, assignment lifecycle persistence.
- Evidence Summary: The 2026-05-25 review reports that `saveAssignmentIfLeaseOwner` accepts rows in `RUNNING` or `CANCEL_REQUESTED`, while runner state writes route through that path.
- Impact: User cancellation can be defeated by a late lease-owner write, leaving queue/history state inconsistent with the control action and weakening cancellation as an alpha safety boundary.
- Severity: high
- Confidence/Staleness: high confidence as a 2026-05-25 current-prior finding with an open related issue.
- Related Issue: https://github.com/dhickel/Magenta/issues/13
- Mitigation Context: Cancellation must be a durable state boundary, not only an advisory runtime flag. Triage should focus on preserving legitimate force-interrupt/lease-owner protections while preventing late overwrite after cancel request.

### Chat Turn Coordination And Interrupt Semantics

#### Finding: Plain streaming turns advertise an interrupt token but cannot accept interrupts

- Issue: Plain streaming chat registers an active turn and sends `turnId`/`interruptToken`, but the plain model path does not set an interrupt-accepting active-turn phase.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 2; `2026-05-08-alpha-consolidated-milestone-review.md` streaming state semantics as older context.
- Targets: `ChatService`, `ActiveTurnRegistry`, `ChatController`, `/api/chat/turns/{turnId}/interrupt`.
- Evidence Summary: The current review reports that stream start payloads expose turn id and token, `ActiveTurn.acceptsInterrupts` defaults false until `phase(...)` changes it, the tool-capable path sets phases, and the plain stream path does not receive or update the active turn.
- Impact: Clients receive a capability that appears valid but normal non-tool streaming calls cannot honor. Browser/API behavior becomes misleading and interrupt tests can pass on tool turns while failing for the common path.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 finding.
- Related Issue: https://github.com/dhickel/Magenta/issues/15
- Mitigation Context: The product contract should clearly distinguish interruptible and non-interruptible phases, either by making plain streaming interruptible or by not advertising interrupt semantics where they cannot work.

#### Finding: Conversation queue executor rejection can poison a queued conversation

- Issue: A queued turn can be marked submitted before executor submission succeeds, so executor rejection can leave the queue head uncleared and later turns stuck.
- Source Findings: `2026-05-25-alpha-quality-review.md` Finding 2; archived test-harness coverage gaps as historical context.
- Targets: `ConversationTurnCoordinator`, `MagentaWorkExecutor`, chat pending/queued-turn processing.
- Evidence Summary: The 2026-05-25 review reports that the coordinator marks a queued turn as submitted before calling executor submission, and rejection does not complete or remove the head.
- Impact: Under executor pressure or shutdown-like rejection conditions, a conversation can stop draining. This is an alpha-relevant reliability issue because the failure mode is a stuck user-facing chat queue.
- Severity: high
- Confidence/Staleness: high confidence as a 2026-05-25 current-prior finding with an open related issue.
- Related Issue: https://github.com/dhickel/Magenta/issues/12
- Mitigation Context: Queue state should reflect actual accepted execution, not attempted dispatch. Validation should include rejection/saturation behavior rather than only successful executor paths.

#### Finding: `ChatService` is a high-risk coordinator with optional dependency drift

- Issue: `ChatService` mixes chat, planning, task execution, tool-loop, prompt assembly, auditing, context maintenance, runtime settings, pending messages, and skill activation concerns behind a large optional dependency graph.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 2; `2026-05-08-alpha-consolidated-milestone-review.md` architecture risk; archived public-alpha route and chat-plan-task reports as historical context.
- Targets: `ChatService`, chat service package, model/tool turn execution, planning mode, task execution, prompt assembly, skills integration.
- Evidence Summary: The current quality review reports a 2,634-line service, broad dependencies, many `@Autowired(required = false)` collaborators, direct construction of helper collaborators inside the constructor, and continued ownership of plan/task/tool execution flow in the same class.
- Impact: Missing beans can become runtime branches instead of wiring failures, tests can instantiate unrealistic partial services, and a change in one chat-adjacent feature can affect unrelated turn state.
- Severity: high
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding. Older reports reinforce that this is a recurring architecture theme.
- Related Issue: none identified in the source reports.
- Mitigation Context: Treat the service as a risk concentration point. Any future changes should classify required runtime collaborators separately from accepted degraded integrations before broad refactoring.

### Task, Assignment, And Run Contracts

#### Finding: Some `TASK_RUN` assignment entry points bypass the required run display name

- Issue: The active API contract requires non-job task/workflow submissions to include a user-visible `runDisplayName`, but multiple task-run assignment paths still create requests with `runDisplayName` null.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 3; archived public-alpha execution-contract findings as historical context only.
- Targets: `PlanController`, `AgentOrchestrationController`, `AssignmentService`, `AssignmentTemplateParser`, saved-plan/task submission APIs, generic agent assignment creation.
- Evidence Summary: The current review reports that task and workflow controllers enforce `requireRunDisplayName`, while plan submit/run stream paths and the generic agent assignment endpoint omit the value. The lower-level validator checks ids but not the non-job display-name contract.
- Impact: Queue, history, and output attribution can receive blank or inconsistent user-visible run names even though sibling routes reject the same missing field.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 finding. Older submit-to-agent/direct-run concerns are historical context and should not be treated as current without revalidation.
- Related Issue: https://github.com/dhickel/Magenta/issues/16
- Mitigation Context: The assignment boundary should enforce one non-job run naming contract regardless of whether the request enters through plans, tasks, workflows, or generic agent assignment routes.

#### Finding: `PlanController` still owns domain conversion and chat prompt construction

- Issue: Plan routes build domain objects and model-facing prompt text in the controller layer instead of delegating this use-case behavior to services.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 4; current `runDisplayName` bug adds a related request-boundary symptom.
- Targets: `PlanController`, plan API, saved plan chat prompt generation, DTO/domain conversion.
- Evidence Summary: The current quality review reports controller-local `toDomain(...)` conversion and a `/chat-prompt` route that assembles plan fields, outputs, steps, validation criteria, assumptions, notes, and instructions directly in the controller.
- Impact: Plan prompt behavior and domain conversion can drift from chat/service flows. Route-specific behavior becomes harder to share, test, and validate against contract changes.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding.
- Related Issue: none identified in the source reports.
- Mitigation Context: Keep later triage focused on the prompt/conversion boundary. This finding does not justify a broad plan API rewrite by itself.

#### Finding: `PlanCompletionService` treats validator availability as optional runtime behavior

- Issue: Saved-plan completion validation depends on optional validator/config beans and degrades into a user-facing validation failure if those beans are absent.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 3; older alpha reviews emphasize validation and stream completion semantics as release-gate concerns.
- Targets: `PlanCompletionService`, `PlanCompletionValidator`, `AiConfig`, saved plan completion validation.
- Evidence Summary: The current review reports optional injection for `PlanCompletionValidator` and `AiConfig`, followed by a runtime failed validation result with "Validator model is not configured." when either is missing.
- Impact: A quality gate can fail at execution time due to wiring/configuration absence, making it harder to distinguish incomplete work from unavailable validator infrastructure.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding.
- Related Issue: none identified in the source reports.
- Mitigation Context: Decide whether validator absence is an explicit degraded mode or a startup/configuration failure. The important contract is that users and operators can tell which case occurred.

### Workflow Execution Semantics

#### Finding: `PASS_THROUGH` workflow routes are validated and executed as single-port mappings

- Issue: The documented `PASS_THROUGH` route contract forwards all source node outputs as a map and does not use source/target ports, but validation and runtime behavior require and use one named port pair.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 4; archived workflow builder/runtime concerns as historical context only.
- Targets: `WorkflowValidator`, `WorkflowRunner`, `WorkflowRouteType`, workflow v2 graph validation and execution.
- Evidence Summary: The current review reports that route knowledge and enum comments describe pass-through as forwarding the source output unchanged, while the validator requires ports for all non-control data routes and the runner copies one source port to one target port.
- Impact: A workflow matching the documented pass-through shape can be rejected. If users add ports to satisfy validation, runtime semantics still collapse pass-through to one field, causing incomplete or incorrectly shaped downstream inputs.
- Severity: high
- Confidence/Staleness: high confidence as a current 2026-05-27 finding.
- Related Issue: https://github.com/dhickel/Magenta/issues/17
- Mitigation Context: This is a contract mismatch between the route model, validator, and executor. Triage should preserve the documented graph shape or deliberately revise the contract.

#### Finding: `DELEGATION` workflow nodes can fabricate completed child plan runs

- Issue: Delegation nodes can start a child `PlanRun` and immediately complete it with empty outputs and a fixed message, without executing chat-backed task work or requiring `task_complete`.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 5; archived workflow empty/no-op and task execution placeholder concerns as historical context only.
- Targets: `WorkflowRunner`, `OrchestrationController`, `PlanService`, workflow v2 runs containing `DELEGATION` nodes.
- Evidence Summary: The current review reports that the workflow UI exposes the `DELEGATION` enum value, while the runner handles it by starting a child run and immediately completing it with empty outputs and "Delegated run completed." Actual chat-backed execution is separate.
- Impact: Workflow history can show completed delegated work when no delegated work happened. This undermines workflow completion evidence and recreates a fake-output class of alpha defect.
- Severity: high
- Confidence/Staleness: medium confidence as a current 2026-05-27 finding. The active exposure of `DELEGATION` should be verified during triage, but the source reports it is UI-reachable.
- Related Issue: https://github.com/dhickel/Magenta/issues/18
- Mitigation Context: Decide whether `DELEGATION` is supported for alpha. If it is visible, completion evidence must mean real delegated execution rather than placeholder success.

### Persistence, Schema, And Ordering

#### Finding: Pending chat FIFO ordering can race under concurrent enqueue

- Issue: Pending message enqueue calculates `max(message_order) + 1` without a uniqueness constraint or per-conversation write lock, so concurrent same-conversation requests can persist duplicate order keys.
- Source Findings: `2026-05-27-alpha-bug-contract-review.md` Finding 6; archived test/schema concerns as historical context.
- Targets: `ChatPendingMessageRepository`, `schema.sql`, browser chat mid-turn queue and background drain behavior.
- Evidence Summary: The current review reports that enqueue selects the next order and inserts it, claims order by `message_order`, and the schema indexes but does not enforce uniqueness on the conversation/order pair.
- Impact: FIFO drain order can become nondeterministic for duplicate order rows. Single-threaded green tests do not prove browser/API ordering under quick duplicate submissions or multi-tab concurrency.
- Severity: medium
- Confidence/Staleness: medium confidence as a current 2026-05-27 finding. It requires a concurrency probe to confirm practical frequency.
- Related Issue: https://github.com/dhickel/Magenta/issues/19
- Mitigation Context: This is a data-ordering contract risk. Validation should exercise concurrent same-conversation enqueue rather than infer safety from SQLite serialization.

#### Finding: Repository constructors still mix schema creation, warm migration, compatibility rewrites, and runtime access

- Issue: Repository startup paths own normal data access plus table creation, compatibility migration, table rewrites, and ignored migration exceptions.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 6; archived `domain-persistence-schema.md` and final readiness schema findings as historical context.
- Targets: `WorkflowRepository`, `WorkspaceRepository`, workflow persistence, workspace persistence, schema initialization, warm migration behavior.
- Evidence Summary: The current quality review reports constructor-time table creation and indexes in `WorkflowRepository`, many ignored `alter table` exceptions, and `WorkspaceRepository` compatibility migration/table rewrites including workspace-related table recreation.
- Impact: Schema evolution is hard to audit because startup side effects, compatibility paths, and CRUD live together. Ignored exceptions reduce observability, and table-rewrite logic makes repository tests carry migration risk.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding. Specific older lease-drop/schema-drift defects are treated as stale unless revalidated.
- Related Issue: none identified in the source reports.
- Mitigation Context: Inventory and observability matter before deletion. The active finding is not that every compatibility path is wrong, but that migration behavior is too hard to reason about for alpha data safety.

### Web/API And UI Boundary Quality

#### Finding: Work Area explorer fragments are maintained as raw HTML strings instead of SimplyPages structures

- Issue: A maintained Work Area explorer surface composes shell, table, inspector, forms, viewer tabs, modal, breadcrumbs, buttons, and tags through multiline strings and `StringBuilder` templates.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 5; archived frontend/static and workspaces/tools/outputs reports as historical context.
- Targets: `WorkAreaExplorerFragments`, Avatar Work Area file browser fragments, HTMX interactions.
- Evidence Summary: The current quality review reports raw string templates across major Work Area explorer surface areas, coupling HTML, escaping, URL construction, HTMX targets, and action routing in one utility.
- Impact: Visual changes, selector-level tests, reuse across workspace/project browsers, and SimplyPages consistency become brittle. This is especially relevant because Work Areas are an alpha operational surface.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding.
- Related Issue: none identified in the source reports.
- Mitigation Context: Raw HTML can remain an escape hatch for isolated fragments, but this surface has grown beyond a narrow exceptional case and should be evaluated against SimplyPages/HTMX reuse patterns.

#### Finding: Avatar dashboard data assembly can hide runtime failures as empty UI state

- Issue: Avatar dashboard data paths use optional service providers and `safeList(...)` wrappers that catch runtime exceptions and return empty lists without surfacing failure state.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 8; archived operational UI concerns as historical context.
- Targets: `AvatarDashboardController`, Avatar dashboard, assignment/Work Area/output/job/chat summaries.
- Evidence Summary: The current review reports optional providers for assignment and Work Area services, broad `safeList(...)` wrapping of dashboard data queries, and a helper that catches `RuntimeException` and returns `List.of()`.
- Impact: Broken dependencies and legitimate empty states can look identical in the operational UI. Browser validation becomes ambiguous because an empty panel may mean no data or a swallowed backend failure.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding.
- Related Issue: none identified in the source reports.
- Mitigation Context: Partial local runs may need explicit degraded states, but runtime service failures should be observable in alpha-facing operational surfaces.

### Service/Controller Cohesion And Maintainability

#### Finding: `OrchestrationController` is an oversized mixed-responsibility controller

- Issue: One controller owns dashboard fragments, plan/task editor UI, job submission UI, agent detail/profile/queue/history/exec surfaces, settings, and rendering/formatting helpers across many injected services.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 1; `2026-05-08-alpha-consolidated-milestone-review.md` controller architecture risk; archived public-alpha web/API reports as historical context.
- Targets: `OrchestrationController`, operational dashboard, agents, jobs, plans, workflows, settings, related UI fragments.
- Evidence Summary: The current quality review reports an 8,085-line controller with a broad constructor dependency set and many unrelated route/rendering families in one class.
- Impact: A fix to one alpha surface must be reviewed inside a class that owns many unrelated surfaces. This raises regression risk and encourages controller-local policy/rendering rather than service-owned read models and reusable fragments.
- Severity: high
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding. Older reports show this has been a recurring architecture concern.
- Related Issue: none identified in the source reports.
- Mitigation Context: Treat this as a responsibility-boundary triage target. The review does not recommend a broad rewrite; it recommends avoiding further concentration and choosing concrete route-family slices when work touches this controller.

#### Finding: Boundary tests rely too heavily on direct construction and large stubs

- Issue: Key tests for controller and chat behavior directly construct large classes with many stubs/null collaborators instead of exercising Spring route, wiring, exception, and public payload boundaries.
- Source Findings: `2026-05-27-alpha-quality-review.md` Finding 7; archived `domain-test-harness.md` public REST/SSE and Spring web coverage gaps.
- Targets: `OrchestrationControllerTest`, `ChatServiceTest`, public web/API route contracts, chat service wiring-sensitive behavior.
- Evidence Summary: The current quality review reports repeated direct construction of `OrchestrationController` with local stub factories and many local stub classes, plus `ChatService` construction with long null-heavy argument lists. The archived test-harness review found broad public REST/SSE and Spring web coverage gaps.
- Impact: Tests can pass while route binding, Spring wiring, optional dependency behavior, exception mapping, SSE behavior, and real web context interactions drift.
- Severity: medium
- Confidence/Staleness: high confidence as a current 2026-05-27 quality finding. The archived coverage gaps are historical but align with the current direct-construction finding.
- Related Issue: none identified in the source reports.
- Mitigation Context: Keep lower-level unit tests where they isolate domain logic, but route, SSE, and wiring-sensitive contracts need closer-to-runtime coverage.

## Consolidated Risk Assessment

The strongest active alpha risk is mismatch between visible API/UI state and backend truth. SSE starts can advertise turns that may not clean up, plain streaming can advertise interrupts it cannot accept, delegation can mark child runs complete without execution, and task-run submissions can persist without the required display name.

The second risk is lifecycle and queue correctness under non-happy paths. `CANCEL_REQUESTED` overwrite and executor rejection poisoning both sit on control-flow edges that users are likely to hit when work is cancelled, overloaded, or interrupted. Those conditions are exactly where an assistant system must remain understandable.

The third risk is architectural concentration. `OrchestrationController` and `ChatService` are not blocker-class defects by themselves, but they amplify the cost and risk of fixing blocker-class defects. Optional wiring and direct-construction tests make it easier for runtime-only behavior to escape review.

The fourth risk is validation confidence. Current findings repeatedly require route-level, SSE, concurrent, or Spring-context validation. Unit-only coverage cannot prove the affected contracts.

Archived public-alpha findings remain useful as a checklist of historically fragile surfaces: security/control, tool confinement, workflow authoring, schema migration, workspace leases, output materialization, mobile UI, and route coverage. They should not be reopened as active defects without current evidence, but they should be revalidated before public exposure because the same classes of risk continue to appear in the current reports.

## Recommendations

1. Treat the current GitHub-linked lifecycle and workflow issues as alpha-gate blockers or near-blockers: SSE cleanup (#14), `CANCEL_REQUESTED` overwrite (#13), conversation queue rejection (#12), `PASS_THROUGH` semantics (#17), and `DELEGATION` fake completion (#18).

2. Treat `runDisplayName` (#16), plain-stream interrupt semantics (#15), and pending FIFO ordering (#19) as high-priority contract hardening. They may be less catastrophic than fake completion or leaked active turns, but they directly affect user-visible reliability and API honesty.

3. Keep quality remediation narrow and evidence-driven. The best quality targets are the specific boundary surfaces that intersect current defects: `OrchestrationController` route families, `ChatService` turn execution, `PlanController` assignment/prompt boundaries, and repository migration observability.

4. Require validation close to the failing contract. SSE cleanup, executor rejection, workflow execution, assignment submission, and pending-message ordering should be verified at controller/service/runtime boundaries, not only by isolated unit tests.

5. Revalidate archived public-alpha security/tool/control findings before public exposure, but do not treat the archived package as current proof. Its value is as a historical risk map and regression checklist.

6. Preserve SimplyPages/HTMX expectations for maintained UI surfaces. Raw string fragments and silent empty-state fallbacks should be treated as UI operational reliability risks when they affect Work Areas, Avatar, agents, outputs, or runtime status.

## Follow-ups

- Independent current-state triage of issue #14 across client disconnect, timeout, send failure, and model/tool failure callback ordering.
- Independent current-state triage of issue #15 across plain streaming, tool streaming, and tool-unsupported fallback.
- Independent assignment-boundary inventory for issue #16 across plan, task, workflow, and generic agent assignment paths.
- Independent workflow semantics triage for issues #17 and #18 with saved workflow JSON fixtures and execution evidence.
- Independent concurrent enqueue/claim probe for issue #19 against SQLite and the browser/API drain behavior.
- Independent queue rejection and cancellation-state review for issues #12 and #13 before alpha sign-off.
- Route-family responsibility inventory for `OrchestrationController`, prioritizing surfaces touched by active defects.
- Required-vs-optional collaborator classification for `ChatService` and plan completion validation.
- Repository migration observability audit for warm alpha data, with particular attention to ignored exceptions and table rewrites.
- Work Area explorer SimplyPages/HTMX reuse review before additional browser-file-surface work.
- Avatar dashboard failure-state review so missing data and failed data loads are distinguishable.
- Historical public-alpha revalidation pass for security/control, shell/file/web tool confinement, workflow XSS, workspace lease materialization, output materialization, mobile layout, and public REST/SSE route coverage.

## Resolved Or Stale Prior Findings

- Archived security/control and tool confinement blockers: unauthenticated mutation/control surface, path-segment traversal risk, host-level shell execution, broad file tool scope, web-fetch redirect SSRF, and workflow stored XSS were major archived public-alpha blockers. They are not treated as current findings in this artifact because the source package is archived and later validation context exists. They remain revalidation targets before public exposure.

- Archived direct-run and transcript/SSE contract blockers: public direct-run routes, chat/job UI controls bypassing submit-to-agent semantics, saved-plan transcript deletion, and plan-run event-name mismatch came from the archived large review. Current reports found adjacent assignment/run-name and SSE cleanup issues, but did not reassert those older exact defects as still open.

- Archived workflow authoring blockers: builder inability to save intermediate valid workflows, empty workflow no-op success, JS composer dominance over HTMX, and workflow graph XSS came from the archived workflow/frontend reports. Current active workflow issues are narrower and different: `PASS_THROUGH` route semantics and `DELEGATION` fake completion.

- Archived schema/workspace/output findings: repeated workspace lease drops, non-canonical `schema.sql`, project workspace lease materialization gaps, output symlink/materialization issues, stale output attribution, and inbox table split came from the archived large review. The current active schema finding is broader repository migration complexity, not proof those specific older defects remain.

- Archived UI stale-target/mobile findings: mobile orchestration shell failure, stale Docker target ids, and stale operational UI labels came from the archived package. No 2026-05-27 source re-established these exact issues as current active findings.

- 2026-05-25 governance drift finding: stale web/API guidance referencing removed alpha auth/CSRF behavior and retired Avatar note paths was explicitly reported as remediated by the 2026-05-25 review.

- Older 2026-05-08 precursor blockers: config secret/default posture, alpha auth boundary, selected-agent side-panel policy, runtime settings wiring, shell cancellation, and raw frontend interpolation were useful historical warnings. They are not asserted as current unless echoed by the 2026-05-27 or 2026-05-25 reports above.
