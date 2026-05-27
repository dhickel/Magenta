import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://localhost:18083";
const outDir = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27-image-longname");
await fs.mkdir(outDir, { recursive: true });

const summary = {
  baseUrl,
  route: `${baseUrl}/avatar?tab=work-areas`,
  startedAt: new Date().toISOString(),
  assertions: [],
  consoleErrors: [],
  requestFailures: [],
  screenshots: [],
  pass: true
};

const assertPush = (name, pass, details = "") => {
  summary.assertions.push({ name, pass, details });
  if (!pass) summary.pass = false;
};

const noteShot = (name) => summary.screenshots.push(name);

const bindPageListeners = (page, tag = "") => {
  page.on("console", (msg) => {
    if (msg.type() === "error") summary.consoleErrors.push(`${tag}${msg.text()}`);
  });
  page.on("requestfailed", (req) => {
    summary.requestFailures.push({ url: req.url(), failure: `${tag}${req.failure()?.errorText || "unknown"}` });
  });
  page.on("response", (res) => {
    if (res.status() >= 400) summary.requestFailures.push({ url: res.url(), failure: `${tag}HTTP ${res.status()}` });
  });
};

const dismissBlockingModalIfPresent = async (page) => {
  const closeBtn = page.locator(".avatar-modal button:has-text('Close'), .avatar-modal [aria-label='Close']").first();
  if ((await closeBtn.count()) > 0) {
    await closeBtn.click().catch(() => {});
    await page.waitForTimeout(250);
  }
};

const openWorkAreaAndFolder = async (page) => {
  await page.goto(summary.route, { waitUntil: "networkidle" });
  await page.waitForTimeout(700);
  await dismissBlockingModalIfPresent(page);
  await page.locator(".avatar-workarea-entry").first().click({ timeout: 8000 });
  await page.waitForTimeout(700);

  const demoFolderRow = page.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).first();
  const hasDemoFolder = (await demoFolderRow.count()) > 0;
  assertPush("demo-fixtures-folder-visible", hasDemoFolder);
  if (!hasDemoFolder) return;

  const openBtn = demoFolderRow.locator("button[aria-label='Open folder']").first();
  if ((await openBtn.count()) > 0) {
    await openBtn.click();
  } else {
    await demoFolderRow.click({ position: { x: 24, y: 16 } });
  }
  await page.waitForTimeout(600);
};

const validateDesktop = async (browser) => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  bindPageListeners(page);
  await openWorkAreaAndFolder(page);

  const imageRow = page.locator(".workspace-explorer-row").filter({ hasText: "demo-thumbnail.png" }).first();
  const hasImageRow = (await imageRow.count()) > 0;
  assertPush("image-row-visible-desktop", hasImageRow);
  if (hasImageRow) {
    await imageRow.scrollIntoViewIfNeeded();
    await imageRow.click();
    await page.waitForTimeout(500);

    const img = page.locator(".avatar-workarea-inspector img, .workspace-inspector img, img[alt*='thumbnail'], img[src*='demo-thumbnail']").first();
    const imgVisible = (await img.count()) > 0 && (await img.isVisible());
    assertPush("inspector-image-visible-desktop", imgVisible);

    if (imgVisible) {
      const fit = await img.evaluate((el) => {
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        const naturalW = (el instanceof HTMLImageElement ? el.naturalWidth : 0) || 0;
        const naturalH = (el instanceof HTMLImageElement ? el.naturalHeight : 0) || 0;
        const renderRatio = r.width > 0 && r.height > 0 ? r.width / r.height : 0;
        const naturalRatio = naturalW > 0 && naturalH > 0 ? naturalW / naturalH : 0;
        const ratioDelta = naturalRatio > 0 ? Math.abs(renderRatio - naturalRatio) : 0;
        const nonZero = r.width > 0 && r.height > 0;
        const objectFit = cs.objectFit || "";
        return { nonZero, objectFit, naturalW, naturalH, renderRatio, naturalRatio, ratioDelta, width: r.width, height: r.height };
      });
      const looksContained = fit.nonZero && fit.objectFit === "contain" && fit.ratioDelta < 0.05;
      assertPush("inspector-image-contained-desktop", looksContained, JSON.stringify(fit));
    }
  }

  const longRow = page.locator(".workspace-explorer-row").filter({ hasText: "this-is-a-very-long-file-name-for-browser-truncation-validation-and-actions.txt" }).first();
  const hasLongRow = (await longRow.count()) > 0;
  assertPush("long-filename-row-visible-desktop", hasLongRow);
  if (hasLongRow) {
    await longRow.scrollIntoViewIfNeeded();
    const actionBtn = longRow.locator("button[aria-label='View file'], button[aria-label='Edit file'], button[aria-label='Rename file'], button[aria-label='Delete file']").first();
    const actionVisible = (await actionBtn.count()) > 0 && (await actionBtn.isVisible());
    assertPush("long-filename-row-action-accessible-desktop", actionVisible);
    const truncation = await longRow.evaluate((el) => {
      const nameCell = el.querySelector("td:first-child span, td:first-child");
      if (!nameCell) return { truncated: false, reason: "name cell missing" };
      const text = (nameCell.textContent || "").trim();
      const truncated = nameCell.scrollWidth > nameCell.clientWidth || text.includes("...");
      return { truncated, text };
    });
    assertPush("long-filename-truncates-or-compresses-desktop", truncation.truncated, JSON.stringify(truncation));
  }

  const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
  assertPush("desktop-no-page-horizontal-overflow", noOverflow, `scrollWidth<=innerWidth:${noOverflow}`);

  const desktopShot = "01-desktop-demo-fixtures-image-longname.png";
  await page.screenshot({ path: path.join(outDir, desktopShot), fullPage: true });
  noteShot(desktopShot);

  await context.close();
};

const validateMobile = async (browser) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  bindPageListeners(page, "[mobile] ");
  await openWorkAreaAndFolder(page);

  const imageRow = page.locator(".workspace-explorer-row").filter({ hasText: "demo-thumbnail.png" }).first();
  if ((await imageRow.count()) > 0) {
    await imageRow.scrollIntoViewIfNeeded();
    await imageRow.click();
    await page.waitForTimeout(450);
    const inspectorName = page.locator(".avatar-workarea-inspector, .workspace-inspector").locator("text=demo-thumbnail.png").first();
    await inspectorName.scrollIntoViewIfNeeded().catch(() => {});
    const img = page.locator(".avatar-workarea-inspector img, .workspace-inspector img, img[src*='demo-thumbnail']").first();
    await img.scrollIntoViewIfNeeded().catch(() => {});
    const imgVisible = await img.isVisible().catch(() => false);
    assertPush("inspector-image-visible-mobile", imgVisible);
  } else {
    assertPush("image-row-visible-mobile", false);
  }

  const longRow = page.locator(".workspace-explorer-row").filter({ hasText: "this-is-a-very-long-file-name-for-browser-truncation-validation-and-actions.txt" }).first();
  if ((await longRow.count()) > 0) {
    await longRow.scrollIntoViewIfNeeded();
    const actionBtn = longRow.locator("button[aria-label='View file'], button[aria-label='Edit file'], button[aria-label='Rename file'], button[aria-label='Delete file']").first();
    const actionVisible = (await actionBtn.count()) > 0 && (await actionBtn.isVisible());
    assertPush("long-filename-row-action-accessible-mobile", actionVisible);
  } else {
    assertPush("long-filename-row-visible-mobile", false);
  }

  const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
  assertPush("mobile-no-page-horizontal-overflow", noOverflow, `scrollWidth<=innerWidth:${noOverflow}`);

  const mobileShot = "02-mobile-demo-fixtures-image-longname.png";
  await page.screenshot({ path: path.join(outDir, mobileShot), fullPage: true });
  noteShot(mobileShot);

  await context.close();
};

const browser = await chromium.launch({ headless: true });
try {
  await validateDesktop(browser);
  await validateMobile(browser);
} finally {
  await browser.close();
}

summary.finishedAt = new Date().toISOString();
summary.consoleErrorCount = summary.consoleErrors.length;
summary.requestFailureCount = summary.requestFailures.length;
if (summary.consoleErrorCount > 0 || summary.requestFailureCount > 0) summary.pass = false;

await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
console.log(JSON.stringify(summary, null, 2));
