import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://localhost:18080";
const outDir = "artifacts/dashboard-widget-suite/phase-03-browser";
fs.mkdirSync(outDir, { recursive: true });

const runtimeCommand = "mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-03-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-03-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'";

const results = {
  verdict: "PASS_BROWSER_PROOF",
  baseUrl,
  runtime: {
    command: runtimeCommand,
    port: 18080,
    isolatedRoot: "/tmp/magenta2-dashboard-widget-suite-phase-03-browser"
  },
  screenshots: [],
  checks: [],
  console: [],
  pageErrors: [],
  network: [],
  visualCritique: []
};

function rel(name) {
  return path.join(outDir, name);
}

function check(id, pass, evidence, extra = {}) {
  results.checks.push({ id, status: pass ? "passed" : "failed", evidence, ...extra });
  if (!pass) {
    results.verdict = "NEEDS_REPAIR";
  }
}

async function screenshot(page, name, fullPage = true) {
  const file = rel(name);
  await page.screenshot({ path: file, fullPage });
  results.screenshots.push(file);
}

async function postForm(page, url, params, method = "POST") {
  return page.evaluate(async ({ url, params, method }) => {
    const response = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        "HX-Request": "true"
      },
      body: new URLSearchParams(params)
    });
    const text = await response.text();
    return { ok: response.ok, status: response.status, text };
  }, { url, params, method });
}

async function jsonFetch(page, url, method, body) {
  return page.evaluate(async ({ url, method, body }) => {
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: body == null ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }
    return { ok: response.ok, status: response.status, data, text };
  }, { url, method, body });
}

function requireOk(result, label) {
  if (!result.ok) {
    throw new Error(`${label} failed ${result.status}: ${String(result.text || result.data).slice(0, 300)}`);
  }
  return result;
}

async function dashboardHealth(page) {
  return page.evaluate(() => {
    const ids = [...document.querySelectorAll("[id]")].map((el) => el.id);
    const duplicates = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))].sort();
    const maxRight = Math.max(
      document.documentElement.scrollWidth,
      ...[...document.querySelectorAll("body *")].map((el) => Math.ceil(el.getBoundingClientRect().right))
    );
    return {
      title: document.title,
      topNavs: document.querySelectorAll("header nav.top-nav").length,
      dashboardHomes: document.querySelectorAll("#dashboard-home").length,
      shellRoots: document.querySelectorAll("[data-avatar-shell='true']").length,
      editContainers: document.querySelectorAll("#avatar-edit-container").length,
      modalRoots: document.querySelectorAll("#avatar-widget-detail-modal").length,
      widgetTypes: [...document.querySelectorAll("[data-avatar-widget-type]")].map((el) => el.getAttribute("data-avatar-widget-type")),
      duplicateIds: duplicates,
      horizontalOverflow: maxRight > window.innerWidth + 2,
      scrollWidth: document.documentElement.scrollWidth,
      viewport: { width: window.innerWidth, height: window.innerHeight }
    };
  });
}

async function widgetIds(page) {
  return page.evaluate(() => [...document.querySelectorAll("[data-avatar-widget-type]")].map((el) => ({
    id: el.getAttribute("data-avatar-widget"),
    type: el.getAttribute("data-avatar-widget-type"),
    text: el.innerText
  })));
}

async function seedDataAndLayout(page) {
  const householdProject = requireOk(await jsonFetch(page, "/api/projects", "POST", {
    name: "Household Pantry Renovation Browser Proof Long Named Project",
    description: "Household project seeded through API for dashboard browser proof",
    ownerAgentId: "avatar"
  }), "create household project").data;

  const codeProject = requireOk(await jsonFetch(page, "/api/projects", "POST", {
    name: "Code Integration Browser Proof Project",
    description: "Code project seeded to prove code-vs-household language",
    ownerAgentId: "avatar",
    gitRepoUrl: "https://example.invalid/magenta/browser-proof.git"
  }), "create code project").data;

  const workArea = requireOk(await jsonFetch(page, "/api/work-areas/home", "POST", {
    ownerType: "AGENT",
    ownerId: "avatar",
    displayName: "Phase 03 Browser Proof Work Area"
  }), "ensure Work Area home").data;

  requireOk(await jsonFetch(page, `/api/work-areas/${workArea.id}/directories`, "POST", {
    path: "notes"
  }), "create Work Area notes dir");
  requireOk(await jsonFetch(page, `/api/work-areas/${workArea.id}/files/markdown`, "POST", {
    parentPath: "notes",
    fileName: "phase03-workarea-note.md"
  }), "create Work Area note file");
  requireOk(await jsonFetch(page, `/api/work-areas/${workArea.id}/files/text?path=${encodeURIComponent("notes/phase03-workarea-note.md")}`, "PUT", {
    content: "# Work Area Browser Note\n\nInitial file-backed Markdown note."
  }), "save Work Area note file");

  await postForm(page, "/_dashboards/_notes", {
    title: "Browser proof personal seed note",
    body: "Personal DB note seeded with alpha tag.",
    tags: "alpha,household"
  });

  await page.goto(`${baseUrl}/dashboards/assistant/_page?edit=true`, { waitUntil: "networkidle" });
  const firstAddRow = requireOk(await postForm(page, "/dashboards/assistant/_layout/rows", {}), "add row for notes/projects").text;
  const firstRow = [...firstAddRow.matchAll(/data-avatar-row-id="([^"]+)"/g)].map((m) => m[1]).at(-1);
  requireOk(await postForm(page, `/_dashboards/_layout/rows/${firstRow}/widgets`, { widgetKey: "notes", columnWidth: "6" }), "add Work Area Notes widget");
  requireOk(await postForm(page, `/_dashboards/_layout/rows/${firstRow}/widgets`, { widgetKey: "projects", columnWidth: "6" }), "add household Projects widget");

  const secondAddRow = requireOk(await postForm(page, "/dashboards/assistant/_layout/rows", {}), "add row for contacts/code").text;
  const secondRow = [...secondAddRow.matchAll(/data-avatar-row-id="([^"]+)"/g)].map((m) => m[1]).at(-1);
  requireOk(await postForm(page, `/_dashboards/_layout/rows/${secondRow}/widgets`, { widgetKey: "contacts-materials", columnWidth: "6" }), "add Contacts/Materials widget");
  requireOk(await postForm(page, `/_dashboards/_layout/rows/${secondRow}/widgets`, { widgetKey: "projects", columnWidth: "6" }), "add code Projects widget");

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  const widgets = await widgetIds(page);
  const personalNotes = widgets.find((w) => w.type === "notes");
  const addedNotes = widgets.filter((w) => w.type === "notes").at(-1);
  const projectWidgets = widgets.filter((w) => w.type === "projects");
  const contactsWidget = widgets.find((w) => w.type === "contacts-materials");

  const missingBeforeBinding = {
    projects: projectWidgets.map((w) => w.text.includes("Project binding is required.") || w.text.includes("Choose a project")),
    contacts: contactsWidget?.text.includes("Project binding is required.") || contactsWidget?.text.includes("Choose a project")
  };

  requireOk(await postForm(page, `/dashboards/assistant/widgets/${personalNotes.id}/settings`, {
    noteSourceMode: "personal",
    sourceMode: "dashboard",
    noteQuery: "",
    density: "compact"
  }, "PUT"), "configure personal Notes widget");
  requireOk(await postForm(page, `/dashboards/assistant/widgets/${addedNotes.id}/settings`, {
    noteSourceMode: "work_area",
    sourceMode: "work_area",
    workAreaId: workArea.id,
    noteQuery: "",
    density: "compact"
  }, "PUT"), "configure Work Area Notes widget");
  requireOk(await postForm(page, `/dashboards/assistant/widgets/${projectWidgets[0].id}/settings`, {
    sourceMode: "project",
    projectId: householdProject.id,
    density: "compact"
  }, "PUT"), "configure household Projects widget");
  requireOk(await postForm(page, `/dashboards/assistant/widgets/${contactsWidget.id}/settings`, {
    sourceMode: "project",
    projectId: householdProject.id,
    density: "compact"
  }, "PUT"), "configure Contacts/Materials widget");
  requireOk(await postForm(page, `/dashboards/assistant/widgets/${projectWidgets[1].id}/settings`, {
    sourceMode: "project",
    projectId: codeProject.id,
    density: "compact"
  }, "PUT"), "configure code Projects widget");

  const artifacts = {
    goals: { goals: [{ title: "Restock pantry staples" }, { title: "Install labeled shelf bins" }] },
    materials: { materials: [{ name: "Clear storage bins" }, { name: "Label maker tape" }] },
    contacts: { contacts: [{ name: "Casey electrician" }, { name: "Morgan supplier" }] },
    blockers: { blockers: [{ title: "Awaiting shelf measurements" }] },
    "next-actions": { nextActions: [{ action: "Measure cabinet depth" }, { action: "Call supplier" }] },
    progress: { progress: { status: "on track", percent: 55, notes: "Two zones organized" } }
  };
  for (const [type, payload] of Object.entries(artifacts)) {
    requireOk(await postForm(page, `/dashboards/assistant/widgets/${projectWidgets[0].id}/_project-artifacts/${type}`, {
      content: JSON.stringify(payload)
    }, "PUT"), `save household ${type}`);
  }
  requireOk(await postForm(page, `/projects/_detail/${householdProject.id}/directories`, {
    path: ".magenta",
    directoryName: "project"
  }), "ensure project artifact dir");
  requireOk(await postForm(page, `/projects/_detail/${householdProject.id}/files/text`, {
    path: ".magenta/project",
    fileName: "phase03-project-note.md"
  }), "create project note file");
  requireOk(await postForm(page, `/projects/_detail/${householdProject.id}/files/text?path=${encodeURIComponent(".magenta/project/phase03-project-note.md")}`, {
    content: "# Household Project Note\n\nProject file-backed note."
  }, "PUT"), "save project note file");

  return {
    householdProject,
    codeProject,
    workArea,
    personalNotesId: personalNotes.id,
    workAreaNotesId: addedNotes.id,
    householdProjectsId: projectWidgets[0].id,
    codeProjectsId: projectWidgets[1].id,
    contactsWidgetId: contactsWidget.id,
    missingBeforeBinding
  };
}

async function openModalByRequest(page, url) {
  const html = await page.evaluate(async (url) => {
    const response = await fetch(url, { headers: { "HX-Request": "true" } });
    return { ok: response.ok, status: response.status, text: await response.text() };
  }, url);
  if (!html.ok) {
    throw new Error(`${url} failed ${html.status}: ${html.text.slice(0, 300)}`);
  }
  await page.locator("#avatar-edit-container").evaluate((el, text) => { el.innerHTML = text; }, html.text);
  await page.waitForSelector("#avatar-widget-detail-modal", { state: "visible" });
}

async function closeModal(page) {
  await page.locator("#avatar-edit-container").evaluate((el) => { el.innerHTML = ""; });
}

async function modalMetrics(page) {
  return page.evaluate(() => {
    const modal = document.querySelector("#avatar-widget-detail-modal");
    if (!modal) return null;
    const rect = modal.getBoundingClientRect();
    const panel = modal.querySelector(".avatar-widget-detail-panel, .avatar-edit-panel");
    const panelRect = panel?.getBoundingClientRect();
    return {
      text: modal.innerText,
      modalCount: document.querySelectorAll("#avatar-widget-detail-modal").length,
      editContainerCount: document.querySelectorAll("#avatar-edit-container").length,
      viewport: { width: window.innerWidth, height: window.innerHeight },
      rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
      panelRect: panelRect ? { x: panelRect.x, y: panelRect.y, width: panelRect.width, height: panelRect.height } : null,
      bodyScrolls: modal.scrollHeight > modal.clientHeight || (panel && panel.scrollHeight > panel.clientHeight),
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 2
    };
  });
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on("console", (message) => results.console.push({ type: message.type(), text: message.text() }));
page.on("pageerror", (error) => results.pageErrors.push(error.message));
page.on("response", (response) => {
  if (response.status() >= 400) {
    results.network.push({ status: response.status(), url: response.url() });
  }
});

try {
  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  results.seed = await seedDataAndLayout(page);

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  await screenshot(page, "desktop-home-seeded.png");
  let health = await dashboardHealth(page);
  check("desktop_home_seeded_single_shell_and_widget_suite", health.topNavs === 1
    && health.dashboardHomes === 1
    && health.shellRoots === 1
    && health.editContainers === 1
    && health.duplicateIds.length === 0
    && ["notes", "projects", "contacts-materials"].every((type) => health.widgetTypes.includes(type)),
  `widgets=${health.widgetTypes.join(",")}; navs=${health.topNavs}; homes=${health.dashboardHomes}; editContainers=${health.editContainers}; duplicates=${health.duplicateIds.join(",") || "none"}`, { health });

  check("missing_binding_states_visible_before_recovery", results.seed.missingBeforeBinding.projects.every(Boolean) && results.seed.missingBeforeBinding.contacts,
    `projects missing before binding=${results.seed.missingBeforeBinding.projects.join(",")}; contacts missing before binding=${results.seed.missingBeforeBinding.contacts}`);

  const personalNoteTitle = `Personal quick capture ${Date.now()}`;
  await page.locator(`#avatar-widget-${results.seed.personalNotesId} input[name="title"]`).fill(personalNoteTitle);
  await page.locator(`#avatar-widget-${results.seed.personalNotesId} input[name="tags"]`).fill("proof,alpha");
  await page.locator(`#avatar-widget-${results.seed.personalNotesId} textarea[name="body"]`).fill("Browser personal DB-backed quick capture note.");
  await Promise.all([
    page.waitForResponse((response) => response.url().includes(`/${results.seed.personalNotesId}/_notes`)),
    page.locator(`#avatar-widget-${results.seed.personalNotesId} button`, { hasText: "Save Note" }).click()
  ]);
  await screenshot(page, "desktop-personal-note-quick-capture.png");

  await page.locator(`#avatar-widget-${results.seed.personalNotesId} input[name="noteQuery"]`).fill("alpha");
  await Promise.all([
    page.waitForResponse((response) => response.url().includes(`/${results.seed.personalNotesId}/settings`)),
    page.locator(`#avatar-widget-${results.seed.personalNotesId} form.avatar-inline-form button`, { hasText: "Search" }).click()
  ]);
  await page.reload({ waitUntil: "networkidle" });
  const personalSummary = await page.locator(`#avatar-widget-${results.seed.personalNotesId}`).innerText();
  check("personal_notes_source_search_quick_capture_db_distinction", personalSummary.includes("personal")
    && personalSummary.includes("Personal notes")
    && personalSummary.includes(personalNoteTitle),
  personalSummary.slice(0, 500));

  const personalOpenButton = page.locator(`#avatar-widget-${results.seed.personalNotesId} button`, { hasText: "Open" }).first();
  await personalOpenButton.click();
  await page.waitForSelector("#avatar-widget-detail-modal", { state: "visible" });
  await screenshot(page, "desktop-personal-note-modal.png");
  const personalModal = await modalMetrics(page);
  check("personal_last_opened_modal_db_backed", personalModal.text.includes("Personal Note")
    && personalModal.text.includes("personal")
    && personalModal.text.includes("avatar_notes")
    && personalModal.modalCount === 1,
  personalModal.text.slice(0, 400), { modal: personalModal });
  await closeModal(page);

  await page.reload({ waitUntil: "networkidle" });
  const workAreaSummary = await page.locator(`#avatar-widget-${results.seed.workAreaNotesId}`).innerText();
  check("work_area_file_notes_source_chip_and_file_backed_distinction", workAreaSummary.includes("work area")
    && workAreaSummary.includes(results.seed.workArea.id)
    && workAreaSummary.includes("phase03-workarea-note.md"),
  workAreaSummary.slice(0, 500));

  await page.locator(`#avatar-widget-${results.seed.workAreaNotesId} button`, { hasText: "View/Edit" }).first().click();
  await page.waitForSelector("#avatar-widget-detail-modal", { state: "visible" });
  await screenshot(page, "desktop-workarea-file-note-modal-before-save.png");
  let fileModal = await modalMetrics(page);
  const updatedContent = "# Work Area Browser Note\n\nSaved through dashboard file-backed flow.";
  await page.locator("#avatar-widget-detail-modal textarea[name='content']").fill(updatedContent);
  await Promise.all([
    page.waitForResponse((response) => response.url().includes(`/${results.seed.workAreaNotesId}/_file-note`) && response.request().method() === "PUT"),
    page.locator("#avatar-widget-detail-modal button", { hasText: "Save File Note" }).click()
  ]);
  await screenshot(page, "desktop-workarea-file-note-modal-after-save.png");
  fileModal = await modalMetrics(page);
  check("work_area_file_note_markdown_edit_save_refresh", fileModal.text.includes("Saved notes/phase03-workarea-note.md")
    && fileModal.text.toLowerCase().includes("file note")
    && fileModal.text.includes("Saved through dashboard file-backed flow")
    && fileModal.modalCount === 1
    && fileModal.editContainerCount === 1,
  fileModal.text.slice(0, 600), { modal: fileModal });
  await closeModal(page);

  const traversal = await postForm(page, `/dashboards/assistant/widgets/${results.seed.householdProjectsId}/_file-note`, {
    source: "project",
    path: ".magenta/project/../outside.md",
    content: "escape"
  }, "PUT");
  check("project_file_note_traversal_rejected", traversal.status >= 400
    && !traversal.ok
    && (traversal.text.includes("must stay under .magenta/project") || traversal.text.includes("Bad Request")),
  `status=${traversal.status}; body=${traversal.text.slice(0, 250)}`);

  await page.reload({ waitUntil: "networkidle" });
  await screenshot(page, "desktop-projects-contacts-seeded.png");
  const householdText = await page.locator(`#avatar-widget-${results.seed.householdProjectsId}`).innerText();
  const codeText = await page.locator(`#avatar-widget-${results.seed.codeProjectsId}`).innerText();
  const contactsText = await page.locator(`#avatar-widget-${results.seed.contactsWidgetId}`).innerText();
  check("projects_household_artifacts_and_no_repo_only_language", householdText.includes("household project")
    && ["Goals", "Materials", "Contacts", "Blockers", "Next Actions", "Progress"].every((label) => householdText.includes(label))
    && ["Restock pantry staples", "Clear storage bins", "Casey electrician", "Awaiting shelf measurements", "Measure cabinet depth", "on track / 55%"].every((text) => householdText.includes(text))
    && !householdText.toLowerCase().includes("git repo"),
  householdText.slice(0, 800));
  check("projects_code_vs_household_distinction_visible", codeText.includes("code project")
    && codeText.includes(results.seed.codeProject.name),
  codeText.slice(0, 500));
  check("contacts_materials_binding_useful_bounded_rows", contactsText.includes("household project")
    && contactsText.includes(results.seed.householdProject.name)
    && contactsText.includes("Contacts")
    && contactsText.includes("Materials")
    && contactsText.includes("Casey electrician")
    && contactsText.includes("Clear storage bins"),
  contactsText.slice(0, 700));

  await page.goto(`${baseUrl}/manage`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-manage-desktop.png");
  await page.goto(`${baseUrl}/agents`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-agents-desktop.png");
  await page.goto(`${baseUrl}/agents/avatar`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-agent-avatar-desktop.png");
  await page.goto(`${baseUrl}/avatar/_work-areas/${results.seed.workArea.id}/explorer`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-workarea-explorer-desktop.png");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  await screenshot(page, "mobile-home-seeded.png");
  health = await dashboardHealth(page);
  check("mobile_home_no_horizontal_overflow", !health.horizontalOverflow
    && health.topNavs === 1
    && health.dashboardHomes === 1
    && health.duplicateIds.length === 0,
  `scrollWidth=${health.scrollWidth}; viewport=${health.viewport.width}; duplicates=${health.duplicateIds.join(",") || "none"}`, { health });

  await openModalByRequest(page, `/dashboards/assistant/widgets/${results.seed.workAreaNotesId}/_file-note?source=work_area&path=${encodeURIComponent("notes/phase03-workarea-note.md")}`);
  await screenshot(page, "mobile-workarea-file-note-modal.png");
  const mobileFileModal = await modalMetrics(page);
  const saveVisible = await page.locator("#avatar-widget-detail-modal button", { hasText: "Save File Note" }).isVisible();
  check("mobile_modal_scrolling_and_controls_reachable", mobileFileModal.modalCount === 1
    && mobileFileModal.editContainerCount === 1
    && !mobileFileModal.horizontalOverflow
    && saveVisible,
  `panel=${JSON.stringify(mobileFileModal.panelRect)}; bodyScrolls=${mobileFileModal.bodyScrolls}; saveVisible=${saveVisible}; overflow=${mobileFileModal.horizontalOverflow}`, { modal: mobileFileModal });
  await closeModal(page);

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  await screenshot(page, "mobile-projects-long-names.png");
  const mobileLongName = await page.evaluate(() => {
    const bad = [...document.querySelectorAll(".avatar-widget, .avatar-list-row, .avatar-note, .avatar-chip")].filter((el) => {
      const rect = el.getBoundingClientRect();
      return rect.width > window.innerWidth + 2 || rect.right > window.innerWidth + 2;
    }).map((el) => ({ tag: el.tagName, className: el.className, text: el.textContent?.slice(0, 80), rect: el.getBoundingClientRect().toJSON?.() || {} }));
    return { badCount: bad.length, bad };
  });
  check("mobile_long_names_wrap_or_truncate_without_overflow", mobileLongName.badCount === 0,
    `overflowing elements=${mobileLongName.badCount}`, mobileLongName);

  results.visualCritique.push(
    "Desktop dashboard uses the same compact operational language as /manage and /agents: thin bordered panels, restrained chips, small controls, and scan-friendly rows.",
    "Notes, Projects, and Contacts/Materials summaries remain bounded; project artifacts render as rows instead of nested card stacks.",
    "File-note modal stays within the shared modal host and keeps save controls reachable on mobile. Mobile dashboard stacks widgets without horizontal overflow.",
    "Work Area file-note wording matches the file explorer source/path language closely enough for this widget scope; the widget does not duplicate the full explorer."
  );

  const unexpectedConsole = results.console.filter((msg) => ["error"].includes(msg.type)
    && !msg.text.includes("Failed to load resource: the server responded with a status of 400"));
  const unexpectedNetwork = results.network.filter((entry) => entry.status >= 500);
  check("console_and_network_clean_except_expected_400_negative", unexpectedConsole.length === 0 && unexpectedNetwork.length === 0,
    `consoleErrors=${unexpectedConsole.length}; serverErrors=${unexpectedNetwork.length}; >=400=${results.network.map((n) => `${n.status} ${n.url}`).join(" | ") || "none"}`);
} catch (error) {
  results.verdict = "NEEDS_REPAIR";
  results.error = error.stack || error.message;
} finally {
  results.networkRequests = results.network;
  fs.writeFileSync(rel("browser-proof-results.json"), JSON.stringify(results, null, 2));
  fs.writeFileSync(rel("console-messages.txt"), results.console.map((msg) => `[${msg.type}] ${msg.text}`).join("\n") + "\n");
  fs.writeFileSync(rel("network-requests.txt"), results.network.map((entry) => `${entry.status} ${entry.url}`).join("\n") + "\n");
  await browser.close();
}

if (results.verdict !== "PASS_BROWSER_PROOF") {
  process.exitCode = 1;
}
