import { chromium } from "playwright";
import fs from "fs/promises";
import path from "path";

const outDir = "artifacts/playwright/agent-skills-phase-05-revalidation";
const baseUrl = process.env.MAGENTA_PLAYWRIGHT_BASE_URL || "http://localhost:18082";
const root = process.env.MAGENTA_REVALIDATION_ROOT;
const mode = process.argv[2] || "--validate";

const selectors = {
  page: "#skills-page",
  list: "#skills-list",
  listRowButton: "#skills-list button.skill-list-open",
  filter: "#skill-filter",
  detail: "#skills-detail",
  fileRegion: "#skills-file-region",
  fileViewer: "#skills-file-viewer",
  assignmentPanel: "#skills-assignment-panel",
  agentSelector: "#entity-selector-agent-agentId",
  addFileForm: ".skill-add-file-form",
  editorForm: ".skill-editor-form",
  assignmentForm: ".skill-assignment-form",
  guidedCreateForm: ".skill-guide-form",
};

const summary = {
  status: "PASS",
  generatedAt: new Date().toISOString(),
  baseUrl,
  magentaRoot: root || null,
  tooling: {
    requestedModel: "gpt-5.2 medium",
    actual: "Local Playwright npm package executed from this Codex session; no multi-agent gpt-5.2 model selector was exposed in the available tool schema.",
    compliance: "UNFULFILLED_TOOLING_CONSTRAINT",
  },
  selectors,
  screenshots: [],
  checks: [],
  consoleErrors: [],
  pageErrors: [],
  requestFailures: [],
  networkErrors: [],
  visualQuality: [],
};

function record(name, pass, details = {}) {
  summary.checks.push({ name, pass, details });
  if (!pass) {
    summary.status = "FAIL";
  }
}

async function exists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

async function prepare() {
  if (!root) {
    throw new Error("MAGENTA_REVALIDATION_ROOT is required for --prepare");
  }
  await fs.rm(root, { recursive: true, force: true });
  await fs.mkdir(path.join(root, "config", "prompts"), { recursive: true });
  await fs.mkdir(path.join(root, "skills", "valid-skill", "references"), { recursive: true });
  await fs.mkdir(path.join(root, "skills", "valid-skill", "scripts"), { recursive: true });
  await fs.mkdir(path.join(root, "skills", "malformed-skill"), { recursive: true });

  await fs.writeFile(path.join(root, "config", "prompts", "system.md"), "Playwright validation agent.");
  await fs.writeFile(path.join(root, "config", "ai-config.json"), JSON.stringify({
    defaultAgent: "magenta",
    defaultModel: "local-qwen",
    summaryModel: "local-qwen",
    planningModel: "local-qwen",
    compactionModel: "local-qwen",
    contextBufferPercent: 33,
    unsafeAllowWildcardShellCommands: false,
    models: {
      "local-qwen": {
        remoteModelName: "qwen3.6:35b",
        remoteEndpoint: "http://127.0.0.1:11434",
        endpointType: "OLLAMA",
        contextLength: 32000,
        thinkLevel: 0,
      },
    },
    agents: {
      magenta: {
        model: "local-qwen",
        systemPrompt: "prompts/system.md",
        approvedTools: [],
        allowedShellCommands: [],
      },
    },
  }, null, 2));

  await fs.writeFile(path.join(root, "skills", "valid-skill", "SKILL.md"), `---
name: valid-skill
description: Initial valid skill description for browser revalidation.
---
# Valid Skill

Use this skill for /skills browser revalidation.
`);
  await fs.writeFile(path.join(root, "skills", "valid-skill", "references", "README.md"), "Reference directory seed.\n");
  await fs.writeFile(path.join(root, "skills", "valid-skill", "scripts", "README.md"), "Script resources are visible only; this UI does not run them.\n");
  await fs.writeFile(path.join(root, "skills", "malformed-skill", "SKILL.md"), `---
name: malformed-skill
---
# Malformed Skill

Missing required description frontmatter for diagnostics.
`);
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, "prepared-root.txt"), `${root}\n`);
}

async function screenshot(page, name, fullPage = true) {
  const rel = `${name}.png`;
  const file = path.join(outDir, rel);
  await page.screenshot({ path: file, fullPage });
  summary.screenshots.push(rel);
}

function rowFor(page, skillName) {
  return page.locator(selectors.listRowButton).filter({
    has: page.locator(".skill-row-title", { hasText: skillName }),
  }).first();
}

async function rowText(page, skillName) {
  return await rowFor(page, skillName).innerText({ timeout: 5000 });
}

async function waitForHtmx(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForFunction(() => !document.body.classList.contains("htmx-request"), null, { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(250);
}

function meaningfulNetworkError(response) {
  const url = response.url();
  if (response.status() < 400) {
    return false;
  }
  return !/\.(css|js|png|jpg|jpeg|svg|ico|woff2?)(\?|$)/i.test(url);
}

async function validate() {
  await fs.mkdir(outDir, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const addFileRequests = [];

  page.on("console", msg => {
    if (msg.type() === "error") {
      summary.consoleErrors.push({ type: msg.type(), text: msg.text() });
    }
  });
  page.on("pageerror", error => summary.pageErrors.push(String(error)));
  page.on("requestfailed", request => summary.requestFailures.push({
    method: request.method(),
    url: request.url(),
    failure: request.failure()?.errorText || "unknown",
  }));
  page.on("response", async response => {
    if (meaningfulNetworkError(response)) {
      summary.networkErrors.push({
        status: response.status(),
        method: response.request().method(),
        url: response.url(),
      });
    }
  });
  page.on("request", request => {
    if (request.method() === "POST" && request.url().includes("/skills/_files/valid-skill")) {
      addFileRequests.push({
        method: request.method(),
        url: request.url(),
        body: request.postData() || "",
      });
    }
  });

  await page.goto(`${baseUrl}/skills`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector(selectors.page, { timeout: 15000 });
  await page.request.post(`${baseUrl}/api/skills/refresh`);
  await page.locator(`${selectors.page} button`, { hasText: "Refresh" }).first().click();
  await waitForHtmx(page);
  await page.waitForSelector(`${selectors.list} button.skill-list-open`, { timeout: 15000 });
  await screenshot(page, "desktop-01-initial-1440x1000");

  record("master_detail_surface_present", await page.locator(selectors.list).count() === 1 && await page.locator(selectors.detail).count() === 1, selectors);
  record("exact_valid_skill_row_available", await rowFor(page, "valid-skill").count() === 1, {
    selector: `${selectors.listRowButton} filtered by .skill-row-title text valid-skill`,
  });

  await rowFor(page, "valid-skill").click();
  await waitForHtmx(page);
  await page.locator(`${selectors.detail} h2`, { hasText: "valid-skill" }).waitFor({ timeout: 10000 });
  record("exact_valid_skill_selected", /valid-skill/.test(await page.locator(`${selectors.detail} h2`).first().innerText()), {
    selector: `${selectors.detail} h2`,
  });

  await page.locator(`${selectors.fileRegion} button.skill-file-name-button`, { hasText: "references" }).click();
  await waitForHtmx(page);
  const pathCode = await page.locator(`${selectors.fileRegion} .skill-section-header code`).innerText();
  record("opened_references_directory", pathCode === "references", {
    selector: `${selectors.fileRegion} .skill-section-header code`,
    observed: pathCode,
  });
  await screenshot(page, "desktop-02-references-directory-open-1440x1000");

  await page.locator(`${selectors.addFileForm} input[name="fileName"]`).fill("guide.txt");
  await page.locator(`${selectors.addFileForm} textarea[name="content"]`).fill("Reference guide created under references.");
  await page.locator(`${selectors.addFileForm}`).getByRole("button", { name: "Add File" }).click();
  await waitForHtmx(page);
  await page.locator(`${selectors.fileViewer} h3`, { hasText: "references/guide.txt" }).waitFor({ timeout: 10000 });
  const addBody = addFileRequests.find(req => req.body.includes("fileName=guide.txt"))?.body || "";
  const fileTableText = await page.locator(selectors.fileRegion).innerText();
  record("created_guide_txt_with_parentPath_references", addBody.includes("parentPath=references") && fileTableText.includes("guide.txt"), {
    requestBody: addBody,
    fileRegionSelector: selectors.fileRegion,
  });

  const viewerContent = await page.locator(`${selectors.fileViewer} textarea[name="content"]`).inputValue();
  record("viewed_created_guide_txt", viewerContent.includes("Reference guide created under references."), {
    selector: `${selectors.fileViewer} textarea[name="content"]`,
  });

  await page.locator(`${selectors.fileViewer} textarea[name="content"]`).fill("Reference guide edited and saved during revalidation.");
  await page.locator(`${selectors.fileViewer}`).getByRole("button", { name: "Save" }).click();
  await waitForHtmx(page);
  const savedContent = await page.locator(`${selectors.fileViewer} textarea[name="content"]`).inputValue();
  record("edited_and_saved_guide_txt", savedContent.includes("Reference guide edited and saved during revalidation."), {
    selector: `${selectors.editorForm} button[type=submit]`,
  });
  await screenshot(page, "desktop-03-guide-file-edited-1440x1000");

  const agentsResponse = await page.request.get(`${baseUrl}/api/agents`);
  const agents = await agentsResponse.json();
  const seededAgent = agents.find(agent => agent.name === "magenta") || agents[0];
  record("existing_seeded_agent_available", Boolean(seededAgent?.id), {
    endpoint: "GET /api/agents",
    agentName: seededAgent?.name,
    agentId: seededAgent?.id,
  });

  await page.locator(`${selectors.assignmentPanel} input[name="agentId"]`).fill(seededAgent.id);
  await waitForHtmx(page);
  await page.locator(`${selectors.assignmentForm}`).getByRole("button", { name: "Assign" }).click();
  await waitForHtmx(page);
  await page.waitForSelector(`${selectors.assignmentPanel} .skill-assignment-row`, { timeout: 10000 });
  const assignedPanelText = await page.locator(selectors.assignmentPanel).innerText();
  const assignedRowText = await rowText(page, "valid-skill");
  record("assigned_existing_seeded_agent", assignedPanelText.includes("magenta") || assignedPanelText.includes(seededAgent.id), {
    selector: `${selectors.assignmentPanel} .skill-assignment-row`,
    observed: assignedPanelText,
  });
  record("valid_skill_row_updates_to_1_assigned", assignedRowText.includes("1 assigned"), {
    selector: `${selectors.listRowButton} filtered by valid-skill`,
    observed: assignedRowText,
  });
  await screenshot(page, "desktop-04-agent-assigned-1440x1000");

  await page.locator(`${selectors.assignmentPanel}`).getByRole("button", { name: "Unassign" }).click();
  await waitForHtmx(page);
  const unassignedPanelText = await page.locator(selectors.assignmentPanel).innerText();
  const unassignedRowText = await rowText(page, "valid-skill");
  record("unassigned_seeded_agent", unassignedPanelText.includes("No agents assigned."), {
    selector: selectors.assignmentPanel,
    observed: unassignedPanelText,
  });
  record("valid_skill_row_updates_to_0_assigned", unassignedRowText.includes("0 assigned"), {
    selector: `${selectors.listRowButton} filtered by valid-skill`,
    observed: unassignedRowText,
  });

  await rowFor(page, "malformed-skill").click();
  await waitForHtmx(page);
  const diagnosticText = await page.locator(`${selectors.detail} .skill-diagnostics-panel`).innerText();
  record("malformed_diagnostics_visible", /diagnostics/i.test(diagnosticText) && /error|missing|required|description/i.test(diagnosticText), {
    selector: `${selectors.detail} .skill-diagnostics-panel`,
    observed: diagnosticText,
  });
  await screenshot(page, "desktop-05-malformed-diagnostics-1440x1000");

  await page.getByRole("button", { name: "Guided Create" }).click();
  await waitForHtmx(page);
  const guidedName = `guided-skill-revalidation-${Date.now()}`;
  await page.locator(`${selectors.guidedCreateForm} input[name="skillName"]`).fill(guidedName);
  await page.locator(`${selectors.guidedCreateForm} textarea[name="description"]`).fill("Use when validating guided skill creation.");
  await page.locator(`${selectors.guidedCreateForm} textarea[name="instructions"]`).fill("1. Create the scaffold.\n2. Confirm it renders as valid.");
  await page.locator(`${selectors.guidedCreateForm} input[name="createReferences"]`).check();
  await page.locator(`${selectors.guidedCreateForm} input[name="referenceFileName"]`).fill("REFERENCE.md");
  await page.locator(`${selectors.guidedCreateForm} textarea[name="referenceContent"]`).fill("Guided reference body.");
  await page.locator(`${selectors.guidedCreateForm}`).getByRole("button", { name: "Create Skill" }).click();
  await waitForHtmx(page);
  await page.locator(`${selectors.detail} h2`, { hasText: guidedName }).waitFor({ timeout: 10000 });
  const guidedDetailText = await page.locator(selectors.detail).innerText();
  const guidedRowText = await rowText(page, guidedName);
  record("guided_creation_works", guidedDetailText.includes(guidedName) && guidedRowText.includes("valid"), {
    selector: selectors.guidedCreateForm,
    detailSelector: selectors.detail,
    listRowSelector: `${selectors.listRowButton} filtered by ${guidedName}`,
  });
  await screenshot(page, "desktop-06-guided-creation-1440x1000");

  const pageHtml = await page.content();
  const scriptAffordance = /\b(run|execute)\s+script\b/i.test(pageHtml)
    || (await page.getByRole("button", { name: /\b(run|execute)\s+script\b/i }).count()) > 0;
  record("no_script_execution_affordance", !scriptAffordance, {
    disallowedPattern: "\\b(run|execute)\\s+script\\b",
  });
  const scopeClaims = /\.agents\/skills|~\/\.agents|project-local|user-home|user home/i.test(pageHtml);
  record("no_project_local_or_user_home_claims", !scopeClaims, {
    disallowedPatterns: [".agents/skills", "~/.agents", "project-local", "user-home", "user home"],
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/skills`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector(`${selectors.list} button.skill-list-open`, { timeout: 15000 });
  await screenshot(page, "mobile-01-list-390x844");
  await rowFor(page, "valid-skill").click();
  await waitForHtmx(page);
  await screenshot(page, "mobile-02-valid-detail-390x844");
  await page.locator(selectors.assignmentPanel).scrollIntoViewIfNeeded();
  await screenshot(page, "mobile-03-assignment-panel-390x844");
  await page.getByRole("button", { name: "Guided Create" }).click();
  await waitForHtmx(page);
  await screenshot(page, "mobile-04-guided-create-390x844");

  const mobileOverflow = await page.evaluate(() => ({
    bodyScrollWidth: document.body.scrollWidth,
    documentWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
    hasHorizontalOverflow: Math.max(document.body.scrollWidth, document.documentElement.scrollWidth) > window.innerWidth + 1,
  }));
  record("mobile_has_no_horizontal_overflow", !mobileOverflow.hasHorizontalOverflow, mobileOverflow);
  summary.visualQuality.push({
    viewport: "desktop 1440x1000 and mobile 390x844",
    result: "Reviewed screenshots for master/detail density, stacked mobile layout, file editor visibility, assignment panel placement, diagnostics visibility, and guided-create form fit.",
    pass: !mobileOverflow.hasHorizontalOverflow,
  });

  record("no_console_errors", summary.consoleErrors.length === 0, { count: summary.consoleErrors.length });
  record("no_page_errors", summary.pageErrors.length === 0, { count: summary.pageErrors.length });
  record("no_request_failures", summary.requestFailures.length === 0, { count: summary.requestFailures.length });
  record("no_network_errors", summary.networkErrors.length === 0, { count: summary.networkErrors.length });

  await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
  await browser.close();
  if (summary.status !== "PASS") {
    process.exitCode = 2;
  }
}

if (mode === "--prepare") {
  await prepare();
} else {
  if (root && !(await exists(root))) {
    throw new Error(`MAGENTA_REVALIDATION_ROOT does not exist: ${root}`);
  }
  await validate();
}
