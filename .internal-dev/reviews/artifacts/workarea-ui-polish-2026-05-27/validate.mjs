import { chromium, devices } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const OUT_DIR = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27");
const BASE_URL = "http://localhost:18083/avatar?tab=work-areas";

async function ensureDir() {
  await fs.mkdir(OUT_DIR, { recursive: true });
}

function pickFirst(page, selectors) {
  return selectors.map((s) => page.locator(s)).find((l) => l);
}

async function firstVisible(page, selectors) {
  for (const sel of selectors) {
    const l = page.locator(sel).first();
    if (await l.count()) {
      if (await l.isVisible().catch(() => false)) return { sel, l };
    }
  }
  return null;
}

async function collectOverflow(page) {
  return page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    hasPageOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
  }));
}

async function runViewport(name, contextOptions) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  const consoleMsgs = [];
  const networkFails = [];
  const badResponses = [];

  page.on("console", (m) => {
    if (["error", "warning"].includes(m.type())) {
      consoleMsgs.push({ type: m.type(), text: m.text() });
    }
  });
  page.on("requestfailed", (r) => {
    networkFails.push({ url: r.url(), method: r.method(), failure: r.failure()?.errorText });
  });
  page.on("response", async (res) => {
    const status = res.status();
    if (status >= 400) {
      badResponses.push({ url: res.url(), status, method: res.request().method() });
    }
  });

  const summary = {
    viewport: name,
    checks: {},
    errors: [],
    screenshots: [],
    consoleMsgs,
    networkFails,
    badResponses,
  };

  await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1200);

  const browseButton = await firstVisible(page, [
    "button:has-text('Browse')",
    "button:has-text('Open Browser')",
    "[data-testid='workarea-browse']",
    "button[title*='Browse']",
  ]);
  if (browseButton) {
    await browseButton.l.click();
    await page.waitForTimeout(800);
  }

  const inspectorHeader = await firstVisible(page, [
    "h2:has-text('Inspector')",
    "h3:has-text('Inspector')",
    "[data-testid='workarea-inspector']",
  ]);
  if (inspectorHeader) {
    const card = inspectorHeader.l.locator("xpath=ancestor::*[self::section or self::div][1]");
    await card.scrollIntoViewIfNeeded().catch(() => {});
  }

  const expandedShot = `${name}-01-browser-inspector-expanded.png`;
  await page.screenshot({ path: path.join(OUT_DIR, expandedShot), fullPage: true });
  summary.screenshots.push(expandedShot);

  const collapseBtn = await firstVisible(page, [
    "button[aria-label*='Collapse inspector']",
    "button[title*='Collapse inspector']",
    "button:has-text('Collapse')",
    "button:has([data-lucide='chevron-right'])",
    "button:has([data-lucide='panel-right-close'])",
  ]);
  let selectedPathBefore = "";
  const selectedPathEl = await firstVisible(page, [
    "[data-testid='workarea-selected-path']",
    "text=/Selected Path|Path:/i",
  ]);
  if (selectedPathEl) {
    selectedPathBefore = (await selectedPathEl.l.textContent())?.trim() || "";
  }
  if (collapseBtn) {
    await collapseBtn.l.click();
    await page.waitForTimeout(500);
  }
  const collapsedShot = `${name}-02-inspector-collapsed.png`;
  await page.screenshot({ path: path.join(OUT_DIR, collapsedShot), fullPage: true });
  summary.screenshots.push(collapsedShot);

  const expandBtn = await firstVisible(page, [
    "button[aria-label*='Expand inspector']",
    "button[title*='Expand inspector']",
    "button:has-text('Expand')",
    "button:has([data-lucide='chevron-left'])",
    "button:has([data-lucide='panel-right-open'])",
  ]);
  if (expandBtn) {
    await expandBtn.l.click();
    await page.waitForTimeout(500);
  }
  const reexpandedShot = `${name}-03-inspector-reexpanded.png`;
  await page.screenshot({ path: path.join(OUT_DIR, reexpandedShot), fullPage: true });
  summary.screenshots.push(reexpandedShot);

  let selectedPathAfter = "";
  if (selectedPathEl) {
    selectedPathAfter = (await selectedPathEl.l.textContent())?.trim() || "";
  }
  summary.checks.selectedPathPreserved = !!selectedPathBefore && selectedPathBefore === selectedPathAfter;

  const newFileBtn = await firstVisible(page, [
    "button:has-text('New File')",
    "button:has-text('Create File')",
    "button[title*='New File']",
  ]);
  if (newFileBtn) {
    await newFileBtn.l.click();
    await page.waitForTimeout(350);
    const nameInput = await firstVisible(page, [
      "input[name='name']",
      "input[placeholder*='filename']",
      "input[placeholder*='File name']",
      "input[type='text']",
    ]);
    if (nameInput) {
      await nameInput.l.fill("pw-very-long-file-name-for-overflow-and-preview-validation-abcdefghijklmnopqrstuvwxyz-0123456789.md");
      const submit = await firstVisible(page, [
        "button[type='submit']",
        "button:has-text('Create')",
        "button:has-text('Save')",
      ]);
      if (submit) {
        await submit.l.click();
        await page.waitForTimeout(800);
      }
    }
  }

  const overflow = await collectOverflow(page);
  summary.checks.overflow = overflow;

  const row = page.locator("tr, [role='row'], li").filter({ hasText: "pw-very-long-file-name-for-overflow-and-preview-validation" }).first();
  if (await row.count()) {
    await row.click({ position: { x: 5, y: 5 } }).catch(() => {});
    await page.waitForTimeout(500);
  }

  const actionChecks = {};
  const actions = [
    { key: "open", terms: ["Open", "View"] },
    { key: "rename", terms: ["Rename"] },
    { key: "delete", terms: ["Delete"] },
    { key: "copy", terms: ["Copy"] },
    { key: "move", terms: ["Move"] },
  ];
  for (const a of actions) {
    let loc = null;
    for (const term of a.terms) {
      const m = await firstVisible(page, [
        `button[aria-label*='${term}']`,
        `button[title*='${term}']`,
        `button:has-text('${term}')`,
      ]);
      if (m) {
        loc = m;
        break;
      }
    }
    if (loc) {
      const aria = await loc.l.getAttribute("aria-label");
      const title = await loc.l.getAttribute("title");
      actionChecks[a.key] = { found: true, ariaLabel: aria, title };
      await loc.l.click().catch(() => {});
      await page.waitForTimeout(250);
    } else {
      actionChecks[a.key] = { found: false };
    }
  }
  summary.checks.rowActions = actionChecks;

  const previewStates = {};
  const unavailable = page.locator("text=/Preview unavailable/i").first();
  previewStates.unavailableVisible = (await unavailable.count()) > 0;
  const img = page.locator(".workarea-preview img, [data-testid='workarea-preview'] img").first();
  previewStates.imagePreviewFound = (await img.count()) > 0;
  const textPreview = page.locator("pre, code").filter({ hasText: "pw-text-preview" }).first();
  previewStates.textPreviewFound = (await textPreview.count()) > 0;
  previewStates.markdownRendered = (await page.locator("blockquote, ul, ol, .markdown-preview pre code").count()) > 0;
  summary.checks.previewStates = previewStates;

  const mdEditBtn = await firstVisible(page, [
    "button[aria-label*='Edit']",
    "button[title*='Edit']",
    "button:has-text('Edit')",
  ]);
  if (mdEditBtn) {
    await mdEditBtn.l.click().catch(() => {});
    await page.waitForTimeout(600);
    const mdModal = `${name}-04-markdown-modal-open.png`;
    await page.screenshot({ path: path.join(OUT_DIR, mdModal), fullPage: true });
    summary.screenshots.push(mdModal);

    for (const tab of ["Preview", "Split", "Edit"]) {
      const tabBtn = await firstVisible(page, [`button:has-text('${tab}')`, `[role='tab']:has-text('${tab}')`]);
      if (tabBtn) {
        await tabBtn.l.click();
        await page.waitForTimeout(300);
      }
    }

    const afterTabs = `${name}-05-markdown-modal-tabs.png`;
    await page.screenshot({ path: path.join(OUT_DIR, afterTabs), fullPage: true });
    summary.screenshots.push(afterTabs);

    const textarea = page.locator("textarea").first();
    if (await textarea.count()) {
      await textarea.fill("# pw header\n\n- one\n- two\n\n> quote\n\n```txt\ncode\n```");
      await page.waitForTimeout(250);
    }
    const saveBtn = await firstVisible(page, [
      "button[aria-label*='Save']",
      "button[title*='Save']",
      "button:has-text('Save')",
    ]);
    if (saveBtn) {
      await saveBtn.l.click().catch(() => {});
      await page.waitForTimeout(500);
    }
  }

  const modalClose = await firstVisible(page, [
    "button[aria-label*='Close']",
    "button[title*='Close']",
    "button:has-text('Close')",
  ]);
  if (modalClose) {
    await modalClose.l.click().catch(() => {});
    await page.waitForTimeout(250);
  } else {
    await page.keyboard.press("Escape").catch(() => {});
  }

  await fs.writeFile(path.join(OUT_DIR, `${name}-summary.json`), JSON.stringify(summary, null, 2));
  await browser.close();
  return summary;
}

await ensureDir();
const desktop = await runViewport("desktop", { viewport: { width: 1440, height: 1000 } });
const mobile = await runViewport("mobile", { ...devices["iPhone 12"] });
const combined = { generatedAt: new Date().toISOString(), desktop, mobile };
await fs.writeFile(path.join(OUT_DIR, "summary.json"), JSON.stringify(combined, null, 2));
console.log(`Wrote summary and screenshots to ${OUT_DIR}`);
