import { chromium, devices } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const OUT = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27");
const URL = "http://localhost:18083/avatar?tab=work-areas";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function clickWorkArea(page) {
  await page.locator(".avatar-workarea-entry").first().click();
  await sleep(900);
}

async function overflow(page) {
  return page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    hasPageOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
  }));
}

async function validate(viewName, contextOptions) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  const consoleIssues = [];
  const badResponses = [];

  page.on("console", (msg) => {
    if (["error", "warning"].includes(msg.type())) consoleIssues.push({ type: msg.type(), text: msg.text() });
  });
  page.on("response", (res) => {
    if (res.status() >= 400) badResponses.push({ status: res.status(), url: res.url(), method: res.request().method() });
  });

  const result = {
    view: viewName,
    screenshots: [],
    checks: {},
    consoleIssues,
    badResponses,
    notes: [],
  };
  const closeModal = async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await page.locator("#avatar-workarea-modal button[aria-label='Close'], #avatar-workarea-modal button:has-text('Cancel'), #avatar-workarea-modal button:has-text('Close')").first().click().catch(() => {});
    await sleep(250);
  };

  await page.goto(URL, { waitUntil: "domcontentloaded" });
  await sleep(1200);
  await clickWorkArea(page);

  await page.screenshot({ path: path.join(OUT, `${viewName}-workarea-open-expanded.png`), fullPage: true });
  result.screenshots.push(`${viewName}-workarea-open-expanded.png`);

  const selectedBefore = (await page.locator(".workspace-inspector-path").first().innerText().catch(() => "")) || "";

  const collapse = page.locator("button[aria-label='Collapse inspector']").first();
  if (await collapse.count()) {
    await collapse.click();
    await sleep(500);
    await page.screenshot({ path: path.join(OUT, `${viewName}-inspector-collapsed.png`), fullPage: true });
    result.screenshots.push(`${viewName}-inspector-collapsed.png`);
  } else {
    result.notes.push("Collapse inspector button not found");
  }

  const expand = page.locator("button[aria-label='Expand inspector']").first();
  if (await expand.count()) {
    await expand.click();
    await sleep(500);
    await page.screenshot({ path: path.join(OUT, `${viewName}-inspector-reexpanded.png`), fullPage: true });
    result.screenshots.push(`${viewName}-inspector-reexpanded.png`);
  } else {
    result.notes.push("Expand inspector button not found");
  }

  const selectedAfter = (await page.locator(".workspace-inspector-path").first().innerText().catch(() => "")) || "";
  result.checks.selectedPathPreserved = selectedBefore.length > 0 && selectedBefore === selectedAfter;
  result.checks.pageOverflow = await overflow(page);

  // Open demo-fixtures for markdown/text fixtures.
  await page.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).locator("button[aria-label='Open folder']").click();
  await sleep(700);
  await page.screenshot({ path: path.join(OUT, `${viewName}-demo-fixtures-table.png`), fullPage: true });
  result.screenshots.push(`${viewName}-demo-fixtures-table.png`);

  // Action buttons presence/labels + non-row-selection behavior.
  const actionSampleRow = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
  await actionSampleRow.click({ position: { x: 20, y: 16 } });
  await sleep(350);
  const selectedPathPreActions = await page.locator(".workspace-inspector-path").first().innerText().catch(() => "");
  const actions = ["View file", "Rename", "Delete", "Copy", "Move"];
  const actionChecks = {};
  for (const action of actions) {
    const btn = actionSampleRow.locator(`button[aria-label='${action}']`).first();
    const found = (await btn.count()) > 0;
    actionChecks[action] = {
      found,
      title: found ? await btn.getAttribute("title") : null,
      ariaLabel: found ? await btn.getAttribute("aria-label") : null,
    };
    if (found && action !== "Delete") {
      await btn.click({ timeout: 3000 }).catch(() => {});
      await sleep(350);
      await closeModal();
    }
  }
  const selectedPathPostActions = await page.locator(".workspace-inspector-path").first().innerText().catch(() => "");
  result.checks.rowActions = actionChecks;
  result.checks.rowActionNoAccidentalSelectionChange = selectedPathPreActions === selectedPathPostActions;

  // Tag set stress on plain-text fixture.
  const textRow = page.locator(".workspace-explorer-row").filter({ hasText: "plain-text-fixture.txt" }).first();
  await textRow.click({ position: { x: 16, y: 16 } });
  await sleep(350);
  await page.locator("button:has-text('Tag Editor')").first().click();
  await sleep(300);
  const tagInput = page.locator("#avatar-workarea-modal input[name='tag']").first();
  if (await tagInput.count()) {
    for (const tag of ["pw-long-tag-alpha-0123456789", "pw-long-tag-beta-abcdefghijklmnopqrstuvwxyz", "pw-nowrap-tag-gamma-very-very-long"]) {
      await tagInput.fill(tag);
      await page.locator("#avatar-workarea-modal button:has-text('Add')").first().click();
      await sleep(200);
    }
    await page.locator("#avatar-workarea-modal button:has-text('Save')").first().click().catch(() => {});
    await sleep(500);
  } else {
    result.notes.push("Tag editor input not found");
  }
  await page.screenshot({ path: path.join(OUT, `${viewName}-long-tags.png`), fullPage: true });
  result.screenshots.push(`${viewName}-long-tags.png`);

  // Preview states.
  const previewText = await page.locator(".workspace-inspector-preview").innerText().catch(() => "");
  result.checks.previewHasUnavailable = /Preview unavailable/i.test(previewText);

  // Open markdown file and validate modal tabs and toolbar.
  const mdRow = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
  await mdRow.locator("button[aria-label='View file']").click();
  await sleep(500);
  await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
  await sleep(400);
  await page.screenshot({ path: path.join(OUT, `${viewName}-markdown-modal-edit.png`), fullPage: true });
  result.screenshots.push(`${viewName}-markdown-modal-edit.png`);

  const modeButtons = {
    edit: await page.locator("#avatar-workarea-modal button:has-text('Edit')").count(),
    preview: await page.locator("#avatar-workarea-modal button:has-text('Preview')").count(),
    split: await page.locator("#avatar-workarea-modal button:has-text('Split')").count(),
  };
  result.checks.markdownModesPresent = modeButtons;

  const modalBox1 = await page.locator("#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell").first().boundingBox().catch(() => null);
  await page.locator("#avatar-workarea-modal button:has-text('Preview')").first().click().catch(() => {});
  await sleep(350);
  await page.locator("#avatar-workarea-modal button:has-text('Split')").first().click().catch(() => {});
  await sleep(350);
  const modalBox2 = await page.locator("#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell").first().boundingBox().catch(() => null);
  result.checks.markdownModalStableDims =
    !!modalBox1 && !!modalBox2 && Math.abs(modalBox1.width - modalBox2.width) < 8 && Math.abs(modalBox1.height - modalBox2.height) < 8;

  // Unsaved preview update + save persistence.
  await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
  const textarea = page.locator("#avatar-workarea-modal textarea").first();
  if (await textarea.count()) {
    await textarea.fill("# pw validation heading\n\n- item one\n- item two\n\n> quote\n\n```txt\ncode\n```");
    await sleep(300);
  }
  await page.locator("#avatar-workarea-modal button:has-text('Preview')").first().click().catch(() => {});
  await sleep(350);
  result.checks.unsavedPreviewUpdated = (await page.locator("#avatar-workarea-modal").innerText()).includes("pw validation heading");
  await page.locator("#avatar-workarea-modal button[aria-label='Save'], #avatar-workarea-modal button[title='Save']").first().click().catch(() => {});
  await sleep(550);
  await closeModal();
  await mdRow.locator("button[aria-label='View file']").click();
  await sleep(450);
  await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
  await sleep(300);
  result.checks.savePersists = (await page.locator("#avatar-workarea-modal").innerText()).includes("pw validation heading");

  // Undo/redo/revert buttons presence and click.
  for (const cmd of ["Undo", "Redo", "Revert"]) {
    const b = page.locator(`#avatar-workarea-modal button[aria-label='${cmd}'], #avatar-workarea-modal button[title='${cmd}']`).first();
    result.checks[`markdown${cmd}Present`] = (await b.count()) > 0;
    if ((await b.count()) > 0) {
      await b.click().catch(() => {});
      await sleep(200);
    }
  }
  await page.screenshot({ path: path.join(OUT, `${viewName}-markdown-modal-modes.png`), fullPage: true });
  result.screenshots.push(`${viewName}-markdown-modal-modes.png`);

  // Plain text editor only edit mode.
  await closeModal();
  await textRow.locator("button[aria-label='View file']").click();
  await sleep(500);
  await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
  await sleep(300);
  const plainPreview = await page.locator("#avatar-workarea-modal button:has-text('Preview')").count();
  const plainSplit = await page.locator("#avatar-workarea-modal button:has-text('Split')").count();
  result.checks.plainTextOnlyEditMode = plainPreview === 0 && plainSplit === 0;
  await page.screenshot({ path: path.join(OUT, `${viewName}-plaintext-modal.png`), fullPage: true });
  result.screenshots.push(`${viewName}-plaintext-modal.png`);

  // Cleanup test tags (best effort).
  await page.keyboard.press("Escape").catch(() => {});
  await sleep(250);
  await textRow.click({ position: { x: 20, y: 16 } });
  await sleep(250);
  await page.locator("button:has-text('Tag Editor')").first().click().catch(() => {});
  await sleep(300);
  for (const tag of ["pw-long-tag-alpha-0123456789", "pw-long-tag-beta-abcdefghijklmnopqrstuvwxyz", "pw-nowrap-tag-gamma-very-very-long"]) {
    await page.locator(`#avatar-workarea-modal button[title='Remove ${tag}'], #avatar-workarea-modal button[aria-label='Remove ${tag}']`).first().click().catch(() => {});
    await sleep(100);
  }
  await page.locator("#avatar-workarea-modal button:has-text('Save')").first().click().catch(() => {});
  await sleep(300);

  result.checks.finalOverflow = await overflow(page);

  await fs.writeFile(path.join(OUT, `${viewName}-deep-summary.json`), JSON.stringify(result, null, 2));
  await browser.close();
  return result;
}

await fs.mkdir(OUT, { recursive: true });
const desktop = await validate("desktop", { viewport: { width: 1440, height: 1000 } });
const mobile = await validate("mobile", { ...devices["iPhone 12"] });
await fs.writeFile(path.join(OUT, "deep-summary.json"), JSON.stringify({ generatedAt: new Date().toISOString(), desktop, mobile }, null, 2));
console.log("done");
