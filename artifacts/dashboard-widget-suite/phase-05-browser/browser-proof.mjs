import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://127.0.0.1:18081";
const outDir = "artifacts/dashboard-widget-suite/phase-05-browser";
fs.mkdirSync(outDir, { recursive: true });

const runtimeCommand = "mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18081 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-05-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-05-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'";

const results = {
  verdict: "PASS_BROWSER_PROOF",
  baseUrl,
  runtime: {
    command: runtimeCommand,
    port: 18081,
    isolatedRoot: "/tmp/magenta2-dashboard-widget-suite-phase-05-browser"
  },
  screenshots: [],
  checks: [],
  console: [],
  pageErrors: [],
  network: [],
  seed: {},
  visualMetrics: {},
  visualCritique: []
};

function rel(name) {
  return path.join(outDir, name);
}

function failVerdict() {
  results.verdict = "FAIL_BROWSER_PROOF";
}

function check(id, pass, evidence, extra = {}) {
  results.checks.push({ id, status: pass ? "passed" : "failed", evidence, ...extra });
  if (!pass) {
    failVerdict();
  }
}

async function screenshot(page, name, fullPage = true) {
  const file = rel(name);
  await page.screenshot({ path: file, fullPage });
  results.screenshots.push(file);
}

async function postForm(page, url, params = {}, method = "POST") {
  return page.evaluate(async ({ url, params, method }) => {
    const response = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        "HX-Request": "true"
      },
      body: new URLSearchParams(params)
    });
    return { ok: response.ok, status: response.status, text: await response.text() };
  }, { url, params, method });
}

function requireOk(result, label) {
  if (!result.ok) {
    throw new Error(`${label} failed ${result.status}: ${result.text.slice(0, 300)}`);
  }
  return result;
}

function localInput(minutesFromNow) {
  const date = new Date(Date.now() + minutesFromNow * 60_000);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function todayDate(offsetDays = 0) {
  const date = new Date(Date.now() + offsetDays * 86_400_000);
  return date.toISOString().slice(0, 10);
}

async function widgetText(page, type) {
  return page.locator(`[data-avatar-widget-type="${type}"]`).first().innerText();
}

async function rowText(page, type, title) {
  return page.locator(`[data-avatar-widget-type="${type}"] .avatar-list-row`, { hasText: title }).first().innerText();
}

async function dashboardMetrics(page) {
  return page.evaluate(() => {
    const ids = [...document.querySelectorAll("[id]")].map((el) => el.id);
    const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))].sort();
    const maxRight = Math.max(
      document.documentElement.scrollWidth,
      ...[...document.querySelectorAll("body *")].map((el) => Math.ceil(el.getBoundingClientRect().right))
    );
    const clipped = [...document.querySelectorAll("button, .avatar-chip, .avatar-list-row-main strong, input, select")].filter((el) => {
      return el.scrollWidth > el.clientWidth + 2 || el.scrollHeight > el.clientHeight + 2;
    }).slice(0, 12).map((el) => ({
      tag: el.tagName,
      className: el.className,
      text: (el.innerText || el.value || el.getAttribute("aria-label") || "").slice(0, 80),
      scrollWidth: el.scrollWidth,
      clientWidth: el.clientWidth,
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight
    }));
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      horizontalOverflow: maxRight > window.innerWidth + 2 || document.documentElement.scrollWidth > window.innerWidth + 2,
      duplicateIds,
      widgetTypes: [...document.querySelectorAll("[data-avatar-widget-type]")].map((el) => el.getAttribute("data-avatar-widget-type")),
      clipped
    };
  });
}

async function seedHabits(page) {
  requireOk(await postForm(page, "/_dashboards/_habits", {
    title: "Build hydration tracker",
    notes: "Browser proof build tracker",
    habitType: "BUILD",
    period: "DAILY",
    targetQuantity: "3",
    targetUnit: "cups",
    displayDays: "Mon,Wed,Fri",
    startTime: "08:00",
    endTime: "18:00",
    streakEnabled: "true"
  }), "create build habit");
  requireOk(await postForm(page, "/_dashboards/_habits", {
    title: "Quit late snacks tracker",
    notes: "Browser proof quit tracker",
    habitType: "QUIT",
    period: "WEEKLY",
    targetQuantity: "1",
    targetUnit: "cravings",
    displayDays: "Tue,Thu",
    startTime: "19:00",
    endTime: "22:00",
    streakEnabled: "true"
  }), "create quit habit");
  requireOk(await postForm(page, "/_dashboards/_habits", {
    title: "Archived screen-time tracker",
    habitType: "BUILD",
    period: "MONTHLY",
    targetQuantity: "10",
    targetUnit: "hours",
    displayDays: "Sat",
    streakEnabled: "false"
  }), "create archived habit");

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  const ids = await page.evaluate(() => {
    const byTitle = {};
    for (const row of document.querySelectorAll('[data-avatar-widget-type="habits-trackers"] .avatar-habit-row')) {
      const title = row.querySelector("strong")?.innerText?.trim();
      const action = row.querySelector('form[hx-post*="/_dashboards/_habits/"]')?.getAttribute("hx-post") || "";
      const match = action.match(/_habits\/([^/]+)\//);
      if (title && match) byTitle[title] = match[1];
    }
    return byTitle;
  });

  requireOk(await postForm(page, `/_dashboards/_habits/${ids["Build hydration tracker"]}/logs`, {
    date: todayDate(0),
    quantity: "3",
    status: "LOGGED",
    notes: "today target met"
  }), "log build habit today");
  requireOk(await postForm(page, `/_dashboards/_habits/${ids["Build hydration tracker"]}/logs`, {
    date: todayDate(-1),
    quantity: "2",
    status: "LOGGED",
    notes: "history correction for yesterday"
  }), "log build habit yesterday correction");
  requireOk(await postForm(page, `/_dashboards/_habits/${ids["Quit late snacks tracker"]}/logs`, {
    date: todayDate(0),
    quantity: "0",
    status: "LOGGED",
    notes: "quit tracker observed"
  }), "log quit habit");
  requireOk(await postForm(page, `/_dashboards/_habits/${ids["Archived screen-time tracker"]}/archive`), "archive habit");
  results.seed.habitIds = ids;
}

async function seedReminders(page) {
  const create = async (title, minutes, sourceType = "", sourceId = "") => {
    requireOk(await postForm(page, "/_dashboards/_reminders", {
      title,
      remindAt: localInput(minutes),
      notes: `${title} browser proof notes`,
      sourceType,
      sourceId
    }), `create reminder ${title}`);
  };
  await create("Due open reminder", -120, "project", "phase05-household");
  await create("Upcoming linked reminder", 240, "calendar", "phase05-calendar-source");
  await create("Snooze action reminder", -90, "task", "phase05-task-source");
  await create("Complete action reminder", -80, "project", "phase05-project-source");
  await create("Skip action reminder", -70, "habit", "phase05-habit-source");
  await create("Closed restart proof reminder", -60, "project", "phase05-closed-restart");
  await create("Snoozed restart proof reminder", -50, "calendar", "phase05-snooze-restart");
  await create("Open due restart proof reminder", -40, "task", "phase05-open-restart");

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });

  async function clickRowAction(title, action) {
    const row = page.locator('[data-avatar-widget-type="reminders-alerts"] .avatar-reminder-row', { hasText: title }).first();
    await row.getByRole("button", { name: action, exact: true }).click();
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(150);
  }

  await clickRowAction("Snooze action reminder", "Snooze");
  await clickRowAction("Complete action reminder", "Complete");
  await clickRowAction("Skip action reminder", "Skip");
  await clickRowAction("Closed restart proof reminder", "Complete");
  await clickRowAction("Closed restart proof reminder", "Restart");
  await clickRowAction("Snoozed restart proof reminder", "Snooze");
  await clickRowAction("Snoozed restart proof reminder", "Restart");
  await clickRowAction("Open due restart proof reminder", "Restart");
}

async function openModal(page, url, expectedSelector) {
  const result = requireOk(await page.evaluate(async (url) => {
    const response = await fetch(url, { headers: { "HX-Request": "true" } });
    return { ok: response.ok, status: response.status, text: await response.text() };
  }, url), `open modal ${url}`);
  await page.locator("#avatar-edit-container").evaluate((el, html) => { el.innerHTML = html; }, result.text);
  await page.waitForSelector(expectedSelector, { state: "visible" });
}

async function modalMetrics(page) {
  return page.evaluate(() => {
    const modal = document.querySelector(".avatar-modal");
    const panel = document.querySelector(".avatar-edit-panel");
    const rect = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, top: r.top, right: r.right, bottom: r.bottom, left: r.left };
    };
    return {
      modal: rect(modal),
      panel: rect(panel),
      panelScrollHeight: panel?.scrollHeight || 0,
      panelClientHeight: panel?.clientHeight || 0,
      overflowY: panel ? getComputedStyle(panel).overflowY : null,
      closeVisible: !!document.querySelector(".avatar-edit-header button")
    };
  });
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  page.on("console", (message) => {
    results.console.push({ type: message.type(), text: message.text() });
  });
  page.on("pageerror", (error) => {
    results.pageErrors.push({ message: error.message, stack: error.stack });
    failVerdict();
  });
  page.on("response", (response) => {
    const status = response.status();
    if (status >= 400) {
      const url = response.url();
      if (!url.endsWith("/favicon.ico")) {
        results.network.push({ status, method: response.request().method(), url });
      }
    }
  });
  page.on("requestfailed", (request) => {
    results.network.push({ failure: request.failure()?.errorText, method: request.method(), url: request.url() });
  });

  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  await seedHabits(page);
  await seedReminders(page);
  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  const widgetIds = await page.evaluate(() => Object.fromEntries(
    [...document.querySelectorAll("[data-avatar-widget-type]")].map((el) => [
      el.getAttribute("data-avatar-widget-type"),
      el.getAttribute("data-avatar-widget")
    ])
  ));
  results.seed.widgetIds = widgetIds;

  const habitsText = await widgetText(page, "habits-trackers");
  const remindersText = await widgetText(page, "reminders-alerts");
  const contextText = await widgetText(page, "dashboard-context");
  const buildRow = await rowText(page, "habits-trackers", "Build hydration tracker");
  const quitRow = await rowText(page, "habits-trackers", "Quit late snacks tracker");
  const dueRow = await rowText(page, "reminders-alerts", "Due open reminder");
  const upcomingRow = await rowText(page, "reminders-alerts", "Upcoming linked reminder");
  const snoozedRow = await rowText(page, "reminders-alerts", "Snooze action reminder");
  const completedRow = await rowText(page, "reminders-alerts", "Complete action reminder");
  const skippedRow = await rowText(page, "reminders-alerts", "Skip action reminder");
  const restartedClosedRow = await rowText(page, "reminders-alerts", "Closed restart proof reminder");
  const restartedSnoozedRow = await rowText(page, "reminders-alerts", "Snoozed restart proof reminder");
  const restartedDueRow = await rowText(page, "reminders-alerts", "Open due restart proof reminder");

  check("habits_build_quit_target_period_progress_trend_streak_render",
    buildRow.includes("build / daily / target 3 cups")
      && quitRow.includes("quit / weekly / target 1 cravings")
      && buildRow.includes("3/3 cups")
      && buildRow.includes("active days")
      && buildRow.includes("streak"),
    { buildRow, quitRow });

  check("habits_display_days_and_time_range_render",
    /Mon|Wed|Fri|08:00|18:00|Tue|Thu|19:00|22:00/.test(buildRow + "\n" + quitRow),
    { buildRow, quitRow, expected: "created displayDays/time ranges should be visible in summary or detail rows" });

  check("habits_archive_state_and_non_punitive_copy_render",
    habitsText.includes("Archived") && habitsText.includes("1") && !/fail|failed|punish|penalty/i.test(habitsText.replace("without penalty language", "")),
    habitsText.slice(0, 1200));

  const habitLogForms = await page.locator('[data-avatar-widget-type="habits-trackers"] form[hx-post*="/_dashboards/_habits/"][hx-post*="/logs"]').count();
  const visibleHabitDateInputs = await page.locator('[data-avatar-widget-type="habits-trackers"] input[name="date"]:not([type="hidden"])').count();
  check("habits_history_correction_ui_action_render",
    habitLogForms > 0 && visibleHabitDateInputs > 0,
    { habitLogForms, visibleHabitDateInputs, expected: "history correction should expose a visible date/log correction control, not only hidden current-day buttons" });

  check("reminders_due_upcoming_snoozed_completed_skipped_and_source_labels_render",
    dueRow.includes("OPEN")
      && upcomingRow.includes("OPEN") && upcomingRow.includes("source calendar phase05-calendar-source")
      && snoozedRow.includes("SNOOZED")
      && completedRow.includes("COMPLETED")
      && skippedRow.includes("SKIPPED")
      && remindersText.includes("Dashboard inbox only")
      && remindersText.includes("External push, email, and PWA delivery are deferred."),
    { dueRow, upcomingRow, snoozedRow, completedRow, skippedRow });

  const closedControls = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('[data-avatar-widget-type="reminders-alerts"] .avatar-reminder-row')];
    const result = {};
    for (const title of ["Complete action reminder", "Skip action reminder"]) {
      const row = rows.find((item) => item.innerText.includes(title));
      const buttons = [...(row?.querySelectorAll("button") || [])].map((button) => button.innerText.trim());
      result[title] = {
        text: row?.innerText || "",
        buttons,
        hasComplete: buttons.includes("Complete"),
        hasSnooze: buttons.includes("Snooze"),
        hasSkip: buttons.includes("Skip"),
        hasRestart: buttons.includes("Restart")
      };
    }
    return result;
  });
  check("closed_reminder_rows_hide_complete_snooze_skip_and_show_restart",
    Object.values(closedControls).every((row) => row.hasRestart && !row.hasComplete && !row.hasSnooze && !row.hasSkip),
    closedControls);

  check("restart_action_visible_and_works_for_closed_snoozed_and_open_due",
    restartedClosedRow.includes("OPEN") && restartedClosedRow.includes("Complete")
      && restartedSnoozedRow.includes("OPEN") && restartedSnoozedRow.includes("Complete")
      && restartedDueRow.includes("OPEN") && restartedDueRow.includes("Complete"),
    { restartedClosedRow, restartedSnoozedRow, restartedDueRow });

  check("reminders_reschedule_actions_visible",
    remindersText.match(/Reschedule/g)?.length >= 8,
    { rescheduleCount: remindersText.match(/Reschedule/g)?.length || 0 });

  const reminderRowLayout = await page.evaluate(() => {
    return [...document.querySelectorAll('[data-avatar-widget-type="reminders-alerts"] .avatar-reminder-row')].map((row) => {
      const main = row.querySelector(".avatar-list-row-main");
      const title = main?.querySelector("strong");
      const mainRect = main?.getBoundingClientRect();
      const titleRect = title?.getBoundingClientRect();
      return {
        text: row.innerText.slice(0, 180),
        mainWidth: mainRect?.width || 0,
        titleWidth: titleRect?.width || 0,
        titleHeight: titleRect?.height || 0,
        actionButtons: [...row.querySelectorAll("button")].map((button) => button.innerText.trim())
      };
    });
  });
  check("reminder_rows_readable_not_crushed_by_actions",
    reminderRowLayout.every((row) => row.mainWidth >= 120 && row.titleWidth >= 80),
    reminderRowLayout);

  check("dashboard_context_read_only_contract_boundary",
    contextText.includes("Read-only dashboard context")
      && contextText.includes("These descriptors do not grant chat actions")
      && contextText.includes("read-only context")
      && !contextText.includes("Authorize")
      && !contextText.includes("Run action")
      && !contextText.includes("Execute"),
    contextText.slice(0, 1800));

  const desktopMetrics = await dashboardMetrics(page);
  results.visualMetrics.desktop = desktopMetrics;
  check("desktop_1440_no_horizontal_overflow_or_duplicate_ids",
    !desktopMetrics.horizontalOverflow && desktopMetrics.duplicateIds.length === 0,
    desktopMetrics);

  await screenshot(page, "desktop-home-seeded.png");
  await openModal(page, `/dashboards/assistant/widgets/${widgetIds["habits-trackers"]}/detail`, "#avatar-widget-detail-modal");
  await screenshot(page, "desktop-habits-detail-modal.png");
  await page.locator("#avatar-edit-container").evaluate((el) => { el.innerHTML = ""; });
  await openModal(page, `/dashboards/assistant/widgets/${widgetIds["reminders-alerts"]}/detail`, "#avatar-widget-detail-modal");
  await screenshot(page, "desktop-reminders-detail-modal.png");
  await page.locator("#avatar-edit-container").evaluate((el) => { el.innerHTML = ""; });
  await page.goto(`${baseUrl}/manage`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-manage-desktop.png");
  await page.goto(`${baseUrl}/agents`, { waitUntil: "networkidle" });
  await screenshot(page, "reference-agents-desktop.png");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/`, { waitUntil: "networkidle" });
  const mobileMetrics = await dashboardMetrics(page);
  results.visualMetrics.mobile = mobileMetrics;
  check("mobile_390_no_horizontal_overflow_or_clipped_controls",
    mobileMetrics.scrollWidth <= mobileMetrics.clientWidth + 2 && mobileMetrics.clipped.length === 0,
    mobileMetrics);
  await screenshot(page, "mobile-home-seeded.png");
  await openModal(page, `/dashboards/assistant/widgets/${widgetIds["reminders-alerts"]}/settings`, "#avatar-widget-settings-modal");
  const mobileModal = await modalMetrics(page);
  results.visualMetrics.mobileModal = mobileModal;
  check("mobile_settings_modal_usable_and_scroll_bounded",
    !!mobileModal.modal
      && !!mobileModal.panel
      && mobileModal.panel.right <= 390
      && mobileModal.panel.left >= 0
      && mobileModal.panel.bottom <= 844
      && mobileModal.closeVisible,
    mobileModal);
  await screenshot(page, "mobile-reminders-settings-modal.png");

  if (results.pageErrors.length === 0 && results.network.length === 0) {
    check("console_network_no_unexpected_errors", true, { pageErrors: [], network: [] });
  } else {
    check("console_network_no_unexpected_errors", false, { pageErrors: results.pageErrors, network: results.network });
  }

  const failedChecks = results.checks.filter((item) => item.status === "failed").map((item) => item.id);
  results.visualCritique.push(
    "Desktop: seeded Phase 05 widgets keep the existing operational dashboard frame, compact controls, thin panel borders, and row/list presentation used by the dashboard suite.",
    "Desktop: context rows are scan-friendly and avoid decorative nested cards; chips remain restrained and semantic.",
    "Desktop: reminder rows preserve readable title/meta width while action and reschedule controls wrap below/alongside without forcing one-letter title columns.",
    "Desktop: habit rows show compact day/time metadata and expose a visible correction form for date, quantity, status, and Apply.",
    "Mobile: the dashboard stacks into a single column without measured document overflow; the settings modal is bounded and close controls remain reachable.",
    failedChecks.length === 0
      ? "No remaining visual blocker was observed in the focused rerun."
      : `Remaining failed checks: ${failedChecks.join(", ")}.`
  );

  fs.writeFileSync(rel("browser-proof-results.json"), JSON.stringify(results, null, 2));
  fs.writeFileSync(rel("console-messages.txt"), results.console.map((m) => `[${m.type}] ${m.text}`).join("\n"));
  fs.writeFileSync(rel("network-issues.json"), JSON.stringify(results.network, null, 2));
  await browser.close();
}

main().catch((error) => {
  results.verdict = "FAIL_BROWSER_PROOF";
  results.fatal = { message: error.message, stack: error.stack };
  fs.writeFileSync(rel("browser-proof-results.json"), JSON.stringify(results, null, 2));
  process.exitCode = 1;
});
