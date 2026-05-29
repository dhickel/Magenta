import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://localhost:18080";
const outDir = "artifacts/dashboard-widget-suite/phase-02-browser";
fs.mkdirSync(outDir, { recursive: true });

const now = new Date();
const pad = (n) => String(n).padStart(2, "0");
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const localDateTime = (d, hour, minute = 0) => `${ymd(d)}T${pad(hour)}:${pad(minute)}`;
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
const tomorrow = new Date(today.getTime() + 24 * 60 * 60 * 1000);
const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000);

const results = {
  verdict: "PASS_BROWSER_PROOF",
  baseUrl,
  runtime: {
    command: "mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-02-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-02-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'",
    port: 18080,
    isolatedRoot: "/tmp/magenta2-dashboard-widget-suite-phase-02-browser"
  },
  screenshots: [],
  checks: [],
  console: [],
  pageErrors: [],
  network: []
};

function rel(name) {
  return path.join(outDir, name);
}

function check(id, pass, evidence, extra = {}) {
  const status = pass ? "passed" : "failed";
  results.checks.push({ id, status, evidence, ...extra });
  if (!pass) {
    results.verdict = "NEEDS_REPAIR";
  }
}

async function screenshot(page, name, fullPage = true) {
  const file = rel(name);
  await page.screenshot({ path: file, fullPage });
  results.screenshots.push(file);
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
      widgetTypes: [...document.querySelectorAll("[data-avatar-widget-type]")].map((el) => el.getAttribute("data-avatar-widget-type")),
      duplicateIds: duplicates,
      horizontalOverflow: maxRight > window.innerWidth + 2,
      viewport: { width: window.innerWidth, height: window.innerHeight, scrollWidth: document.documentElement.scrollWidth }
    };
  });
}

async function seedPlannerData(page) {
  return page.evaluate(async ({ todayText, tomorrowText, yesterdayText }) => {
    async function post(path, params) {
      const response = await fetch(path, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
          "HX-Request": "true"
        },
        body: new URLSearchParams(params)
      });
      const text = await response.text();
      if (!response.ok) {
        throw new Error(`${path} failed ${response.status}: ${text.slice(0, 200)}`);
      }
      return text;
    }
    function lastTaskId(html) {
      const matches = [...html.matchAll(/_planner-tasks\/([^/"?]+)\/subtodos/g)].map((m) => m[1]);
      return matches[matches.length - 1] || null;
    }

    await post("/_dashboards/_calendar", {
      title: "Browser proof calendar event",
      startsAt: `${tomorrowText}T13:00`,
      endsAt: `${tomorrowText}T14:00`,
      location: "Kitchen"
    });
    await post("/_dashboards/_time-blocks", {
      title: "Browser proof seeded time block",
      startsAt: `${todayText}T14:00`,
      endsAt: `${todayText}T15:00`,
      sourceType: "planner"
    });
    await post("/_dashboards/_reminders", {
      title: "Browser proof seeded reminder",
      remindAt: `${todayText}T16:00`,
      notes: "Seeded browser reminder",
      sourceType: "planner"
    });
    await post("/_dashboards/_planner-tasks", {
      title: "Browser proof top priority",
      priority: "URGENT",
      dueAt: `${todayText}T11:00`,
      recurrenceMode: "NONE"
    });
    await post("/_dashboards/_planner-tasks", {
      title: "Browser proof overdue task",
      priority: "HIGH",
      dueAt: `${yesterdayText}T09:00`,
      recurrenceMode: "NONE"
    });
    await post("/_dashboards/_planner-tasks", {
      title: "Browser proof unscheduled backlog",
      priority: "NORMAL",
      recurrenceMode: "NONE"
    });
    const recurringHtml = await post("/_dashboards/_planner-tasks", {
      title: "Browser proof recurring chore",
      priority: "NORMAL",
      startsAt: `${todayText}T09:00`,
      dueAt: `${todayText}T10:00`,
      recurrenceMode: "DAILY",
      recurrenceInterval: "1",
      recurrenceStartDate: todayText,
      recurrenceTime: "09:00",
      linkedProjectId: "project-browser-proof"
    });
    const recurringTaskId = lastTaskId(recurringHtml);
    if (recurringTaskId) {
      await post(`/_dashboards/_planner-tasks/${recurringTaskId}/subtodos`, { title: "Browser proof subtodo" });
      const occurrenceStart = new Date(`${todayText}T09:00:00`).toISOString();
      await post(`/_dashboards/_planner-tasks/${recurringTaskId}/occurrences`, {
        occurrenceStart,
        action: "RESTARTED"
      });
      return { recurringTaskId, occurrenceStart };
    }
    return { recurringTaskId: null, occurrenceStart: null };
  }, { todayText: ymd(today), tomorrowText: ymd(tomorrow), yesterdayText: ymd(yesterday) });
}

async function openDetail(page, label) {
  await page.locator(`button[aria-label="${label}"]`).first().click();
  await page.waitForSelector("#avatar-widget-detail-modal", { state: "visible" });
}

async function closeModal(page) {
  const close = page.locator("#avatar-widget-detail-modal button", { hasText: "Close" }).first();
  if (await close.count()) {
    await close.click();
    await page.waitForTimeout(250);
  }
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
  await page.goto(baseUrl + "/", { waitUntil: "networkidle" });
  const seed = await seedPlannerData(page);
  results.seed = seed;
  await page.reload({ waitUntil: "networkidle" });
  await screenshot(page, "desktop-home-seeded.png");

  let health = await dashboardHealth(page);
  check("desktop_normal_dashboard_widgets_and_single_shell", health.widgetTypes.includes("today-planner")
    && health.widgetTypes.includes("tasks-routines")
    && health.widgetTypes.includes("calendar-schedule")
    && health.topNavs === 1
    && health.dashboardHomes === 1
    && health.shellRoots === 1
    && health.duplicateIds.length === 0,
  `widgets=${health.widgetTypes.join(",")}; navs=${health.topNavs}; dashboardHomes=${health.dashboardHomes}; duplicates=${health.duplicateIds.join(",") || "none"}`,
  { health });

  await openDetail(page, "Open Today Planner detail");
  await screenshot(page, "desktop-today-detail.png");
  const todayDetail = await page.evaluate(() => {
    const modal = document.querySelector("#avatar-widget-detail-modal");
    const text = modal?.innerText || "";
    const sectionLabels = [...modal?.querySelectorAll(".avatar-phase-list strong") || []].map((el) => el.textContent?.trim());
    return {
      text,
      sectionLabels,
      hasQuickCapture: !!modal?.querySelector('form[hx-post="/_dashboards/_today/quick-capture"]'),
      hasReviewNotes: !!modal?.querySelector('textarea[name="reviewNotes"]'),
      hasRestart: !!modal?.querySelector('[hx-post="/_dashboards/_today/restart"]')
    };
  });
  check("today_detail_core_controls", todayDetail.hasQuickCapture && todayDetail.hasReviewNotes && todayDetail.hasRestart
    && ["Top priorities", "Now", "Next", "Later"].every((label) => todayDetail.sectionLabels.includes(label)),
  `sections=${todayDetail.sectionLabels.join(",")}; quick=${todayDetail.hasQuickCapture}; review=${todayDetail.hasReviewNotes}; restart=${todayDetail.hasRestart}`);
  check("today_detail_overdue_and_unscheduled_are_usable", todayDetail.sectionLabels.includes("Overdue")
    && todayDetail.sectionLabels.includes("Unscheduled"),
  `detail sections=${todayDetail.sectionLabels.join(",") || "none"}; modal text contains Overdue=${todayDetail.text.includes("Overdue")}, Unscheduled=${todayDetail.text.includes("Unscheduled")}`);

  const quickTitle = `Browser quick capture ${Date.now()}`;
  await page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_today/quick-capture"] input[name="title"]').fill(quickTitle);
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/_dashboards/_today/quick-capture")),
    page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_today/quick-capture"] button[type="submit"]').click()
  ]);
  await page.waitForTimeout(400);
  health = await dashboardHealth(page);
  const quickVisible = await page.locator(`[data-avatar-widget-type="today-planner"]`).first().innerText();
  await screenshot(page, "desktop-after-quick-capture.png");
  check("quick_capture_refresh_boundaries", quickVisible.includes(quickTitle)
    && health.editContainers === 1
    && health.topNavs === 1
    && health.dashboardHomes === 1
    && health.duplicateIds.length === 0,
  `quick visible=${quickVisible.includes(quickTitle)}; editContainers=${health.editContainers}; duplicates=${health.duplicateIds.join(",") || "none"}`);

  await closeModal(page);
  await openDetail(page, "Open Today Planner detail");
  const reviewNotes = `Reviewed in browser proof ${Date.now()}`;
  await page.locator('#avatar-widget-detail-modal textarea[name="reviewNotes"]').fill(reviewNotes);
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/_dashboards/_today/review")),
    page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_today/review"] button[type="submit"]').click()
  ]);
  await page.waitForTimeout(400);
  await closeModal(page);
  await openDetail(page, "Open Today Planner detail");
  const reviewValue = await page.locator('#avatar-widget-detail-modal textarea[name="reviewNotes"]').inputValue();
  await screenshot(page, "desktop-today-review-notes-persisted.png");
  check("today_review_notes_visible_after_submit", reviewValue.includes(reviewNotes), `review textarea value=${reviewValue}`);
  await closeModal(page);

  await openDetail(page, "Open Tasks/Routines detail");
  await screenshot(page, "desktop-tasks-detail.png");
  const tasksInitial = await page.evaluate(() => {
    const modal = document.querySelector("#avatar-widget-detail-modal");
    const text = modal?.innerText || "";
    return {
      text,
      hasStatus: !!modal?.querySelector('select[name="status"]'),
      hasRange: !!modal?.querySelector('select[name="range"]'),
      hasRecurrence: !!modal?.querySelector('select[name="recurrence"]'),
      hasOccurrenceActions: ["Skip", "Snoozed", "Restarted"].some((label) => text.includes(label)) || ["Skip", "Snooze", "Restart"].some((label) => text.includes(label)),
      hasSubtodoText: text.includes("Browser proof subtodo"),
      hasProjectLink: text.includes("project-browser-proof")
    };
  });
  check("tasks_detail_filters_and_recurrence_controls", tasksInitial.hasStatus && tasksInitial.hasRange && tasksInitial.hasRecurrence
    && tasksInitial.text.includes("repeat daily"),
  `status=${tasksInitial.hasStatus}; range=${tasksInitial.hasRange}; recurrence=${tasksInitial.hasRecurrence}; has daily=${tasksInitial.text.includes("repeat daily")}`);
  check("tasks_detail_project_and_subtodo_placeholders", tasksInitial.hasProjectLink && tasksInitial.hasSubtodoText,
  `project link visible=${tasksInitial.hasProjectLink}; subtodo visible=${tasksInitial.hasSubtodoText}`);
  check("tasks_detail_occurrence_actions_visible", tasksInitial.hasOccurrenceActions,
  `occurrence action text present=${tasksInitial.hasOccurrenceActions}`);

  await page.locator('#avatar-widget-detail-modal select[name="status"]').selectOption("PLANNED");
  await page.locator('#avatar-widget-detail-modal select[name="range"]').selectOption("WEEK");
  await page.locator('#avatar-widget-detail-modal select[name="recurrence"]').selectOption("RECURRING");
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/_dashboards/_widgets/tasks-routines/detail")),
    page.locator('#avatar-widget-detail-modal form.avatar-planner-filters button[type="submit"]').click()
  ]);
  await page.waitForTimeout(400);
  await screenshot(page, "desktop-tasks-filtered-recurring-week.png");
  const filteredText = await page.locator("#avatar-widget-detail-modal").innerText();
  check("tasks_filters_apply_server_fragment", filteredText.includes("Browser proof recurring chore")
    && !filteredText.includes("Browser proof unscheduled backlog"),
  `filtered text includes recurring=${filteredText.includes("Browser proof recurring chore")}; excludes unscheduled=${!filteredText.includes("Browser proof unscheduled backlog")}`);

  const skipButton = page.locator('#avatar-widget-detail-modal form:has(input[name="action"][value="SKIPPED"]) button[type="submit"]').first();
  if (await skipButton.count()) {
    await Promise.all([
      page.waitForResponse((response) => response.url().includes("/occurrences")),
      skipButton.click()
    ]);
    await page.waitForTimeout(400);
  }
  await closeModal(page);
  await openDetail(page, "Open Tasks/Routines detail");
  const tasksAfterAction = await page.locator("#avatar-widget-detail-modal").innerText();
  await screenshot(page, "desktop-tasks-after-occurrence-action.png");
  check("tasks_occurrence_status_visible_and_parent_unchanged", tasksAfterAction.includes("planned /")
    && tasksAfterAction.toLowerCase().includes("skipped"),
  `parent task meta contains planned=${tasksAfterAction.includes("planned /")}; visible skipped occurrence status=${tasksAfterAction.toLowerCase().includes("skipped")}`);
  await closeModal(page);

  await openDetail(page, "Open Calendar/Schedule detail");
  await screenshot(page, "desktop-calendar-detail-before-actions.png");
  const calendarInitial = await page.evaluate(() => {
    const modal = document.querySelector("#avatar-widget-detail-modal");
    const text = modal?.innerText || "";
    return {
      text,
      gridCells: modal?.querySelectorAll(".avatar-calendar-cell").length || 0,
      hasAgenda: text.includes("Agenda"),
      pillClasses: [...modal?.querySelectorAll(".avatar-calendar-pill") || []].map((el) => el.className),
      agendaKinds: {
        event: text.includes("event /"),
        recurrence: text.includes("recurrence /"),
        timeBlock: text.includes("time_block /") || text.includes("time block /"),
        reminder: text.includes("reminder /")
      },
      hasTimeBlockForm: !!modal?.querySelector('form[hx-post="/_dashboards/_time-blocks"]'),
      hasReminderForm: !!modal?.querySelector('form[hx-post="/_dashboards/_reminders"]')
    };
  });
  check("calendar_grid_and_agenda_structure", calendarInitial.gridCells === 42 && calendarInitial.hasAgenda,
  `grid cells=${calendarInitial.gridCells}; agenda=${calendarInitial.hasAgenda}`);
  check("calendar_distinct_item_kinds", calendarInitial.agendaKinds.event
    && calendarInitial.agendaKinds.recurrence
    && calendarInitial.agendaKinds.timeBlock
    && calendarInitial.agendaKinds.reminder,
  `agendaKinds=${JSON.stringify(calendarInitial.agendaKinds)}; pill classes=${calendarInitial.pillClasses.join(" | ")}`);
  check("calendar_timeblock_and_reminder_htmx_affordances", calendarInitial.hasTimeBlockForm && calendarInitial.hasReminderForm,
  `time block form=${calendarInitial.hasTimeBlockForm}; reminder form=${calendarInitial.hasReminderForm}`);

  const blockTitle = `Browser UI time block ${Date.now()}`;
  await page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_time-blocks"] input[name="title"]').fill(blockTitle);
  await page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_time-blocks"] input[name="startsAt"]').fill(localDateTime(today, 15, 30));
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/_dashboards/_time-blocks")),
    page.locator('#avatar-widget-detail-modal form[hx-post="/_dashboards/_time-blocks"] button[type="submit"]').click()
  ]);
  await page.waitForTimeout(400);
  health = await dashboardHealth(page);
  const calendarWidgetText = await page.locator('[data-avatar-widget-type="calendar-schedule"]').first().innerText();
  await screenshot(page, "desktop-after-timeblock-submit.png");
  check("timeblock_htmx_refresh_boundaries", calendarWidgetText.includes(blockTitle)
    && health.topNavs === 1
    && health.dashboardHomes === 1
    && health.duplicateIds.length === 0,
  `time block visible=${calendarWidgetText.includes(blockTitle)}; duplicates=${health.duplicateIds.join(",") || "none"}`);

  const reminderRoute = await page.evaluate(async ({ title, remindAt }) => {
    const response = await fetch("/_dashboards/_reminders", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        "HX-Request": "true"
      },
      body: new URLSearchParams({ title, remindAt, notes: "Browser proof reminder route" })
    });
    return { status: response.status, text: await response.text() };
  }, { title: `Browser route reminder ${Date.now()}`, remindAt: localDateTime(today, 16, 0) });
  check("reminder_htmx_route_returns_calendar_fragment", reminderRoute.status === 200 && reminderRoute.text.includes("avatar-widget") && reminderRoute.text.includes("calendar-schedule"),
  `route status=${reminderRoute.status}; fragment has calendar-schedule=${reminderRoute.text.includes("calendar-schedule")}`);

  await closeModal(page);
  await openDetail(page, "Open Calendar/Schedule detail");
  await screenshot(page, "desktop-calendar-after-reminder-route.png");
  const calendarAfterActions = await page.locator("#avatar-widget-detail-modal").innerText();
  check("calendar_reflects_occurrence_status_and_reminder", calendarAfterActions.toLowerCase().includes("skipped")
    && calendarAfterActions.includes("Browser route reminder"),
  `calendar skipped visible=${calendarAfterActions.toLowerCase().includes("skipped")}; reminder visible=${calendarAfterActions.includes("Browser route reminder")}`);
  await closeModal(page);

  for (const route of ["/manage", "/agents"]) {
    await page.goto(baseUrl + route, { waitUntil: "networkidle" });
    await screenshot(page, `reference-${route.slice(1)}-desktop.png`);
    const refHealth = await dashboardHealth(page);
    check(`reference_${route.slice(1)}_loads_without_overflow_or_duplicate_ids`, !refHealth.horizontalOverflow && refHealth.duplicateIds.length === 0,
    `overflow=${refHealth.horizontalOverflow}; duplicates=${refHealth.duplicateIds.join(",") || "none"}`, { health: refHealth });
  }

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(baseUrl + "/", { waitUntil: "networkidle" });
  await openDetail(page, "Open Today Planner detail");
  await screenshot(page, "mobile-today-detail.png");
  const mobileToday = await dashboardHealth(page);
  check("mobile_today_modal_no_horizontal_overflow", !mobileToday.horizontalOverflow, `viewport=${JSON.stringify(mobileToday.viewport)}`, { health: mobileToday });
  await closeModal(page);
  await openDetail(page, "Open Tasks/Routines detail");
  await screenshot(page, "mobile-tasks-detail.png");
  const mobileTasks = await dashboardHealth(page);
  const mobileTargets = await page.evaluate(() => [...document.querySelectorAll("#avatar-widget-detail-modal button, #avatar-widget-detail-modal input, #avatar-widget-detail-modal select, #avatar-widget-detail-modal textarea")]
    .map((el) => {
      const rect = el.getBoundingClientRect();
      return { tag: el.tagName, text: el.textContent?.trim() || el.getAttribute("name") || el.getAttribute("aria-label"), left: rect.left, right: rect.right, width: rect.width };
    })
    .filter((item) => item.left < -2 || item.right > window.innerWidth + 2));
  check("mobile_tasks_modal_controls_stay_in_view", !mobileTasks.horizontalOverflow && mobileTargets.length === 0,
  `overflow=${mobileTasks.horizontalOverflow}; offscreen controls=${JSON.stringify(mobileTargets)}`, { health: mobileTasks });
  await closeModal(page);
  await openDetail(page, "Open Calendar/Schedule detail");
  await screenshot(page, "mobile-calendar-detail.png");
  const mobileCalendar = await dashboardHealth(page);
  check("mobile_calendar_modal_no_horizontal_overflow", !mobileCalendar.horizontalOverflow, `viewport=${JSON.stringify(mobileCalendar.viewport)}`, { health: mobileCalendar });

  results.visualCritique = [
    "Desktop / keeps a dense operational shell with chat rail plus widget grid and compact blue-gray cards.",
    "The planner widgets match the low-radius panel language and avoid hero/marketing composition.",
    "Calendar/Schedule uses a real month grid and agenda, but missing visible reminder creation weakens the expected in-dashboard workflow.",
    "Today Planner hierarchy is compact, but overdue and unscheduled data are not exposed as usable detail sections.",
    "Tasks/Routines filters are compact and HTMX-first, but subtodos and occurrence state visibility are weak in the detail surface.",
    "Mobile modals are scrollable and mostly compact, but the calendar grid is dense and should be watched for touch ergonomics."
  ];
} catch (error) {
  results.verdict = "NEEDS_REPAIR";
  results.error = error.stack || String(error);
} finally {
  await browser.close();
  fs.writeFileSync(rel("browser-proof-results.json"), JSON.stringify(results, null, 2));
  fs.writeFileSync(rel("console-messages.txt"), results.console.map((entry) => `${entry.type}: ${entry.text}`).join("\n"));
  fs.writeFileSync(rel("network-requests.txt"), results.network.map((entry) => `${entry.status} ${entry.url}`).join("\n"));
}

if (results.verdict !== "PASS_BROWSER_PROOF") {
  process.exitCode = 1;
}
