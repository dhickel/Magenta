# Remove Alpha Authentication

## 1. Objective

Fully remove the alpha authentication, authorization, and CSRF implementation introduced by `public-alpha-remediation/security-access-control`. The alpha UI and public APIs should be open by default, with no Basic auth prompt, no CSRF token requirement, no Spring Security filter chain, no alpha credential properties, and no auth-specific browser helper. This is a removal plan, not a disable-by-configuration plan.

Keep non-auth shell and safety protections that are still valid for an open alpha: XSS-safe DOM/server rendering, plain path-segment validation before filesystem path composition, path traversal protection, and agent-scoped assignment lifecycle protections. Those protections are not auth scaffolding and must not be removed as collateral cleanup.

## 2. Inputs And Assumptions

Confirmed inputs:

- Current checkout branch: `alpha-docs-and-entity-selectors`.
- Current worktree is dirty before this planning task:
  - `AGENTS.md`
  - `.internal-dev/notes/idea_drop.md`
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- The alpha security branch lineage is:
  - base: `2690e9a plans`
  - branch commits: `bf10a85 Add alpha auth CSRF gate`, `21331ea Record alpha auth validation`, `b1418f4 Validate path segment ids`, `de3c76e Record path segment validation gate`, `0c114bb Render workflow graph text safely`, `5a03fe3 Record workflow XSS validation gate`, `f4b1978 Scope assignment lifecycle controls`, `17525ee Record lifecycle validation gate`, `7cad407 Record security domain validation`.
- `public-alpha-remediation/security-access-control` is already an ancestor of the current integration lineage, so scope auth removal by diffing `2690e9a..public-alpha-remediation/security-access-control` and by searching current `HEAD` for auth/CSRF residue.
- Relevant changelogs read:
  - `.internal-dev/changelogs/2026-05-18-alpha-auth-csrf-gate.md`
  - `.internal-dev/changelogs/2026-05-18-security-access-control-domain.md`
  - `.internal-dev/changelogs/2026-05-18-public-alpha-security-id-segment-validation.md`
  - `.internal-dev/changelogs/2026-05-18-workflow-graph-xss-security.md`
  - `.internal-dev/changelogs/2026-05-18-agent-scoped-assignment-lifecycle.md`
- Relevant package guides read:
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/core/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/core/util/AGENTS.md`

Assumptions for the implementing agent to verify before editing:

- The open-alpha policy applies to all web/API routes currently protected only by `AlphaSecurityConfiguration`; there is no alternate auth layer to preserve in this codebase.
- Removing `spring-boot-starter-security` is safe because current application code uses Spring Security only for the alpha gate and its tests. Re-run `rg` to catch any new uses before deletion.
- If HTMX non-2xx fragment swapping still needs a browser helper after auth removal, it should live under a neutral shell name with no auth/CSRF behavior. Do not keep `alpha-security.js` as a generic helper.

## 3. Scope

In scope:

- Remove Spring Security alpha gate code, dependencies, tests, properties, docs, Playwright auth defaults, and browser shell references.
- Delete auth/CSRF-specific browser behavior: `XSRF-TOKEN` cookie reads, `X-XSRF-TOKEN` header injection, fetch monkey patching for CSRF, Basic-auth error UI, and `magenta:security-error` handling.
- Preserve or move non-auth HTMX shell behavior only if it is needed for existing open-alpha UX, such as allowing known operational error fragments to swap on `400` responses.
- Update tests so mutation routes are exercised without auth credentials and without CSRF request processors.
- Update docs and package guides so they accurately state that alpha routes are intentionally open and that auth/authorization will be redone at a different layer later.
- Complete `.internal-dev` closeout after implementation: changelog, reusable knowledge if useful, out-of-scope bugs immediately if discovered, archive this plan when finalized, and commit implementation plus `.internal-dev` updates.

Out of scope:

- Adding replacement authentication, authorization, role checks, sessions, users, login pages, API keys, reverse-proxy guidance, or feature flags.
- Leaving the existing auth implementation disabled by properties, profiles, or Spring autoconfigure exclusions.
- Removing non-auth safety work from the security branch:
  - `PlainPathSegmentValidator`
  - agent/workspace path segment validation
  - workflow editor XSS-safe rendering
  - assignment lifecycle scoping by route `agentId`
  - route-id/path traversal protection
- Reworking broader frontend architecture except for removing auth shell script references and preserving any required non-auth shell helper.

## 4. Current-State Analysis

Auth/authorization/CSRF implementation to remove:

- `pom.xml`
  - `spring-boot-starter-security`
  - `spring-security-test`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
  - `@EnableWebSecurity`
  - `SecurityFilterChain`
  - Basic auth entry point
  - `UserDetailsService`
  - `magenta.alpha-access` properties
  - CSRF token repository/filter
  - HTMX auth error rendering
- `src/main/resources/static/js/alpha-security.js`
  - CSRF cookie/header support
  - same-origin fetch monkey patch
  - HTMX auth/CSRF error UI
  - currently also contains a non-auth `htmx:beforeSwap` helper for operational error fragments.
- `src/main/resources/application.yml`
  - `magenta.alpha-access.username`
  - `magenta.alpha-access.password`
- `src/test/resources/application.yml`
  - test `magenta.alpha-access` properties.
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
  - `ALPHA_SECURITY_JS`
  - `.addCustomJs(ALPHA_SECURITY_JS)` in the shared shell.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
  - `ALPHA_SECURITY_JS`
  - `.addCustomJs(ALPHA_SECURITY_JS)` in the dashboard shell.
- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java`
  - delete completely.
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
  - currently asserts `/js/alpha-security.js?v=1` is present.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
  - `alphaSecurityJsAllowsKnownOperationalErrorFragmentsToSwapOnNon2xx` reads `alpha-security.js`.
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
  - imports `SecurityMockMvcRequestPostProcessors.csrf` and `httpBasic`.
  - sets `magenta.alpha-access.*`.
  - calls `.with(alphaAuth()).with(csrf())` on unsafe requests.
  - helper `alphaAuth()`.
- `tests/playwright/public-alpha-harness.spec.js`
  - auth-gate probe named `unsafe anonymous mutation is an expected non-2xx validation path`.
  - `alphaCredentials()` and `xsrfToken()`.
- `playwright.config.js`
  - `httpCredentials`.
  - `MAGENTA_ALPHA_USERNAME` and `MAGENTA_ALPHA_PASSWORD` defaults.
- `tests/playwright/README.md`
  - startup and environment docs mention alpha credentials.
- Docs currently documenting alpha auth:
  - `docs/api/00-index.md`
  - `docs/technical/security.md`
  - `docs/technical/configuration-operations.md`
  - `docs/technical/architecture.md`
  - `docs/technical/frontend-htmx.md`
  - `docs/technical/api-reference.md`
  - `docs/end-user/quickstart.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
  - responsibility says this package owns the public-alpha HTTP access gate.
  - guidance says to preserve CSRF compatibility.

Non-auth security/safety implementation to keep:

- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/test/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidatorTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
  - keep id validation around profile ids.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfilePathSegmentValidationTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
  - keep `agentId`, `jobId`, `projectId`, `assignmentId` validation.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
  - keep validation before path composition.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathSegmentValidationTest.java`
- Current server-rendered workflow XSS coverage in `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`, especially `workflowEditorRendersPersistedNodePayloadsAsEscapedDomValues`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
  - keep scoped lifecycle methods `cancel(agentId, assignmentId)`, `pause(agentId, assignmentId)`, `resume(agentId, assignmentId)`, `forceInterrupt(agentId, assignmentId, reason)`, `delete(agentId, assignmentId)`, and `transcript(agentId, assignmentId)`.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
  - keep passing route `agentId` to assignment lifecycle operations.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
  - keep passing route `agentId` to assignment lifecycle operations.
- Existing tests around same-agent/cross-agent lifecycle behavior:
  - `OrchestrationRuntimeTest.scopedAssignmentLifecycleAcceptsSameAgentControls`
  - `OrchestrationRuntimeTest.scopedAssignmentLifecycleRejectsCrossAgentControlsWithoutMutation`
  - controller tests that assert lifecycle errors render and state is unchanged.

## 5. Target Design

The target state is an open-alpha Spring Boot application with no application-level authentication or CSRF enforcement:

- No `SecurityFilterChain` bean.
- No `UserDetailsService` bean for alpha access.
- No Spring Security dependencies in `pom.xml`.
- No `magenta.alpha-access` config keys or `MAGENTA_ALPHA_*` docs.
- No CSRF token cookie expectation.
- No Basic auth requirement in tests, docs, Playwright config, or browser validation.
- Unsafe methods should reach the existing controller/service validation paths directly. Expected invalid requests should fail because of domain validation, not because of auth/CSRF.

Shell helper target:

- Delete `src/main/resources/static/js/alpha-security.js`.
- If non-auth HTMX error-fragment swapping is required, create a neutral helper such as `src/main/resources/static/js/magenta-shell.js` and load it from relevant shells.
- The neutral helper may contain only generic open-alpha shell behavior, for example:

```javascript
(function () {
  function responseIsSameOrigin(xhr) {
    if (!xhr || !xhr.responseURL) return true;
    return new URL(xhr.responseURL, window.location.href).origin === window.location.origin;
  }

  function hasOperationalErrorFragment(responseText) {
    if (!responseText) return false;
    const doc = new DOMParser().parseFromString(responseText, "text/html");
    return Boolean(doc.querySelector(".orch-error, .orch-status-error, .agent-lifecycle-panel"));
  }

  document.body.addEventListener("htmx:beforeSwap", function (event) {
    const detail = event.detail || {};
    const xhr = detail.xhr;
    if (!xhr || xhr.status < 400) return;
    if (!responseIsSameOrigin(xhr)) return;
    if (!detail.target || !document.documentElement.contains(detail.target)) return;
    if (!hasOperationalErrorFragment(xhr.responseText)) return;
    event.detail.shouldSwap = true;
  });
})();
```

Do not include these auth/CSRF behaviors in the neutral helper:

- `XSRF-TOKEN`
- `X-XSRF-TOKEN`
- `csrf`
- `httpBasic`
- `Authentication required.`
- `CSRF token missing or invalid.`
- `mag-auth-error`
- `magenta:security-error`
- fetch monkey patching for auth/CSRF.

Documentation target:

- `docs/technical/security.md` should become a current alpha security posture document, not an auth implementation document. It should say the app is intentionally open for alpha and list the non-auth safety controls that remain.
- API docs should say there is no built-in alpha auth/CSRF gate in this layer. Keep validation/status documentation for domain errors.
- Frontend/HTMX docs should mention generic HTMX fragment handling if kept, not alpha auth events or CSRF.

## 6. Implementation Plan

### Step 0: Create a clean implementation branch or worktree

The current main worktree is dirty. The implementation agent must not overwrite unrelated local changes. Use a dedicated worktree if these changes are still present:

```bash
git status --short --branch
git worktree add ../magenta2-remove-alpha-auth alpha-docs-and-entity-selectors
cd ../magenta2-remove-alpha-auth
git switch -c remove-alpha-authentication
```

If the main worktree is clean by then, a direct branch is acceptable:

```bash
git switch -c remove-alpha-authentication
```

Before coding, capture the auth residue baseline:

```bash
rg -n "MAGENTA_ALPHA|alpha-access|spring-boot-starter-security|spring-security-test|AlphaSecurity|alpha-security|XSRF|csrf|httpBasic|SecurityFilterChain|UserDetailsService|EnableWebSecurity" -g '!target/**' .
```

### Step 1: Remove Spring Security from build and runtime configuration

Edit `pom.xml`:

- Remove `spring-boot-starter-security`.
- Remove `spring-security-test`.

Edit `src/main/resources/application.yml`:

- Delete the full `magenta.alpha-access` block.
- Keep `magenta.features`, `magenta.plan`, and `magenta.ai`.

Edit `src/test/resources/application.yml`:

- Delete the test `magenta.alpha-access` block.
- Keep test feature flags and test datasource config.

Delete:

- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`

Gotcha: do not replace this with a permit-all security filter chain, and do not add `SecurityAutoConfiguration` excludes. The absence of Spring Security is the intended target.

### Step 2: Remove auth shell script and neutralize shell behavior

Delete:

- `src/main/resources/static/js/alpha-security.js`

Edit `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`:

- Remove `ALPHA_SECURITY_JS`.
- Remove `.addCustomJs(ALPHA_SECURITY_JS)` from `shell(...)`.
- If a new neutral shell helper is created, name the constant `MAGENTA_SHELL_JS` or similar and load that instead. Do not use "alpha" or "security" in the filename or constant name for generic shell behavior.

Edit `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`:

- Remove `ALPHA_SECURITY_JS`.
- Remove `.addCustomJs(ALPHA_SECURITY_JS)` from the dashboard shell.
- If a neutral helper is created for HTMX error fragment swapping, load it here only if the behavior is needed on orchestration pages. Loading it in the top-level/chat shell is acceptable only if tests prove those shells need it.

If preserving non-auth HTMX error swap:

- Add `src/main/resources/static/js/magenta-shell.js`.
- Copy only the `htmx:beforeSwap` operational error fragment behavior from `alpha-security.js`.
- Do not copy cookie, CSRF, Basic-auth, `responseError`, fetch monkey patch, or auth text behavior.
- Add a static test that proves the new helper contains `htmx:beforeSwap`, `event.detail.shouldSwap = true`, `.orch-error, .orch-status-error, .agent-lifecycle-panel`, and does not contain `csrf`, `XSRF`, `X-XSRF`, `Authentication required`, `mag-auth-error`, or `fetch =`.

### Step 3: Update web package guide

Edit `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`:

- Remove the responsibility bullet that says the package owns the public-alpha HTTP access gate.
- Remove the guidance bullet about preserving CSRF compatibility.
- Add a replacement guidance bullet:
  - `Alpha web/API routes are intentionally open in this layer; do not reintroduce authentication, authorization, CSRF, sessions, or credential prompts without a new auth-layer plan.`
- Keep controller-thin, public API, HTMX, and SimplyPages guidance intact, including the current user edits about reusable components.

### Step 4: Update tests that directly assert auth behavior

Delete:

- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java`

Edit `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`:

- Replace assertions that `home` and `chat` contain `/js/alpha-security.js?v=1` with `doesNotContain("/js/alpha-security.js")`.
- If a neutral helper is loaded, assert the exact neutral path instead, for example `/js/magenta-shell.js?v=1`.

Edit `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`:

- Remove or rename `alphaSecurityJsAllowsKnownOperationalErrorFragmentsToSwapOnNon2xx`.
- If `magenta-shell.js` exists, replace it with `magentaShellJsAllowsKnownOperationalErrorFragmentsToSwapOnNon2xx` and assert:
  - contains `htmx:beforeSwap`
  - contains `event.detail.shouldSwap = true`
  - contains `xhr.status < 400`
  - contains `responseIsSameOrigin(xhr)`
  - contains `document.documentElement.contains(detail.target)`
  - contains `.orch-error, .orch-status-error, .agent-lifecycle-panel`
  - does not contain `csrf`
  - does not contain `XSRF`
  - does not contain `Authentication required.`
  - does not contain `mag-auth-error`
- Add or update shell-render tests to assert `/js/alpha-security.js` is absent from dashboard pages.

### Step 5: Update integration route-binding tests to prove open mutations

Edit `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`:

- Remove imports:
  - `SecurityMockMvcRequestPostProcessors.csrf`
  - `SecurityMockMvcRequestPostProcessors.httpBasic`
- Remove `magenta.alpha-access.username` and `magenta.alpha-access.password` from `@TestPropertySource`.
- Remove every `.with(alphaAuth())`.
- Remove every `.with(csrf())`.
- Delete helper `alphaAuth()`.
- Keep the same request bodies and expected domain outcomes.
- Add one explicit regression test near the top:

```java
@Test
void unsafeMutationRoutesAreOpenWithoutAuthOrCsrf() throws Exception {
    String agentId = createAgent();
    String planId = createPlan("Open Alpha Plan");

    MvcResult stream = mockMvc.perform(post("/api/plans/" + planId + "/runs/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content(json(Map.of("agentId", agentId))))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(stream))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("event:submitted")));
}
```

Also add one negative assertion that the old auth failure no longer exists:

```java
mockMvc.perform(post("/api/chat/" + conversationId + "/plan/execute"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.error", containsString("Direct plan execution is disabled")));
```

The important proof is that the result is a domain validation response, not `401` or `403`.

### Step 6: Preserve non-auth safety tests and do not rename them as auth removal

Keep these tests and update only if signatures drift because of unrelated current-branch changes:

- `src/test/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidatorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfilePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
  - especially the scoped lifecycle tests.
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
  - lifecycle route tests.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
  - workflow XSS-safe rendering and lifecycle fragment tests.

Do not delete `PlainPathSegmentValidator` or lifecycle scoping to make auth removal easier. If a test fails here after auth cleanup, fix the actual coupling rather than removing the safety behavior.

### Step 7: Update Playwright harness and browser validation docs

Edit `playwright.config.js`:

- Remove `username`, `password`, and `httpCredentials`.
- Keep `baseURL`, timeouts, reporter, and project config.

Edit `tests/playwright/public-alpha-harness.spec.js`:

- Delete `alphaCredentials()`.
- Delete `xsrfToken()`.
- Replace `unsafe anonymous mutation is an expected non-2xx validation path` with an open-alpha regression test, for example:

```javascript
test("unsafe mutation reaches domain validation without auth or csrf", async ({ request }, testInfo) => {
  const response = await request.post("/plans/_editor/_draft", {
    headers: { "HX-Request": "true" },
  });
  expect(response.status()).not.toBe(401);
  expect(response.status()).not.toBe(403);
  expect(response.status(), "open alpha mutation should reach route/domain handling").toBeLessThan(500);
});
```

- If the old `EXPECTED_NON_2XX` fixture only existed for the auth probe, remove or rename that fixture.
- Keep existing focused page/HTMX workflow validation.

Edit `tests/playwright/README.md`:

- Remove `MAGENTA_ALPHA_USERNAME` and `MAGENTA_ALPHA_PASSWORD` from startup examples and env var list.
- State that the harness assumes the app is open for alpha and does not configure browser HTTP credentials.

### Step 8: Update public docs

Update these docs to remove auth/CSRF claims:

- `docs/api/00-index.md`
  - Replace the security summary with: alpha routes are currently open in this application layer; unsafe methods are protected only by route/service validation until auth is reintroduced elsewhere.
  - Remove `401`/`403` auth/CSRF common error language unless a controller independently returns those statuses for a non-auth reason.
- `docs/technical/security.md`
  - Rewrite around current open-alpha safety posture.
  - Include preserved controls:
    - XSS-safe server/DOM rendering for workflow/editor surfaces.
    - plain path-segment validation before filesystem path composition.
    - workspace/data-root confinement.
    - agent-scoped assignment lifecycle operations.
  - Explicitly state removed controls:
    - Spring Security alpha Basic auth.
    - CSRF token filter/cookie/header.
    - alpha credential properties.
  - State that future auth/authorization will be designed in a separate layer/plan.
- `docs/technical/configuration-operations.md`
  - Remove `MAGENTA_ALPHA_USERNAME` and `MAGENTA_ALPHA_PASSWORD`.
  - Remove "unsafe methods require Basic auth and CSRF".
- `docs/technical/architecture.md`
  - Remove `alpha security` from `api.web` ownership.
  - Remove `alpha CSRF/header injection` from frontend JS justification.
- `docs/technical/frontend-htmx.md`
  - Remove `alpha-security.js` link and CSRF guidance.
  - If `magenta-shell.js` exists, document it narrowly as generic HTMX non-2xx fragment support.
- `docs/technical/api-reference.md`
  - Remove the global auth/CSRF requirement.
  - Update common errors to domain validation and not auth.
- `docs/end-user/quickstart.md`
  - Remove the `Authentication And CSRF` section or replace with an "Open Alpha Access" section that states no built-in login is active yet.

Run `rg` again after docs updates to ensure no stale auth guidance remains outside archived historical artifacts.

### Step 9: Full residue scan and cleanup

Run:

```bash
rg -n "MAGENTA_ALPHA|alpha-access|spring-boot-starter-security|spring-security-test|AlphaSecurity|alpha-security|XSRF|X-XSRF|csrf|httpBasic|SecurityFilterChain|UserDetailsService|EnableWebSecurity|Basic realm|mag-auth-error|magenta:security-error" -g '!target/**' -g '!**/.archive/**' .
```

Expected result after implementation:

- No production or active test/docs hits for auth/CSRF residue.
- Historical changelogs/knowledge may still mention what was removed. If the scan finds only old `.internal-dev/changelogs` entries that are part of history, do not edit them unless the task owner asks.
- If active `.internal-dev/plans/public-alpha-remediation/**` still claims the auth domain is current, either update only if this branch is still active or mention it in the implementation closeout. Do not broadly rewrite archived historical plans.

Also run:

```bash
rg -n "PlainPathSegmentValidator|workflowEditorRendersPersistedNodePayloadsAsEscapedDomValues|scopedAssignmentLifecycle" src/main/java src/test/java
```

Expected result:

- Non-auth safety code/tests are still present.

## 7. Tests To Add, Update, Or Remove

Remove:

- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java`

Update:

- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
  - assert alpha security script is absent; assert neutral helper only if created.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
  - replace alpha-security JS test with neutral helper test or remove it if no helper remains.
  - keep workflow XSS and lifecycle tests.
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
  - remove security request processors and alpha test properties.
  - add open unsafe mutation regression test.
- `tests/playwright/public-alpha-harness.spec.js`
  - remove auth/CSRF probe and helper functions.
  - add open unsafe mutation regression that fails on `401` or `403`.
- `playwright.config.js`
  - remove `httpCredentials`.

Keep and run:

- `src/test/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidatorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfilePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`

Suggested focused test command:

```bash
mvn test \
  -Dtest=FrontendControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest,PlainPathSegmentValidatorTest,AgentProfilePathSegmentValidationTest,WorkspacePathSegmentValidationTest,OrchestrationRuntimeTest,AgentOrchestrationControllerTest
```

Then run the full suite:

```bash
mvn test
```

## 8. Validation Workflow For Separate Validation Agent

The validation agent must use `gpt-5.3-codex` with reasoning effort `medium`, per repo policy.

Validation agent scope:

1. Confirm no active auth/CSRF residue:

```bash
rg -n "MAGENTA_ALPHA|alpha-access|spring-boot-starter-security|spring-security-test|AlphaSecurity|alpha-security|XSRF|X-XSRF|csrf|httpBasic|SecurityFilterChain|UserDetailsService|EnableWebSecurity|Basic realm|mag-auth-error|magenta:security-error" -g '!target/**' -g '!**/.archive/**' .
```

2. Confirm unsafe route reaches domain/controller behavior without auth:

```bash
mvn test -Dtest=PublicApiRouteBindingTest
```

Expected: no test uses `httpBasic` or `csrf`; unsafe route tests pass without credentials.

3. Confirm non-auth safety protections remain:

```bash
mvn test -Dtest=PlainPathSegmentValidatorTest,AgentProfilePathSegmentValidationTest,WorkspacePathSegmentValidationTest,OrchestrationRuntimeTest,AgentOrchestrationControllerTest,OrchestrationControllerTest
```

Expected:

- invalid path segments still fail.
- workflow/editor persisted script-like values still render inert/escaped.
- cross-agent assignment lifecycle mutation still fails without changing assignment state.

4. Run full automated validation:

```bash
mvn test
```

5. Run bounded startup smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Expected: Spring Boot starts without missing Spring Security classes, without generated default password logs, and without requiring `magenta.alpha-access`.

## 9. Playwright And Browser Validation Expectations

Browser validation must be run by a separate validation agent/subagent, not inline with the main implementation workflow.

Focused browser validation steps:

1. Start the app on a fixed local port with a fresh SQLite DB and no alpha credential env vars:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta-remove-alpha-auth-playwright.sqlite?foreign_keys=true'
```

2. Run the focused Playwright harness:

```bash
MAGENTA_PLAYWRIGHT_BASE_URL=http://localhost:18080 npx playwright test tests/playwright/public-alpha-harness.spec.js
```

Expected:

- No Playwright config uses `httpCredentials`.
- Pages load without a browser Basic auth prompt.
- POST/PUT/DELETE interactions used by the focused harness do not fail with `401` or `403` from auth/CSRF.
- Plan/workflow HTMX interactions still update the page.
- If a neutral shell helper is kept, operational `400` fragments that are designed for HTMX still swap into their targets.
- No `alpha-security.js` network request appears.

If Playwright cannot run because of local browser/runtime issues, report the exact blocker and do not mark browser validation complete.

## 10. Startup Smoke

Run after focused tests and before sign-off:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Acceptance criteria:

- Application context starts.
- No Spring Security default password is printed.
- No bean creation failure references `AlphaSecurityConfiguration`, `SecurityFilterChain`, `UserDetailsService`, `CsrfToken`, or `magenta.alpha-access`.
- No required env var references `MAGENTA_ALPHA_USERNAME` or `MAGENTA_ALPHA_PASSWORD`.

## 11. `.internal-dev` Closeout Requirements

After implementation and validation:

- Add a changelog entry in `.internal-dev/changelogs/` summarizing auth removal, docs/test updates, and preserved non-auth safety features.
- Add knowledge in `.internal-dev/knowledge/` only if the implementation discovers reusable guidance beyond this plan, for example a durable "open alpha security posture" note. Do not duplicate docs just to satisfy process.
- Log out-of-scope bugs immediately in `.internal-dev/bugs/` if discovered.
- Do not create deferred notes unless the user explicitly approves a deferred future idea.
- Move this finalized plan to `.internal-dev/plans/.archive/remove-alpha-authentication/` after the implementation is complete and accepted.
- Commit implementation and `.internal-dev` updates together on the dedicated branch.

Suggested commit message:

```text
Remove alpha auth and CSRF gate
```

## 12. Handoff Checklist

- [ ] Work is on `remove-alpha-authentication` or a similarly dedicated branch/worktree.
- [ ] Existing dirty user changes in the original worktree were not overwritten.
- [ ] `pom.xml` no longer includes Spring Security runtime/test dependencies.
- [ ] `AlphaSecurityConfiguration.java` is deleted.
- [ ] `alpha-security.js` is deleted.
- [ ] `magenta.alpha-access` and `MAGENTA_ALPHA_*` are removed from active config/docs/tests.
- [ ] Frontend and orchestration shells no longer load `/js/alpha-security.js`.
- [ ] Any retained shell helper has a neutral name and contains no auth/CSRF behavior.
- [ ] `AlphaSecurityConfigurationTest` is deleted.
- [ ] Public route tests run unsafe mutations without `httpBasic()` and without `csrf()`.
- [ ] Playwright config/harness no longer uses HTTP credentials or CSRF tokens.
- [ ] Docs reflect open-alpha access and preserved non-auth safety posture.
- [ ] `PlainPathSegmentValidator` and its service/test call sites remain.
- [ ] Workflow/editor XSS-safe rendering tests remain.
- [ ] Agent-scoped assignment lifecycle tests remain.
- [ ] Residue `rg` scan has no active auth/CSRF hits outside historical `.internal-dev` records.
- [ ] Focused tests pass.
- [ ] `mvn test` passes.
- [ ] Bounded Spring Boot startup smoke passes.
- [ ] Separate validation agent completes focused Playwright validation against a live app.
- [ ] `.internal-dev` changelog/closeout is complete.
- [ ] Plan is archived only after implementation is accepted.
- [ ] Git commit includes implementation plus `.internal-dev` closeout.

## 13. Risks And Ambiguities To Verify

- Current `alpha-security.js` also carries generic HTMX non-2xx fragment swapping. Removing it without replacing that small non-auth behavior may regress visible error fragments on operational pages. Prefer a neutral helper if focused browser validation shows this behavior is still needed.
- `PublicApiRouteBindingTest` was added after the security branch and currently imports Spring Security test helpers. Removing the dependency will expose every lingering helper import as a compile failure; fix the tests instead of restoring the dependency.
- Current docs were updated to describe alpha auth as the global API posture. They must be updated in the same implementation so future agents do not reintroduce the gate from stale docs.
- Historical `.internal-dev/changelogs` and knowledge files legitimately record what was previously added. Treat those as history, not active residue, unless the user asks for historical correction.
- The current branch includes later frontend changes where `src/main/resources/static/js/orchestration/workflows.js` is already deleted and workflow XSS safety moved into server-rendered workflow editor tests. Do not try to resurrect that deleted JS file while removing auth.
