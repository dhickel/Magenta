import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL || "http://localhost:18083";
const outDir = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27-final");
await fs.mkdir(outDir, { recursive: true });

const summary = {
  baseUrl,
  startedAt: new Date().toISOString(),
  assertions: [],
  consoleErrors: [],
  requestFailures: [],
  screenshots: [],
  fixtureLimitations: [],
  pass: true
};

const assertPush = (name, pass, details = "") => {
  summary.assertions.push({ name, pass, details });
  if (!pass) summary.pass = false;
};

const recordShot = (name) => {
  summary.screenshots.push(name);
};

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();

page.on("console", (msg) => {
  if (msg.type() === "error") summary.consoleErrors.push(msg.text());
});
page.on("requestfailed", (req) => {
  summary.requestFailures.push({ url: req.url(), failure: req.failure()?.errorText || "unknown" });
});
page.on("response", (res) => {
  const s = res.status();
  if (s >= 400) summary.requestFailures.push({ url: res.url(), failure: `HTTP ${s}` });
});

const route = `${baseUrl}/avatar?tab=work-areas`;
await page.goto(route, { waitUntil: "networkidle" });
await page.waitForTimeout(700);
await page.locator(".avatar-workarea-entry").first().click({ timeout: 6000 });
await page.waitForTimeout(800);
const demoFolderRow = page.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).first();
if ((await demoFolderRow.count()) > 0) {
  const openFolderBtn = demoFolderRow.locator("button[aria-label='Open folder']").first();
  if ((await openFolderBtn.count()) > 0) {
    await openFolderBtn.click();
  } else {
    await demoFolderRow.click({ position: { x: 24, y: 16 } });
  }
  await page.waitForTimeout(500);
}

await page.screenshot({ path: path.join(outDir, "01-workareas-desktop-initial.png"), fullPage: true });
recordShot("01-workareas-desktop-initial.png");

const noHOverflowDesktop = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
assertPush("desktop-no-horizontal-overflow", noHOverflowDesktop, `scrollWidth<=innerWidth: ${noHOverflowDesktop}`);

const rowBriefing = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
await rowBriefing.scrollIntoViewIfNeeded();
await rowBriefing.click();
await page.waitForTimeout(400);

const viewButton = rowBriefing.locator("button[aria-label='View file']").first();
await viewButton.click();
await page.waitForSelector("#avatar-workarea-modal .avatar-modal, #avatar-workarea-modal .avatar-modal-workarea-editor", { timeout: 8000 });
await page.waitForTimeout(500);
const openEditBtn = page.locator("#avatar-workarea-modal button:has-text('Edit')").first();
if ((await openEditBtn.count()) > 0) {
  await openEditBtn.click();
  await page.waitForTimeout(250);
}

await page.screenshot({ path: path.join(outDir, "02-markdown-editor-edit-desktop.png"), fullPage: true });
recordShot("02-markdown-editor-edit-desktop.png");

const navOverEditor = await page.evaluate(() => {
  const modal = document.querySelector(".avatar-modal-workarea-editor");
  const nav = document.querySelector("header, nav, .top-nav, .avatar-shell-topbar");
  if (!modal || !nav) return { ok: true, details: "modal or nav not found for overlap check" };
  const r = modal.getBoundingClientRect();
  const x = Math.floor(r.left + 24);
  const y = Math.floor(r.top + 24);
  const top = document.elementFromPoint(x, y);
  const topInModal = !!top?.closest("#avatar-workarea-modal");
  return { ok: topInModal, details: `topTag:${top?.tagName || "none"}, topClass:${top?.className || ""}` };
});
assertPush("editor-panel-over-global-nav", !!navOverEditor.ok, navOverEditor.details);

const hasMarkdownShell = (await page.locator(".avatar-markdown-editor-shell").count()) > 0;
assertPush("markdown-editor-shell-exists", hasMarkdownShell, `.avatar-markdown-editor-shell count > 0: ${hasMarkdownShell}`);

const controls = [
  "Save",
  "Undo",
  "Redo",
  "Revert Unsaved",
  "Close"
];
for (const name of controls) {
  const aria = page.locator(`[aria-label='${name}'], [title='${name}']`).first();
  const ok = (await aria.count()) > 0;
  assertPush(`control-present-${name}`, ok);
}

const previewTab = page.locator("#avatar-workarea-modal button:has-text('Preview')").first();
await previewTab.click();
await page.waitForTimeout(500);
await page.screenshot({ path: path.join(outDir, "03-markdown-editor-preview-desktop.png"), fullPage: true });
recordShot("03-markdown-editor-preview-desktop.png");

const splitTab = page.locator("#avatar-workarea-modal button:has-text('Split')").first();
await splitTab.click();
await page.waitForTimeout(400);
const splitSize = await page.locator(".avatar-modal-workarea-editor, .avatar-modal").first().boundingBox();
await previewTab.click();
await page.waitForTimeout(350);
const previewSize = await page.locator(".avatar-modal-workarea-editor, .avatar-modal").first().boundingBox();
const editTab = page.locator("#avatar-workarea-modal button:has-text('Edit')").first();
await editTab.click();
await page.waitForTimeout(350);
const editSize = await page.locator(".avatar-modal-workarea-editor, .avatar-modal").first().boundingBox();

const stableDims =
  !!splitSize &&
  !!previewSize &&
  !!editSize &&
  Math.abs(splitSize.width - previewSize.width) < 40 &&
  Math.abs(splitSize.height - previewSize.height) < 40 &&
  Math.abs(editSize.width - previewSize.width) < 40 &&
  Math.abs(editSize.height - previewSize.height) < 40;
assertPush("markdown-modal-dimensions-stable-across-modes", stableDims, JSON.stringify({ splitSize, previewSize, editSize }));

const editorLocator = page.locator("#avatar-workarea-modal textarea[data-editor-source='true'], #avatar-workarea-modal textarea").first();
await editorLocator.fill((await editorLocator.inputValue()) + "\n\nUnsaved preview probe line.");
await page.waitForTimeout(250);
await previewTab.click();
await page.waitForTimeout(400);
const previewUpdated = await page.locator("text=Unsaved preview probe line.").count();
assertPush("markdown-unsaved-preview-updates", previewUpdated > 0, `count=${previewUpdated}`);

const closeButton = page.locator(`#avatar-workarea-modal [aria-label='Close'], #avatar-workarea-modal [title='Close']`).first();
await closeButton.click();
await page.waitForTimeout(500);
const modalHostCount = await page.locator("#avatar-workarea-modal").count();
const modalChildren = await page.locator("#avatar-workarea-modal .avatar-modal").count();
assertPush("single-modal-host-remains-after-close", modalHostCount === 1, `count=${modalHostCount}`);
assertPush("modal-host-cleared-after-close", modalChildren === 0, `.avatar-modal children=${modalChildren}`);

const rowPlain = page.locator(".workspace-explorer-row").filter({ hasText: "plain-text-fixture.txt" }).first();
await rowPlain.scrollIntoViewIfNeeded();
await rowPlain.click();
await page.waitForTimeout(450);
const pointerOk = await rowPlain.isVisible();
assertPush("no-stale-modal-subtree-intercepts-plain-text-row", pointerOk, `plain row visible after click=${pointerOk}`);

const inspectorBounded = await page.evaluate(() => {
  const p = [...document.querySelectorAll(".avatar-workarea-inspector-preview-text, .workspace-inspector-preview, .avatar-workarea-inspector pre, .avatar-workarea-preview pre, pre")]
    .find((el) => (el.textContent || "").toLowerCase().includes("fixture"));
  if (!p) return { ok: false, details: "plain text preview block not found" };
  const cs = getComputedStyle(p);
  return { ok: /(auto|hidden|scroll)/.test(cs.overflowY) || p.scrollHeight > p.clientHeight, details: `overflowY=${cs.overflowY}, h=${p.clientHeight}/${p.scrollHeight}` };
});
assertPush("plain-text-inspector-preview-bounded", inspectorBounded.ok, inspectorBounded.details);

await rowPlain.locator("button[aria-label='View file']").first().click();
await page.waitForSelector("#avatar-workarea-modal .avatar-modal, #avatar-workarea-modal .avatar-modal-workarea-editor", { timeout: 8000 });
await page.waitForTimeout(400);
await page.screenshot({ path: path.join(outDir, "04-plaintext-editor-desktop.png"), fullPage: true });
recordShot("04-plaintext-editor-desktop.png");

const hasPreviewOrSplitInPlainText =
  (await page.locator("#avatar-workarea-modal button:has-text('Preview')").count()) > 0 ||
  (await page.locator("#avatar-workarea-modal button:has-text('Split')").count()) > 0;
assertPush("plain-text-editor-edit-only", !hasPreviewOrSplitInPlainText, `hasPreviewOrSplit=${hasPreviewOrSplitInPlainText}`);

await closeButton.click();
await page.waitForTimeout(450);
const modalChildrenAfterPlainClose = await page.locator("#avatar-workarea-modal .avatar-modal").count();
assertPush("plain-text-close-clears-modal-host", modalChildrenAfterPlainClose === 0, `.avatar-modal children=${modalChildrenAfterPlainClose}`);

await rowBriefing.click({ position: { x: 24, y: 16 } });
await page.waitForTimeout(300);
await rowBriefing.locator("button[aria-label='View file']").first().click();
await page.waitForTimeout(500);
await page.screenshot({ path: path.join(outDir, "05-markdown-editor-reopen-desktop.png"), fullPage: true });
recordShot("05-markdown-editor-reopen-desktop.png");

const markdownSpacingOk = await page.evaluate(() => {
  const previewRoot = document.querySelector(".avatar-markdown-editor-shell, .markdown-body, .avatar-markdown-preview");
  if (!previewRoot) return { ok: false, details: "preview root missing" };
  const p = previewRoot.querySelector("p");
  const ul = previewRoot.querySelector("ul, ol");
  const code = previewRoot.querySelector("pre, code");
  return { ok: !!(p || ul || code), details: `p:${!!p}, list:${!!ul}, code:${!!code}` };
});
assertPush("markdown-preview-spacing-acceptable", markdownSpacingOk.ok, markdownSpacingOk.details);

const imageFixtureExists = await page.locator(".workspace-explorer-row").filter({ hasText: /\.(png|jpg|jpeg|gif|webp)/i }).count();
if (imageFixtureExists === 0) {
  summary.fixtureLimitations.push("No image fixture row detected in current workarea listing.");
}

const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } });
const mobilePage = await mobile.newPage();
mobilePage.on("console", (msg) => {
  if (msg.type() === "error") summary.consoleErrors.push(`[mobile] ${msg.text()}`);
});
mobilePage.on("requestfailed", (req) => {
  summary.requestFailures.push({ url: req.url(), failure: `[mobile] ${req.failure()?.errorText || "unknown"}` });
});
mobilePage.on("response", (res) => {
  if (res.status() >= 400) summary.requestFailures.push({ url: res.url(), failure: `[mobile] HTTP ${res.status()}` });
});

await mobilePage.goto(route, { waitUntil: "networkidle" });
await mobilePage.waitForTimeout(600);
await mobilePage.locator(".avatar-workarea-entry").first().click({ timeout: 6000 });
await mobilePage.waitForTimeout(700);
const mDemoFolderRow = mobilePage.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).first();
if ((await mDemoFolderRow.count()) > 0) {
  const mOpenFolderBtn = mDemoFolderRow.locator("button[aria-label='Open folder']").first();
  if ((await mOpenFolderBtn.count()) > 0) {
    await mOpenFolderBtn.click();
  } else {
    await mDemoFolderRow.click({ position: { x: 24, y: 16 } });
  }
  await mobilePage.waitForTimeout(450);
}
const mobileNoOverflow = await mobilePage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
assertPush("mobile-no-horizontal-overflow", mobileNoOverflow, `scrollWidth<=innerWidth: ${mobileNoOverflow}`);
await mobilePage.screenshot({ path: path.join(outDir, "06-workareas-mobile.png"), fullPage: true });
recordShot("06-workareas-mobile.png");

const mobileBrief = mobilePage.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
await mobileBrief.scrollIntoViewIfNeeded();
const mobileView = mobileBrief.locator("button[aria-label='View file']").first();
try {
  await mobileView.click({ timeout: 4000 });
  await mobilePage.waitForTimeout(550);
  await mobilePage.screenshot({ path: path.join(outDir, "07-markdown-editor-mobile.png"), fullPage: true });
  recordShot("07-markdown-editor-mobile.png");
  assertPush("mobile-markdown-modal-opened", true);
} catch (error) {
  assertPush("mobile-markdown-modal-opened", false, String(error));
}

await mobile.close();
await context.close();
await browser.close();

summary.finishedAt = new Date().toISOString();
summary.consoleErrorCount = summary.consoleErrors.length;
summary.requestFailureCount = summary.requestFailures.length;

await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
console.log(JSON.stringify({ outDir, pass: summary.pass, assertions: summary.assertions.length, consoleErrors: summary.consoleErrorCount, requestFailures: summary.requestFailureCount }, null, 2));
