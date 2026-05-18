const { test, expect, request } = require("playwright/test");

const PUBLIC_ROUTES = [
  { path: "/", marker: "Magenta" },
  { path: "/chat", selector: "[data-chat-root='true']" },
  { path: "/dashboard", selector: "[data-orchestration-page='dashboard']" },
  { path: "/plans", selector: "[data-orchestration-page='plans']" },
  { path: "/workflows", selector: "[data-orchestration-page='workflows']" },
  { path: "/jobs", selector: "[data-orchestration-page='jobs']" },
  { path: "/projects", selector: "[data-orchestration-page='projects']" },
  { path: "/inbox", selector: "[data-orchestration-page='inbox']" },
  { path: "/outputs", selector: "[data-orchestration-page='outputs']" },
  { path: "/agents", selector: "[data-orchestration-page='agents']" },
  { path: "/settings", selector: "[data-orchestration-page='settings']" },
];

const STATIC_ASSET_PATTERN = /\/(?:css|js|webjars)\//;
const EXPECTED_NON_2XX = [
  { method: "POST", path: "/plans/_editor/_draft", status: 401, reason: "anonymous unsafe mutation auth gate" },
];

test.beforeEach(async ({ page }, testInfo) => {
  testInfo.consoleMessages = [];
  testInfo.pageErrors = [];
  testInfo.networkFailures = [];
  testInfo.unexpectedResponses = [];
  testInfo.expectedResponses = [];

  page.on("console", (message) => {
    const type = message.type();
    if (["error", "warning"].includes(type)) {
      testInfo.consoleMessages.push({
        type,
        text: message.text(),
        location: message.location(),
      });
    }
  });
  page.on("pageerror", (error) => {
    testInfo.pageErrors.push({ message: error.message, stack: error.stack });
  });
  page.on("requestfailed", (request) => {
    testInfo.networkFailures.push({
      method: request.method(),
      url: request.url(),
      resourceType: request.resourceType(),
      failure: request.failure(),
    });
  });
  page.on("response", (response) => {
    const request = response.request();
    const status = response.status();
    const url = response.url();
    const assetFailure = status >= 400
      && (STATIC_ASSET_PATTERN.test(url) || ["script", "stylesheet", "image", "font"].includes(request.resourceType()));
    const serverFailure = status >= 500;
    if (assetFailure || serverFailure) {
      testInfo.unexpectedResponses.push({
        method: request.method(),
        url,
        status,
        resourceType: request.resourceType(),
      });
    }
  });
});

test.afterEach(async ({}, testInfo) => {
  await attachDiagnostics(testInfo);
  expect.soft(testInfo.pageErrors, "unexpected page errors").toEqual([]);
  expect.soft(testInfo.networkFailures, "unexpected failed browser requests").toEqual([]);
  expect.soft(testInfo.unexpectedResponses, "unexpected 500/static asset failures").toEqual([]);
});

test("public page reachability matrix stays renderable", async ({ page }) => {
  for (const route of PUBLIC_ROUTES) {
    await page.goto(route.path);
    await expect(page.locator("body")).toBeVisible();
    if (route.selector) {
      await expect(page.locator(route.selector)).toBeVisible();
    }
    if (route.marker) {
      await expect(page.locator("body")).toContainText(route.marker);
    }
  }

  const agentId = await firstAgentId(page);
  await page.goto(`/agents/${agentId}`);
  await expect(page.locator(`[data-orchestration-page='agents'][data-agent-id='${agentId}']`)).toBeVisible();
  await expect(page.locator("#agent-tab-panel")).toBeVisible();
});

test("mobile agent shell keeps content readable at phone width", async ({ page }) => {
  const agentId = await firstAgentId(page);
  await page.setViewportSize({ width: 390, height: 780 });
  await page.goto(`/agents/${agentId}`);
  await expect(page.locator("#agent-tab-panel")).toBeVisible();

  const layout = await page.evaluate(() => {
    const container = document.querySelector(".main-container.has-sidebar");
    const wrapper = document.querySelector(".content-wrapper");
    const content = document.querySelector("#content-area");
    const sidebar = document.querySelector(".main-sidebar");
    const rect = (element) => element ? element.getBoundingClientRect() : null;
    return {
      viewportWidth: window.innerWidth,
      documentScrollWidth: document.documentElement.scrollWidth,
      gridTemplateColumns: container ? getComputedStyle(container).gridTemplateColumns : null,
      gridTemplateAreas: container ? getComputedStyle(container).gridTemplateAreas : null,
      sidebarGridArea: sidebar ? getComputedStyle(sidebar).gridArea : null,
      wrapper: rect(wrapper),
      content: rect(content),
    };
  });

  expect(layout.content?.width, "content area should use the mobile viewport").toBeGreaterThanOrEqual(340);
  expect(layout.wrapper?.width, "content wrapper should use the mobile viewport").toBeGreaterThanOrEqual(340);
  expect(layout.documentScrollWidth, "mobile shell should not force horizontal overflow").toBeLessThanOrEqual(410);
  expect(layout.gridTemplateAreas, "mobile shell should use content-only grid area").toContain("content");
  expect(layout.sidebarGridArea, "off-canvas sidebar should not create implicit layout tracks").toBe("auto");
});

test("plan editor HTMX mutation persists through UI and API", async ({ page }) => {
  const title = `PW Harness Plan ${Date.now()}`;
  const goal = "Prove the checked-in focused harness catches plan persistence regressions.";
  const summary = "Playwright harness plan mutation persistence";

  await page.goto("/plans");
  await page.getByRole("button", { name: "New Plan", exact: true }).click();
  await expect(page.locator("#plan-title")).toHaveValue("Untitled Plan");

  const editor = page.locator("#plan-editor-container");
  const planId = await idFromHxAttribute(
    editor.locator("form[hx-put^='/plans/_editor/']"),
    "hx-put",
    "/plans/_editor/"
  );
  await editor.locator("#plan-title").fill(title);
  await editor.locator("#plan-summary").fill(summary);
  await editor.locator("#plan-goal").fill(goal);
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes(`/plans/_editor/${planId}`) && response.request().method() === "PUT"
    ),
    editor.getByRole("button", { name: "Save" }).click(),
  ]);
  await expect(editor.locator("#plan-title")).toHaveValue(title);

  const apiPlan = await apiJson(page, `/api/plans/${planId}`);
  expect(apiPlan.id).toBe(planId);
  expect(apiPlan.title).toBe(title);
  expect(apiPlan.summary).toBe(summary);
  expect(apiPlan.goal).toBe(goal);

  await page.reload();
  await page.locator("#plan-filter").fill(title);
  await expect(page.locator("#plan-list")).toContainText(title);
});

test("workflow HTMX critical flow saves, validates, and separates submit validation", async ({ page }) => {
  const title = `PW Harness Workflow ${Date.now()}`;

  await page.goto("/workflows");
  await page.locator(".browser-sidebar-header button[hx-post='/workflows/_editor/_draft'][hx-target='#workflow-editor-container']").click();
  const editor = page.locator("#workflow-editor-container");
  await expect(editor.locator("#workflow-title")).toHaveValue("Untitled Workflow");

  const workflowId = await idFromHxAttribute(
    editor.locator("form[hx-put^='/workflows/_editor/']"),
    "hx-put",
    "/workflows/_editor/"
  );

  await editor.locator("#workflow-title").fill(title);
  await editor.locator("#workflow-summary").fill("Focused harness workflow draft");
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes(`/workflows/_editor/${workflowId}`) && response.request().method() === "PUT"
    ),
    editor.getByRole("button", { name: "Save" }).click(),
  ]);
  await expect(editor.locator("#workflow-title")).toHaveValue(title);

  await editor.getByRole("button", { name: "Add", exact: true }).click();
  await expect(editor.locator("#workflow-nodes-section")).toContainText("node_1");

  await editor.getByRole("button", { name: "Validate" }).click();
  await expect(editor.locator("#workflow-validation-result")).toContainText(/Valid: no errors found\.|ERROR:/);

  await editor.getByRole("button", { name: "Submit to Agent" }).click();
  await expect(editor.locator("#workflow-submit-container")).toBeVisible();
  await expect(editor.locator("#workflow-submit-container")).not.toContainText("Graph Composer");

  const apiWorkflow = await apiJson(page, `/api/workflows/${workflowId}`);
  expect(apiWorkflow.id).toBe(workflowId);
  expect(apiWorkflow.title).toBe(title);
  expect(apiWorkflow.nodes.length).toBeGreaterThanOrEqual(1);
});

test("unsafe anonymous mutation is an expected non-2xx validation path", async ({ baseURL }, testInfo) => {
  const csrfContext = await request.newContext({ baseURL, httpCredentials: alphaCredentials() });
  const csrfResponse = await csrfContext.get("/plans");
  expect(csrfResponse.ok(), "GET /plans should issue the CSRF cookie").toBe(true);
  const csrfToken = await xsrfToken(csrfContext);
  await csrfContext.dispose();

  const context = await request.newContext({
    baseURL,
    httpCredentials: {
      username: alphaCredentials().username,
      password: "invalid-alpha-password",
    },
  });
  const response = await context.post("/plans/_editor/_draft", {
    headers: {
      "HX-Request": "true",
      "X-XSRF-TOKEN": csrfToken,
      "Cookie": `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
    },
  });
  const observed = {
    method: "POST",
    path: "/plans/_editor/_draft",
    status: response.status(),
    reason: "anonymous unsafe mutation auth gate",
  };
  testInfo.expectedResponses.push(observed);
  expect(observed).toMatchObject(EXPECTED_NON_2XX[0]);
  await context.dispose();
});

async function firstAgentId(page) {
  const agents = await apiJson(page, "/api/agents");
  expect(Array.isArray(agents), "GET /api/agents should return an agent list").toBe(true);
  expect(agents.length, "live validation requires at least one seeded agent").toBeGreaterThan(0);
  return agents[0].id;
}

async function apiJson(page, path) {
  const response = await page.request.get(path);
  expect(response.ok(), `${path} should return 2xx`).toBe(true);
  return await response.json();
}

async function idFromHxAttribute(locator, attribute, prefix) {
  const value = await locator.getAttribute(attribute);
  expect(value, `${attribute} should include ${prefix}<id>`).toContain(prefix);
  const id = value.slice(prefix.length).split("/")[0];
  expect(id, "persisted draft id should be non-empty").toBeTruthy();
  return id;
}

function alphaCredentials() {
  return {
    username: process.env.MAGENTA_ALPHA_USERNAME || "alpha",
    password: process.env.MAGENTA_ALPHA_PASSWORD || "test-alpha-password",
  };
}

async function xsrfToken(context) {
  const cookies = await context.storageState().then((state) => state.cookies);
  const token = cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value;
  expect(token, "XSRF-TOKEN cookie should be issued before auth-gate probe").toBeTruthy();
  return token;
}

async function attachDiagnostics(testInfo) {
  const diagnostics = {
    consoleMessages: testInfo.consoleMessages ?? [],
    pageErrors: testInfo.pageErrors ?? [],
    networkFailures: testInfo.networkFailures ?? [],
    unexpectedResponses: testInfo.unexpectedResponses ?? [],
    expectedResponses: testInfo.expectedResponses ?? [],
  };
  await testInfo.attach("browser-diagnostics.json", {
    body: JSON.stringify(diagnostics, null, 2),
    contentType: "application/json",
  });
}
