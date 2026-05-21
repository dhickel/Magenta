import { chromium } from "playwright";
import fs from "node:fs/promises";

const baseUrl = "http://localhost:18080";
const outDir = "test-results/services-ux-playwright-remediation-2026-05-21";
await fs.mkdir(outDir, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();

const consoleMessages = [];
const networkEvents = [];
page.on("console", (msg) => {
  consoleMessages.push({ type: msg.type(), text: msg.text(), url: page.url() });
});
page.on("response", (resp) => {
  const status = resp.status();
  if (status >= 400) {
    networkEvents.push({ status, url: resp.url(), method: resp.request().method() });
  }
});
page.on("requestfailed", (req) => {
  networkEvents.push({ status: "FAILED", url: req.url(), method: req.method(), failure: req.failure()?.errorText });
});

async function screenshot(name) {
  await page.screenshot({ path: `${outDir}/${name}`, fullPage: true });
}

await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${baseUrl}/dashboard`, { waitUntil: "networkidle" });
await screenshot("dashboard-mobile-390x844.png");
const openResult = await page.evaluate(async () => {
  const sidebar = document.querySelector(".main-container.has-sidebar > .main-sidebar");
  const toggle =
    document.querySelector("[data-sidebar-toggle]") ||
    document.querySelector("[aria-label*='menu' i]") ||
    document.querySelector("[aria-label*='sidebar' i]") ||
    document.querySelector(".mobile-nav-toggle") ||
    document.querySelector("button[title*='Menu' i]");
  if (!toggle) {
    return { clicked: false, reason: "toggle-not-found" };
  }
  // Normalize state: close first, then open to validate open anchoring after interaction.
  toggle.click();
  await new Promise((resolve) => setTimeout(resolve, 250));
  const rectClosed = sidebar ? sidebar.getBoundingClientRect() : null;
  return {
    clicked: true,
    rectClosed,
    stylesClosed: sidebar
      ? {
          position: getComputedStyle(sidebar).position,
          overflowY: getComputedStyle(sidebar).overflowY,
          transform: getComputedStyle(sidebar).transform
        }
      : null
  };
});

await screenshot("mobile-sidebar-closed-390x844.png");

const reopenedState = await page.evaluate(async () => {
  const sidebar = document.querySelector(".main-container.has-sidebar > .main-sidebar");
  const toggle =
    document.querySelector("[data-sidebar-toggle]") ||
    document.querySelector("[aria-label*='menu' i]") ||
    document.querySelector("[aria-label*='sidebar' i]") ||
    document.querySelector(".mobile-nav-toggle") ||
    document.querySelector("button[title*='Menu' i]");
  if (!toggle) return { reopened: false };
  toggle.click();
  await new Promise((resolve) => setTimeout(resolve, 250));
  const rectOpen = sidebar ? sidebar.getBoundingClientRect() : null;
  const styles = sidebar ? getComputedStyle(sidebar) : null;
  return {
    reopened: true,
    rectOpen,
    styles: styles ? { position: styles.position, overflowY: styles.overflowY, transform: styles.transform } : null
  };
});
await screenshot("mobile-sidebar-open-390x844.png");

const sidebarPostCheck = await page.evaluate(() => {
  const sidebar = document.querySelector(".main-container.has-sidebar > .main-sidebar");
  if (!sidebar) return { exists: false };
  const rect = sidebar.getBoundingClientRect();
  const scrollability = sidebar.scrollHeight > sidebar.clientHeight;
  return { exists: true, rect, scrollHeight: sidebar.scrollHeight, clientHeight: sidebar.clientHeight, scrollability };
});

await page.goto(`${baseUrl}/jobs`, { waitUntil: "networkidle" });
await screenshot("jobs-mobile-390x844.png");

const jobsOverflowCheck = await page.evaluate(() => {
  const wrappers = Array.from(document.querySelectorAll(".dashboard-section, .orch-panel"));
  const withTables = wrappers
    .map((wrapper) => {
      const table = wrapper.querySelector("table");
      if (!table) return null;
      return {
        wrapperClass: wrapper.className,
        wrapperOverflowX: getComputedStyle(wrapper).overflowX,
        wrapperClientWidth: wrapper.clientWidth,
        wrapperScrollWidth: wrapper.scrollWidth,
        tableClientWidth: table.clientWidth,
        tableScrollWidth: table.scrollWidth
      };
    })
    .filter(Boolean);
  return { found: withTables.length > 0, tables: withTables };
});

await page.setViewportSize({ width: 1440, height: 950 });
await page.goto(`${baseUrl}/dashboard`, { waitUntil: "networkidle" });
await screenshot("dashboard-desktop-1440x950.png");

await page.goto(`${baseUrl}/agents`, { waitUntil: "networkidle" });
await screenshot("agents-desktop-1440x950.png");

const agentLink = page.locator("a[href^='/agents/']").first();
if (await agentLink.count()) {
  await agentLink.click();
  await page.waitForLoadState("networkidle");
  await screenshot("agent-detail-desktop-1440x950.png");
}

const result = {
  baseUrl,
  openResult,
  reopenedState,
  sidebarPostCheck,
  jobsOverflowCheck,
  consoleCount: consoleMessages.length,
  consoleMessages,
  networkIssueCount: networkEvents.length,
  networkEvents
};

await fs.writeFile(`${outDir}/validation-results.json`, JSON.stringify(result, null, 2));
await fs.writeFile(
  `${outDir}/console-network-summary.txt`,
  [
    `Console messages: ${consoleMessages.length}`,
    ...consoleMessages.map((m) => `[${m.type}] ${m.url} :: ${m.text}`),
    ``,
    `Network issues (>=400/failed): ${networkEvents.length}`,
    ...networkEvents.map((e) => `[${e.status}] ${e.method} ${e.url}${e.failure ? ` :: ${e.failure}` : ""}`)
  ].join("\n")
);

await browser.close();
