import { chromium, devices } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const OUT = path.resolve(".internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27");
const URL = "http://localhost:18083/avatar?tab=work-areas";
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function run(view, ctxOpts) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext(ctxOpts);
  const page = await context.newPage();
  const res = { view, checks: {}, screenshots: [], steps: [], console: [], network4xx5xx: [] };
  page.on("console", (m) => ["error", "warning"].includes(m.type()) && res.console.push(`${m.type()}: ${m.text()}`));
  page.on("response", (r) => r.status() >= 400 && res.network4xx5xx.push(`${r.status()} ${r.request().method()} ${r.url()}`));

  const step = async (name, fn) => {
    try { await fn(); res.steps.push({ name, ok: true }); } catch (e) { res.steps.push({ name, ok: false, error: String(e.message || e) }); }
  };
  const shot = async (name) => { await page.screenshot({ path: path.join(OUT, `${view}-${name}.png`), fullPage: true }); res.screenshots.push(`${view}-${name}.png`); };
  const closeModal = async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await page.locator("#avatar-workarea-modal button[aria-label='Close'], #avatar-workarea-modal button:has-text('Close'), #avatar-workarea-modal button:has-text('Cancel')").first().click({ timeout: 800 }).catch(() => {});
    await wait(220);
  };

  await page.goto(URL);
  await wait(1200);
  await page.locator(".avatar-workarea-entry").first().click();
  await wait(900);
  await shot("01-open-expanded");

  const overflow1 = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  res.checks.pageOverflowInitial = overflow1[0] > overflow1[1];

  await step("collapse-expand", async () => {
    await page.locator("button[aria-label='Collapse inspector']").click({ timeout: 2000 });
    await wait(450);
    await shot("02-collapsed");
    await page.locator("button[aria-label='Expand inspector']").click({ timeout: 2000 });
    await wait(450);
    await shot("03-reexpanded");
  });

  await step("open-demo-fixtures", async () => {
    await page.locator(".workspace-explorer-row").filter({ hasText: "demo-fixtures" }).locator("button[aria-label='Open folder']").click();
    await wait(700);
    await shot("04-demo-fixtures");
  });

  await step("row-actions-aria", async () => {
    const row = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
    const actions = ["View file", "Rename", "Delete", "Copy", "Move"];
    const out = {};
    for (const a of actions) {
      const b = row.locator(`button[aria-label='${a}']`).first();
      out[a] = (await b.count()) > 0 ? { title: await b.getAttribute("title"), aria: await b.getAttribute("aria-label") } : null;
      if (a !== "Delete" && (await b.count()) > 0) {
        await b.click({ timeout: 1200 }).catch(() => {});
        await wait(260);
        await closeModal();
      }
    }
    res.checks.rowActions = out;
  });

  await step("preview-unavailable-text-markdown", async () => {
    const txtRow = page.locator(".workspace-explorer-row").filter({ hasText: "plain-text-fixture.txt" }).first();
    await txtRow.click({ position: { x: 20, y: 15 } });
    await wait(350);
    res.checks.previewUnavailableOnText = /Preview unavailable/i.test(await page.locator(".workspace-inspector-preview").innerText().catch(() => ""));
    const mdRow = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
    await mdRow.click({ position: { x: 20, y: 15 } });
    await wait(350);
    const ptxt = await page.locator(".workspace-inspector-preview").innerText().catch(() => "");
    res.checks.markdownPreviewRendered = /-|\d+\.|#|quote|code/i.test(ptxt);
    await shot("05-inspector-preview-states");
  });

  await step("markdown-modal", async () => {
    const mdRow = page.locator(".workspace-explorer-row").filter({ hasText: "briefing.md" }).first();
    await mdRow.locator("button[aria-label='View file']").click();
    await wait(450);
    await page.locator("#avatar-workarea-modal button:has-text('Edit')").click({ timeout: 1200 }).catch(() => {});
    await wait(300);
    res.checks.markdownTabs = {
      edit: await page.locator("#avatar-workarea-modal button:has-text('Edit')").count(),
      preview: await page.locator("#avatar-workarea-modal button:has-text('Preview')").count(),
      split: await page.locator("#avatar-workarea-modal button:has-text('Split')").count(),
    };
    await shot("06-markdown-modal-edit");
    await page.locator("#avatar-workarea-modal button:has-text('Preview')").click().catch(() => {});
    await wait(260);
    await page.locator("#avatar-workarea-modal button:has-text('Split')").click().catch(() => {});
    await wait(260);
    await shot("07-markdown-modal-tabs");
    const ta = page.locator("#avatar-workarea-modal textarea").first();
    if (await ta.count()) {
      await page.locator("#avatar-workarea-modal button:has-text('Edit')").click().catch(() => {});
      await ta.fill("# pw heading\n\n- one\n- two\n\n> quote\n\n```txt\ncode\n```");
      await wait(200);
      await page.locator("#avatar-workarea-modal button:has-text('Preview')").click().catch(() => {});
      await wait(250);
      res.checks.unsavedPreviewShowsChange = (await page.locator("#avatar-workarea-modal").innerText()).includes("pw heading");
      await page.locator("#avatar-workarea-modal button[aria-label='Save'], #avatar-workarea-modal button[title='Save']").first().click().catch(() => {});
      await wait(450);
    }
    res.checks.markdownToolbar = {
      save: await page.locator("#avatar-workarea-modal button[aria-label='Save'], #avatar-workarea-modal button[title='Save']").count(),
      undo: await page.locator("#avatar-workarea-modal button[aria-label='Undo'], #avatar-workarea-modal button[title='Undo']").count(),
      redo: await page.locator("#avatar-workarea-modal button[aria-label='Redo'], #avatar-workarea-modal button[title='Redo']").count(),
      revert: await page.locator("#avatar-workarea-modal button[aria-label='Revert'], #avatar-workarea-modal button[title='Revert']").count(),
      close: await page.locator("#avatar-workarea-modal button[aria-label='Close'], #avatar-workarea-modal button[title='Close']").count(),
    };
    await closeModal();
  });

  await step("plaintext-modal-only-edit", async () => {
    const txtRow = page.locator(".workspace-explorer-row").filter({ hasText: "plain-text-fixture.txt" }).first();
    await txtRow.locator("button[aria-label='View file']").click();
    await wait(400);
    await page.locator("#avatar-workarea-modal button:has-text('Edit')").click().catch(() => {});
    await wait(250);
    res.checks.plainOnlyEdit = (await page.locator("#avatar-workarea-modal button:has-text('Preview')").count()) === 0
      && (await page.locator("#avatar-workarea-modal button:has-text('Split')").count()) === 0;
    await shot("08-plaintext-modal");
    await closeModal();
  });

  const overflow2 = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  res.checks.pageOverflowFinal = overflow2[0] > overflow2[1];
  await browser.close();
  return res;
}

await fs.mkdir(OUT, { recursive: true });
const desktop = await run("desktop", { viewport: { width: 1440, height: 1000 } });
const mobile = await run("mobile", { ...devices["iPhone 12"] });
await fs.writeFile(path.join(OUT, "browser-validation-summary.json"), JSON.stringify({ generatedAt: new Date().toISOString(), desktop, mobile }, null, 2));
console.log("browser validation summary written");
