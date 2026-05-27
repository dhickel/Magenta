import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://localhost:18083";
const outDir = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27-image-longname-rerun");
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

const sanitize = (s) => String(s || "").replace(/\s+/g, " ").trim();

const getActionMeta = async (row) => {
  return row.locator("button").evaluateAll((buttons) =>
    buttons.map((btn) => {
      const cs = getComputedStyle(btn);
      const rect = btn.getBoundingClientRect();
      return {
        label: (btn.getAttribute("aria-label") || btn.getAttribute("title") || btn.textContent || "").trim(),
        title: btn.getAttribute("title") || "",
        ariaLabel: btn.getAttribute("aria-label") || "",
        visible: cs.display !== "none" && cs.visibility !== "hidden" && rect.width > 0 && rect.height > 0,
        disabled: btn.hasAttribute("disabled") || btn.getAttribute("aria-disabled") === "true"
      };
    })
  );
};

const openWorkAreaAndFolder = async (page) => {
  await page.goto(summary.route, { waitUntil: "networkidle" });
  await page.waitForTimeout(700);
  await page.locator(".avatar-workarea-entry").first().click({ timeout: 8000 });
  await page.waitForTimeout(700);

  const demoFolderRow = page.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).first();
  const hasDemoFolder = (await demoFolderRow.count()) > 0;
  assertPush("demo-fixtures-folder-visible", hasDemoFolder);
  if (!hasDemoFolder) return;

  await demoFolderRow.click({ position: { x: 14, y: 16 } });
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
    await imageRow.click({ position: { x: 14, y: 16 } });
    await page.waitForTimeout(500);

    const img = page.locator(".avatar-workarea-inspector img, .workspace-inspector img, img[alt*='thumbnail'], img[src*='demo-thumbnail']").first();
    const imgVisible = (await img.count()) > 0 && (await img.isVisible());
    assertPush("inspector-image-visible-desktop", imgVisible);

    if (imgVisible) {
      const fit = await img.evaluate((el) => {
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        const parent = el.parentElement?.getBoundingClientRect();
        const naturalW = (el instanceof HTMLImageElement ? el.naturalWidth : 0) || 0;
        const naturalH = (el instanceof HTMLImageElement ? el.naturalHeight : 0) || 0;
        const renderRatio = r.width > 0 && r.height > 0 ? r.width / r.height : 0;
        const naturalRatio = naturalW > 0 && naturalH > 0 ? naturalW / naturalH : 0;
        const ratioDelta = naturalRatio > 0 ? Math.abs(renderRatio - naturalRatio) : 0;
        const nonZero = r.width > 0 && r.height > 0;
        const objectFit = cs.objectFit || "";
        return { nonZero, objectFit, naturalW, naturalH, renderRatio, naturalRatio, ratioDelta, width: r.width, height: r.height, parentW: parent?.width || 0, parentH: parent?.height || 0 };
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
    const actionMeta = await getActionMeta(longRow);
    const actionVisible = actionMeta.some((a) => a.visible && /(rename|delete|copy|move|view|edit)/i.test(`${a.label} ${a.ariaLabel} ${a.title}`));
    assertPush("long-filename-row-action-accessible-desktop-dom-only", actionVisible, JSON.stringify(actionMeta));
    const geometry = await longRow.evaluate((el) => {
      const nameCell = el.querySelector("td:first-child");
      const nameTextNode = nameCell?.querySelector("span") || nameCell;
      if (!nameCell || !nameTextNode) return { ok: false, reason: "name cell missing" };
      const cellRect = nameCell.getBoundingClientRect();
      const textRect = nameTextNode.getBoundingClientRect();
      const pageOverflow = document.documentElement.scrollWidth - window.innerWidth;
      const text = (nameTextNode.textContent || "").trim();
      return {
        ok: true,
        text,
        textLength: text.length,
        nameCellClientWidth: nameCell.clientWidth,
        nameCellScrollWidth: nameCell.scrollWidth,
        textRectWidth: textRect.width,
        cellRectWidth: cellRect.width,
        textOverlapsActions: textRect.right > cellRect.right + 1,
        pageOverflowPx: pageOverflow
      };
    });
    const acceptable = geometry.ok && !geometry.textOverlapsActions && geometry.pageOverflowPx <= 1;
    assertPush("long-filename-visual-acceptable-desktop", acceptable, JSON.stringify(geometry));
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
    await imageRow.click({ position: { x: 14, y: 16 } });
    await page.waitForTimeout(450);
    const inspectorName = page.locator(".avatar-workarea-inspector, .workspace-inspector").locator("text=demo-thumbnail.png").first();
    await inspectorName.scrollIntoViewIfNeeded().catch(() => {});
    const img = page.locator(".avatar-workarea-inspector img, .workspace-inspector img, img[src*='demo-thumbnail']").first();
    await img.scrollIntoViewIfNeeded().catch(() => {});
    const imgVisible = await img.isVisible().catch(() => false);
    assertPush("inspector-image-visible-mobile", imgVisible);
    if (imgVisible) {
      const fit = await img.evaluate((el) => {
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        const parent = el.parentElement?.getBoundingClientRect();
        return {
          objectFit: cs.objectFit || "",
          nonZero: r.width > 0 && r.height > 0,
          width: r.width,
          height: r.height,
          parentW: parent?.width || 0,
          parentH: parent?.height || 0
        };
      });
      assertPush("inspector-image-contained-mobile", fit.nonZero && fit.objectFit === "contain", JSON.stringify(fit));
    }
  } else {
    assertPush("image-row-visible-mobile", false);
  }

  const longRow = page.locator(".workspace-explorer-row").filter({ hasText: "this-is-a-very-long-file-name-for-browser-truncation-validation-and-actions.txt" }).first();
  if ((await longRow.count()) > 0) {
    await longRow.scrollIntoViewIfNeeded();
    const actionMeta = await getActionMeta(longRow);
    const actionVisible = actionMeta.some((a) => a.visible && /(rename|delete|copy|move|view|edit)/i.test(`${a.label} ${a.ariaLabel} ${a.title}`));
    assertPush("long-filename-row-action-accessible-mobile-dom-only", actionVisible, JSON.stringify(actionMeta));
    const geometry = await longRow.evaluate((el) => {
      const nameCell = el.querySelector("td:first-child");
      const nameTextNode = nameCell?.querySelector("span") || nameCell;
      if (!nameCell || !nameTextNode) return { ok: false, reason: "name cell missing" };
      const cellRect = nameCell.getBoundingClientRect();
      const textRect = nameTextNode.getBoundingClientRect();
      const pageOverflow = document.documentElement.scrollWidth - window.innerWidth;
      const text = (nameTextNode.textContent || "").trim();
      return {
        ok: true,
        text,
        textLength: text.length,
        nameCellClientWidth: nameCell.clientWidth,
        nameCellScrollWidth: nameCell.scrollWidth,
        textRectWidth: textRect.width,
        cellRectWidth: cellRect.width,
        textOverlapsActions: textRect.right > cellRect.right + 1,
        pageOverflowPx: pageOverflow
      };
    });
    const acceptable = geometry.ok && !geometry.textOverlapsActions && geometry.pageOverflowPx <= 1;
    assertPush("long-filename-visual-acceptable-mobile", acceptable, JSON.stringify(geometry));
  } else {
    assertPush("long-filename-row-visible-mobile", false);
  }

  const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
  assertPush("mobile-no-page-horizontal-overflow", noOverflow, `scrollWidth<=innerWidth:${noOverflow}`);

  const mobileShot = "02-mobile-demo-fixtures-image-longname.png";
  await page.evaluate(() => {
    const header = document.querySelector(".avatar-workarea-inspector h3, .workspace-inspector h3, .workspace-inspector, .avatar-workarea-inspector");
    if (header) {
      header.scrollIntoView({ block: "start", behavior: "instant" });
    }
  }).catch(() => {});
  await page.waitForTimeout(200);
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
summary.visualTruncationAssessment = {
  desktop: sanitize(
    summary.assertions.find((a) => a.name === "long-filename-visual-acceptable-desktop")?.details || "not-captured"
  ),
  mobile: sanitize(
    summary.assertions.find((a) => a.name === "long-filename-visual-acceptable-mobile")?.details || "not-captured"
  )
};

await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
console.log(JSON.stringify(summary, null, 2));
