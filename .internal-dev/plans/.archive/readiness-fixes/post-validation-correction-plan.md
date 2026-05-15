# Context

The implemented `readiness-fixes` branch was reviewed against:

- `.internal-dev/plans/readiness-fixes/non-security-alpha-remediation-plan.md`
- `.internal-dev/plans/readiness-fixes/non-security-alpha-validation-criteria.md`
- `.internal-dev/plans/readiness-fixes/work-log.md`
- `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md`

The review found that most original remediation groups are implemented, but several alpha-facing behavior and evidence gaps remain. This plan corrects only those validation findings. Security-class work remains out of scope.

# Goal

Make the `readiness-fixes` implementation pass its validation criteria by fixing the browser-visible assignment regression, making feature flags enforce runtime behavior, standardizing side-panel stream lifecycle semantics, adding missing stream/wire-contract validation, and completing `.internal-dev` evidence.

# In Scope

- Fixing `/api/agents/{agentId}/assignments` request validation for real browser payloads.
- Enforcing schedules/reactions feature flags in runtime services, not only controller/UI routes.
- Making orchestration side-panel chat follow the same SSE lifecycle pattern as chat/task/workflow streams.
- Adding tests that actually cover stream lifecycle outcomes and chat stream serialization.
- Running and recording required validation: targeted tests, `mvn test`, startup smoke, browser validation or documented fallback.
- Cleaning the readiness work log and adding implementation changelog/knowledge evidence.

# Out of Scope

- Authentication, authorization, secrets, SSRF, frontend injection, selected-agent shell policy, or shell command policy redesign.
- Re-opening already-passed remediation groups unless a new test exposes a regression.
- Reworking the entire orchestration UI.
- Replacing Spring MVC `SseEmitter` with WebFlux endpoints across the app.
- Productizing schedules or event reactions for alpha. The hard decision is to keep them disabled by default and prevent runtime execution while disabled.

# Implementation Order

1. Fix assignment request validation first because it is a direct browser workflow regression.
2. Enforce schedule/reaction feature flags at runtime because hidden features must be inert.
3. Convert side-panel chat to asynchronous SSE lifecycle support.
4. Add missing stream lifecycle and serialization tests.
5. Run full validation and update `.internal-dev` evidence.

# Implementation Steps

## 1. Fix Agent Assignment Request Validation

### Issue

`AssignmentRequest.agentId` is `@NotBlank`, and `AgentOrchestrationController.assign` accepts `@Valid @RequestBody AssignmentRequest`. The orchestration UI submits assignment JSON without `agentId` because the target agent is already in the URL. In a real MVC request, validation rejects the body before the controller can replace the body agent id with the path variable.

### Where The Original Edit Went Wrong

The original validation fix added Bean Validation annotations directly to domain-ish request records and relied on direct controller unit tests. That missed Spring MVC's real validation order: `@Valid @RequestBody` happens before controller method logic. The direct unit test passed because it supplied `agentId` and bypassed MVC validation.

### Hard Decision

Do not require UI clients to duplicate path state in the request body. The path variable is the authoritative agent id for `/api/agents/{agentId}/assignments`. Use a route-specific DTO without `agentId` instead of weakening `AssignmentRequest` globally.

### Code Targets

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- Optional new MVC-style test class if the project pattern supports it:
  - `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerMvcTest.java`

### Implementation Shape

Add a route-specific DTO and map it to `AssignmentRequest` with the path `agentId`.

```java
@PostMapping("/assignments")
public WorkAssignment assign(
    @PathVariable String agentId,
    @Valid @RequestBody AgentAssignmentCreateRequest request
) {
    try {
        return assignmentService.create(new AssignmentRequest(
            agentId,
            request.jobId(),
            request.jobItemId(),
            request.assignmentType(),
            request.priority(),
            request.modelOverride(),
            request.workspaceId(),
            request.input()
        ));
    } catch (IllegalArgumentException | IllegalStateException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentAssignmentCreateRequest(
    String jobId,
    String jobItemId,
    @NotNull AssignmentType assignmentType,
    Integer priority,
    String modelOverride,
    String workspaceId,
    Map<String, Object> input
) {
    public AgentAssignmentCreateRequest {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
```

Keep `AssignmentRequest.agentId` validation for service/API paths where the request really owns agent id, such as job run defaults or internal service use. Do not remove `@NotBlank` from `AssignmentRequest` unless another route proves it is also path-owned.

### Tests To Add

- Test that `assign` accepts a request DTO with no body `agentId` and creates an assignment for the path agent id.
- Test that body `agentId` is ignored if supplied as an unknown JSON field.
- Add one Spring MVC/MockMvc-style test or lightweight `ObjectMapper` deserialization test proving the actual UI payload shape binds:

```json
{
  "assignmentType": "REPORT",
  "priority": 0,
  "modelOverride": null,
  "input": {"message": "hello"}
}
```

Expected result: no Bean Validation error for missing `agentId`; `assignmentService.create` receives `agentId` from the path.

## 2. Enforce Schedule And Reaction Feature Flags At Runtime

### Issue

Schedules and event reactions are hidden in controllers/UI but still execute if rows already exist. `ScheduleService.pollDueSchedules` still fires schedules; `OrchestrationEventService.handle` still enqueues assignments from enabled reactions.

### Where The Original Edit Went Wrong

The original alpha decision treated "not reachable through UI/API" as equivalent to "not alpha-facing." That is not enough for persisted operational systems. Hidden runtime features can still affect real users through old rows, direct DB edits, fixtures, or restored databases.

### Hard Decision

When a feature is disabled for alpha, both public routes and runtime execution must be disabled. Existing persisted schedules/reactions must remain stored but inert while the flag is false.

### Code Targets

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `.internal-dev/knowledge/alpha-surface-decisions.md`

### Implementation Shape

Inject feature flags into the services that execute runtime behavior.

```java
@Service
public class ScheduleService {
    private final boolean schedulesEnabled;

    public ScheduleService(
        OrchestrationRuntimeRepository repository,
        AgentProfileService agentProfileService,
        AssignmentService assignmentService,
        OrchestrationEventService eventService,
        @Value("${magenta.features.schedules-enabled:false}") boolean schedulesEnabled
    ) {
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.assignmentService = assignmentService;
        this.eventService = eventService;
        this.schedulesEnabled = schedulesEnabled;
    }

    @Scheduled(fixedDelayString = "${magenta.orchestration.scheduler-delay-ms:10000}")
    @Transactional
    public void pollDueSchedules() {
        if (!schedulesEnabled) {
        }
        // existing due processing
    }
}
```

For reactions, gate event handling before reading enabled reactions:

```java
@Service
public class OrchestrationEventService {
    private final boolean reactionsEnabled;

    public OrchestrationEventService(
        OrchestrationRuntimeRepository repository,
        AssignmentService assignmentService,
        @Value("${magenta.features.reactions-enabled:false}") boolean reactionsEnabled
    ) {
        this.repository = repository;
        this.assignmentService = assignmentService;
        this.reactionsEnabled = reactionsEnabled;
    }

    @Transactional
    public void handle(OrchestrationEvent event) {
        if (!reactionsEnabled) {
            repository.saveEvent(new OrchestrationEvent(
                event.id(), event.eventType(), event.sourceType(), event.sourceId(),
                event.payload(), event.createdAt(), Instant.now()
            ));
        }
        // existing reaction handling
    }
}
```

The event still gets marked handled when reactions are disabled. This prevents repeated "unhandled" state while guaranteeing no reaction-generated assignment is created.

Keep controller-level `404` checks. They are still useful for public API behavior.

### Constructor Compatibility

Many tests instantiate these services directly. Add secondary constructors or default flag parameters so current tests can opt into enabled behavior explicitly:

```java
public ScheduleService(repository, agentProfileService, assignmentService, eventService) {
    this(repository, agentProfileService, assignmentService, eventService, true);
}
```

Use `true` for existing runtime tests that were written to validate schedule/reaction mechanics. Add new disabled tests with `false`.

### Tests To Add

- Disabled schedules:
  - Insert or save an enabled due schedule.
  - Call `pollDueSchedules()`.
  - Assert no `schedule_firings` row was created.
  - Assert no assignment was created.
  - Assert `nextRunAt` did not advance.
- Enabled schedules:
  - Existing schedule due/idempotency tests must still pass with `schedulesEnabled=true`.
- Disabled reactions:
  - Save an enabled reaction.
  - Publish a matching event or call `handle`.
  - Assert the event is marked handled.
  - Assert no assignment was created.
- Enabled reactions:
  - Existing reaction enqueue tests must still pass with `reactionsEnabled=true`.

## 3. Standardize Side-Panel Chat SSE Lifecycle

### Issue

The side-panel endpoint is named `/chat/stream` and returns `SseEmitter`, but it runs `chatService.chat(...)` synchronously in the request thread. It does not use `SubscriptionGuard`, does not offload model work, and cannot cleanly handle client disconnect/cancellation like the other stream endpoints.

### Where The Original Edit Went Wrong

The original SSE standardization changed emitter construction for the side panel but left its execution model synchronous. That satisfied a superficial "uses `createEmitter`" check but not the lifecycle semantics required by the plan.

### Hard Decision

Keep the endpoint as SSE and make it asynchronous. Do not downgrade it to JSON, because the UI already consumes it as SSE and the original criteria explicitly included the orchestration side-panel stream.

### Code Targets

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- Optional new helper:
  - `src/main/java/io/mindspice/magenta2/api/web/AgentChatStreamSupport.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/main/resources/static/js/orchestration/agent-chat.js` only if event names or payloads change. Prefer no JS change.

### Implementation Shape

Use Reactor like task/workflow streams. Return the emitter immediately, run model work on `boundedElastic`, and bind cleanup through `SubscriptionGuard`.

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@PathVariable String agentId, @Valid @RequestBody AgentChatRequest request) {
    SseEmitter emitter = SseStreamLifecycle.createEmitter();
    SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
    SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

    Disposable subscription = Flux.defer(() -> {
            AgentProfile agent = agentProfileService.get(agentId);
            String message = normalizeMessage(request);
            String pageContext = normalizePageContext(request);
            String model = normalizeModel(request, agent);
            ChatRequest.MsgRequest chatRequest = new ChatRequest.MsgRequest(
                request.conversationId(),
                "Agent page context: " + pageContext + "\n\n" + message,
                model,
                null
            );
            return Flux.just(new AgentChatResult(agent, chatService.chat(chatRequest)));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
            result -> sendStartAndDone(emitter, result),
            error -> sendErrorAndComplete(emitter, error),
            emitter::complete
        );
    guard.set(subscription);
    return emitter;
}
```

Prefer emitting `start` before the model call. If doing so inside the `Flux` is awkward, use:

```java
Flux.concat(
    Flux.just(startPayload),
    Flux.fromCallable(() -> donePayloadFrom(chatService.chat(chatRequest)))
)
```

Keep payload names currently used by `agent-chat.js`:

- `start`: `event`, `agentId`, `agentName`
- `done`: `event`, `agentId`, `conversationId`, `model`, `message`
- `error`: `event`, `error`

### Error Handling Rules

- Blank message: send `error` event and complete. Do not throw a 500.
- Missing agent: send `error` event and complete, unless existing API semantics require a 404 before stream creation. Choose one behavior and test it.
- Unsupported chat response: send `error` event and complete.
- Client disconnect/completion/timeout: dispose subscription through the guard.

### Tests To Add

- `agentChatStreamReturnsBeforeChatServiceCompletes`.
- `agentChatStreamDisposesSubscriptionOnCompletion`.
- `agentChatStreamEmitsStartAndDone`.
- `agentChatStreamEmitsErrorForBlankMessage`.
- `agentChatStreamEmitsErrorForUnsupportedChatResponse`.
- If feasible with test hooks, simulate emitter completion and verify the running subscription is disposed.

Do not only assert `emitter.getTimeout()` or non-null emitters.

## 4. Add Real Stream Lifecycle Validation

### Issue

The current stream tests do not actually prove all terminal outcomes required by validation. Some `SseStreamLifecycleTest` methods only call `guard.dispose()` and confirm timeout/error handlers were not invoked.

### Where The Original Edit Went Wrong

The original tests validated helper internals instead of observable stream lifecycle behavior. That created confidence around `AtomicReference` disposal but did not validate completion, timeout, error, disconnect, or cancellation semantics.

### Hard Decision

Use two levels of tests:

1. Unit-test `SseStreamLifecycle` with a testable callback registration abstraction.
2. Controller-level tests for chat/task/workflow/side-panel terminal behavior using captured emitters or live fallback probes.

Do not spend time trying to force private `SseEmitter` internals through brittle reflection if a small wrapper makes lifecycle callbacks testable.

### Code Targets

- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java`
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`

### Implementation Shape

Add a package-private lifecycle registration helper that can be unit-tested without relying on `SseEmitter` internals.

```java
static LifecycleCallbacks callbacks(
    SubscriptionGuard guard,
    Runnable onTimeoutHandler,
    Consumer<Throwable> onErrorHandler
) {
    return new LifecycleCallbacks(
        guard::dispose,
        () -> {
            guard.dispose();
            if (onTimeoutHandler != null) onTimeoutHandler.run();
        },
        error -> {
            guard.dispose();
            if (onErrorHandler != null) onErrorHandler.accept(error);
        }
    );
}

static void registerCallbacks(
    SseEmitter emitter,
    SubscriptionGuard guard,
    Runnable onTimeoutHandler,
    Consumer<Throwable> onErrorHandler
) {
    LifecycleCallbacks callbacks = callbacks(guard, onTimeoutHandler, onErrorHandler);
    emitter.onCompletion(callbacks.onCompletion());
    emitter.onTimeout(callbacks.onTimeout());
    emitter.onError(callbacks.onError());
}

record LifecycleCallbacks(
    Runnable onCompletion,
    Runnable onTimeout,
    Consumer<Throwable> onError
) {}
```

Then tests can call the returned callbacks directly and prove timeout/error handlers run.

### Required Test Matrix

For each stream endpoint changed by this correction, add focused tests for:

- Normal completion.
- Model/service error.
- Validation failure before work starts.
- Timeout callback disposal where applicable.
- Client completion/disconnect disposal through callback.
- User cancellation or interrupt where the endpoint supports it.

For chat stream cancellation, reuse `ActiveTurnRegistry` and existing interrupt endpoint tests if possible. The minimum acceptable proof is:

- active turn is registered,
- interrupt is accepted,
- stream terminal cleanup removes the active turn,
- no retry is triggered for cancellation.

## 5. Add ChatStreamEvent Serialization Tests

### Issue

`ChatStreamEvent` changed from a nullable record to sealed typed records. Even if browser field access still works, this is a wire-contract change and needs serialization tests.

### Where The Original Edit Went Wrong

The original edit reasoned from manual JS inspection and full test suite pass. There was no test that serialized the event records and compared the JSON fields consumed by `chat-client.js`.

### Hard Decision

Keep the sealed hierarchy, but test it as a public wire contract. Do not revert to nullable union unless serialization proves incompatible.

### Code Targets

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEvent.java`
- New test:
  - `src/test/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEventSerializationTest.java`

### Test Shape

Use `ObjectMapper` with the same module registration style used elsewhere.

```java
@Test
void startEventSerializesFieldsReadByBrowser() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ChatStreamEvent.Start event = new ChatStreamEvent.Start(
        "conversation-1", "model-a", "turn-1", "token-1", ChatPlanState.normal()
    );

    JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

    assertThat(json.get("conversationId").asText()).isEqualTo("conversation-1");
    assertThat(json.get("model").asText()).isEqualTo("model-a");
    assertThat(json.get("turnId").asText()).isEqualTo("turn-1");
    assertThat(json.get("interruptToken").asText()).isEqualTo("token-1");
    assertThat(json.has("planState")).isTrue();
}
```

Add tests for:

- `Start`
- `Chunk`
- `Tool`
- `SystemNotice`
- `Interrupt`
- `Context`
- `Done`
- `Error`

For each event, assert the fields consumed by `src/main/resources/static/js/chat-client.js`.

## 6. Browser Validation Or Documented Fallback

### Issue

The implementation changed live chat/SSE/browser behavior but did not record Playwright MCP validation or a fallback browser probe.

### Where The Original Edit Went Wrong

The original implementation stopped at unit tests. The validation criteria explicitly require browser validation when live chat, SSE, interruption, browser surfaces, task/workflow streaming, or visible stream/client behavior changes.

### Hard Decision

Run browser validation after code fixes. If Playwright MCP is blocked, use a fallback browser-origin probe against the same live app and record that blocker. Do not mark validation complete with only `mvn test`.

### Code/Artifact Targets

- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` if a reusable workflow improvement is discovered.
- `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md` or a follow-up review file.
- `.internal-dev/changelogs/<date>-non-security-alpha-remediation-implementation.md`
- Optional fallback fixture:
  - `.internal-dev/test-fixtures/readiness-fixes/live-validation.js`

### Minimum Browser Probe

Run the app against isolated SQLite:

```bash
rm -f /tmp/magenta2-alpha-correction-browser.sqlite
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-correction-browser.sqlite'
```

Probe at least:

- `/chat` loads.
- Basic `/api/chat/stream` emits `start` and terminal `done` or expected `error`.
- `/api/tasks/{taskId}/runs/stream` emits `started` and terminal event for a controlled fixture.
- `/api/workflows/{workflowId}/runs/stream` emits `started` and terminal event for a controlled fixture.
- `/api/agents/{agentId}/chat/stream` side-panel endpoint returns quickly and emits valid SSE.
- Agent assignment form payload shape succeeds without body `agentId`.
- Disabled schedules/reactions routes return 404.
- No unexpected console or network errors.

Record the exact validation path and result in the implementation changelog or follow-up review.

## 7. Evidence Cleanup

### Issue

The work log is stale/malformed and there is no implementation changelog for the remediation branch.

### Where The Original Edit Went Wrong

The work log was append-heavy and not reconciled after later agents completed pending items. It contains contradictory status for 2.2 and table rows embedded in narrative text.

### Hard Decision

Do not rewrite history into a polished story. Add a correction section at the top that supersedes stale rows, and fix the obvious malformed table entries. Keep the detailed reports below for traceability.

### Artifact Targets

- `.internal-dev/plans/readiness-fixes/work-log.md`
- `.internal-dev/changelogs/2026-05-08-non-security-alpha-remediation-implementation.md`
- `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md` or a follow-up review.
- `.internal-dev/knowledge/alpha-surface-decisions.md`

### Work Log Fix

Add a top section:

```markdown
## Validation Correction Status

This section supersedes the original summary table below.

| Finding | Status | Evidence |
|---|---|---|
| Assignment request validation | fixed | tests..., browser... |
| Feature flags runtime enforcement | fixed | tests... |
| Side-panel SSE lifecycle | fixed | tests..., browser... |
| Stream lifecycle coverage | fixed | tests... |
| ChatStreamEvent serialization | fixed | tests... |
| Evidence closeout | fixed | changelog..., review... |
```

Then fix the original issue 2.2 row from `pending` to completed only after the corrective tests pass.

# Validation

Run these commands after implementation:

```bash
mvn test -Dtest=AgentOrchestrationControllerTest,OrchestrationDurableRuntimeTest
mvn test -Dtest=SseStreamLifecycleTest,ChatControllerTest,TaskControllerTest,WorkflowControllerTest,ChatStreamEventSerializationTest
mvn test
rm -f /tmp/magenta2-alpha-correction-smoke.sqlite
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-correction-smoke.sqlite'
```

Expected startup smoke result: exit code `124` is acceptable only if logs show healthy Tomcat startup before timeout.

Browser validation is required because these fixes touch browser/SSE behavior:

- Use Playwright MCP first.
- If MCP is blocked by profile infrastructure, record the blocker and run a documented fallback browser-origin probe against the same live app.

# Exit Criteria

- `/api/agents/{agentId}/assignments` accepts the UI payload without body `agentId` and creates the assignment for the path agent.
- Disabled schedules do not fire, create firings, create assignments, publish due events, or advance `nextRunAt`.
- Disabled reactions do not enqueue assignments from persisted enabled reactions.
- Side-panel chat stream returns immediately, offloads model work, uses shared lifecycle cleanup, and preserves existing SSE event names.
- Stream tests cover real completion, error, timeout callback, disconnect/completion cleanup, and cancellation/interrupt behavior where supported.
- `ChatStreamEvent` serialization tests cover every typed event consumed by `chat-client.js`.
- `mvn test` passes.
- Startup smoke passes or has a real documented external blocker.
- Browser validation or accepted fallback is recorded.
- Work log is corrected.
- Implementation changelog exists.
- Validation review is updated or superseded with a follow-up showing no unresolved blocking findings.
