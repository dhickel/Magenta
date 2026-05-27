const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE_URL = 'http://localhost:18082';
const OUT_DIR = path.join(
  process.cwd(),
  '.internal-dev/reviews/artifacts/workarea-markdown-editor-followup-2026-05-27-rerun'
);
const MD_PATH = 'demo-fixtures/briefing.md';
const TXT_PATH = 'demo-fixtures/plain-text-fixture.txt';
const UNSAVED_MARKER = 'UNSAVED_PREVIEW_RERUN_MARKER';
const UNDO_REDO_MARKER = 'UNDO_REDO_RERUN_MARKER';
const TXT_MARKER = 'TEXT_SAVE_RERUN_MARKER';

function sanitizeRequest(r) {
  return { status: r.status(), method: r.request().method(), url: r.url() };
}

async function runPass(viewport, prefix) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const consoleErrors = [];
  const networkErrors = [];
  const screenshots = [];

  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push({ type: m.type(), text: m.text() });
  });
  page.on('response', (r) => {
    if (r.status() >= 400) networkErrors.push(sanitizeRequest(r));
  });

  const shot = async (name, fullPage = true) => {
    const fp = path.join(OUT_DIR, `${prefix}-${name}.png`);
    await page.screenshot({ path: fp, fullPage });
    screenshots.push(fp);
  };
  const closeModalIfOpen = async () => {
    const modal = page.locator('#avatar-workarea-modal .avatar-modal');
    if (await modal.count()) {
      const closeButtons = page.getByRole('button', { name: 'Close', exact: true });
      if (await closeButtons.count()) {
        await closeButtons.last().click();
        await page.waitForTimeout(200);
      } else {
        await page.keyboard.press('Escape');
        await page.waitForTimeout(200);
      }
    }
  };
  const ensureEditorTextarea = async () => {
    const textarea = page.locator('textarea[data-editor-source="true"]').first();
    if (await textarea.count()) return textarea;
    const editBtn = page.getByRole('button', { name: 'Edit', exact: true });
    if (await editBtn.count()) {
      await editBtn.first().click();
      await page.waitForTimeout(220);
      return page.locator('textarea[data-editor-source="true"]').first();
    }
    const textBtn = page.getByRole('button', { name: 'Text', exact: true });
    if (await textBtn.count()) {
      await textBtn.first().click();
      await page.waitForTimeout(220);
      return page.locator('textarea[data-editor-source="true"]').first();
    }
    return textarea;
  };

  const out = {
    viewport,
    prefix,
    filesTested: [MD_PATH, TXT_PATH],
    checks: {},
    screenshots,
    consoleErrors: [],
    networkErrors: []
  };

  try {
    await page.goto(`${BASE_URL}/avatar?tab=work-areas`, { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: 'Work Areas', exact: true }).click();
    await page.waitForTimeout(250);
    await shot('01-workareas');

    const workAreaEntry = page.locator('.avatar-workarea-entry').first();
    await workAreaEntry.click();
    await page.locator('#avatar-workarea-explorer-shell').waitFor({ timeout: 10000 });
    await shot('02-explorer-root');

    const fixtureDir = page.locator('.workspace-explorer-row[data-workarea-path="demo-fixtures"]');
    await fixtureDir.getByRole('button', { name: 'Open' }).click();
    await page.waitForTimeout(300);
    await shot('03-demo-fixtures-open');
    await closeModalIfOpen();

    const mdRow = page.locator(`.workspace-explorer-row[data-workarea-path="${MD_PATH}"]`);
    const txtRow = page.locator(`.workspace-explorer-row[data-workarea-path="${TXT_PATH}"]`);
    out.checks.fixturePresence = {
      markdownFound: (await mdRow.count()) > 0,
      textFound: (await txtRow.count()) > 0
    };
    if (!out.checks.fixturePresence.markdownFound || !out.checks.fixturePresence.textFound) {
      throw new Error(
        `Required fixtures missing in explorer. markdownFound=${out.checks.fixturePresence.markdownFound}, textFound=${out.checks.fixturePresence.textFound}`
      );
    }

    await closeModalIfOpen();
    await mdRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(600);
    await shot('04-markdown-viewer');

    out.checks.viewerStructure = await page.evaluate(() => {
      const body =
        document.querySelector('.avatar-workarea-preview-pane .magenta-rendered-markdown') ||
        document.querySelector('.avatar-workarea-preview-pane') ||
        document.querySelector('.avatar-workarea-editor') ||
        document.body;
      const hasNestedList = !!body.querySelector('ul ul li, ul ol li, ol ul li, ol ol li');
      const hasBlockquote = !!body.querySelector('blockquote');
      const hasInlineCode = !!body.querySelector('p code, li code');
      const hasFencedCode = !!body.querySelector('pre code, pre');
      const hasTable = !!body.querySelector('table');
      const overflowNodes = [body, ...Array.from(body.querySelectorAll('pre,table,blockquote,ul,ol'))];
      const horizontalOverflow = overflowNodes.some((n) => n.scrollWidth - n.clientWidth > 2);
      return { hasNestedList, hasBlockquote, hasInlineCode, hasFencedCode, hasTable, horizontalOverflow };
    });

    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.waitForTimeout(250);
    await shot('05-markdown-edit');
    const mdTextarea = page.locator('textarea[data-editor-source="true"]').first();
    const mdSavedBaseline = await mdTextarea.inputValue();
    await mdTextarea.fill(`${mdSavedBaseline}\n\n${UNSAVED_MARKER}\n\n- preview bullet\n  - preview nested`);
    await page.waitForTimeout(200);

    await page.getByRole('button', { name: 'Preview', exact: true }).click();
    await page.waitForTimeout(300);
    await shot('06-markdown-preview-unsaved');
    out.checks.previewShowsUnsaved = await page.evaluate((marker) => {
      const txt = document.body.innerText || '';
      return txt.includes(marker) && txt.includes('preview nested');
    }, UNSAVED_MARKER);
    out.checks.previewModeActive = await page.locator('[data-editor-mode="preview"]').count() > 0;

    await page.getByRole('button', { name: 'Split', exact: true }).click();
    await page.waitForTimeout(300);
    await shot('07-markdown-split-unsaved');
    out.checks.splitModeActive = await page.locator('[data-editor-mode="split"]').count() > 0;
    out.checks.splitShowsEditorAndPreview =
      (await page.locator('textarea[data-editor-source="true"]').count()) > 0 &&
      (await page.locator('.markdown-body, pre, table, blockquote').count()) > 0;

    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.waitForTimeout(200);
    await mdTextarea.fill(`${mdSavedBaseline}\n${UNDO_REDO_MARKER}`);
    await page.waitForTimeout(180);
    const undo = page.getByRole('button', { name: 'Undo', exact: true });
    const redo = page.getByRole('button', { name: 'Redo', exact: true });
    const revert = page.getByRole('button', { name: 'Revert Unsaved', exact: true });
    const beforeUndo = await mdTextarea.inputValue();
    let afterUndo = beforeUndo;
    let afterRedo = beforeUndo;
    let afterRevert = beforeUndo;
    const undoEnabled = await undo.isEnabled();
    const redoEnabledPreUndo = await redo.isEnabled();
    if (undoEnabled) {
      await undo.click();
      await page.waitForTimeout(180);
      afterUndo = await mdTextarea.inputValue();
    }
    const redoEnabledPostUndo = await redo.isEnabled();
    if (redoEnabledPostUndo) {
      await redo.click();
      await page.waitForTimeout(180);
      afterRedo = await mdTextarea.inputValue();
    }
    if (await revert.isEnabled()) {
      await revert.click();
      await page.waitForTimeout(220);
      afterRevert = await mdTextarea.inputValue();
    }
    out.checks.undoRedoRevert = {
      undoEnabled,
      redoEnabledPreUndo,
      redoEnabledPostUndo,
      undoWorked: !afterUndo.includes(UNDO_REDO_MARKER),
      redoWorked: afterRedo.includes(UNDO_REDO_MARKER),
      revertUnsavedWorked: afterRevert === mdSavedBaseline
    };
    await shot('08-markdown-undo-redo-revert');

    await page.getByRole('button', { name: 'Save File', exact: true }).click();
    await page.waitForTimeout(450);
    await closeModalIfOpen();
    await mdRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(500);
    out.checks.persistedSavedMarkdownShape = await page.evaluate(() => {
      const panel = document.querySelector('#workspace-file-view-panel') || document.body;
      const body = panel.querySelector('.markdown-body') || panel;
      return {
        hasNestedList: !!body.querySelector('ul ul li, ul ol li, ol ul li, ol ol li'),
        hasBlockquote: !!body.querySelector('blockquote'),
        hasInlineCode: !!body.querySelector('p code, li code'),
        hasFencedCode: !!body.querySelector('pre code, pre')
      };
    });
    await shot('09-markdown-reopen-post-save');

    await closeModalIfOpen();
    await txtRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(450);
    await shot('10-text-view');
    out.checks.textModeNoMarkdownControls = await page.evaluate(() => {
      const modePreview = document.querySelector('[data-editor-mode="preview"]');
      const modeSplit = document.querySelector('[data-editor-mode="split"]');
      const modeButtons = Array.from(document.querySelectorAll('button')).map((b) => (b.textContent || '').trim());
      const hasPreviewBtn = modeButtons.includes('Preview');
      const hasSplitBtn = modeButtons.includes('Split');
      return !modePreview && !modeSplit && !hasPreviewBtn && !hasSplitBtn;
    });

    const txtArea = await ensureEditorTextarea();
    await page.waitForTimeout(120);
    const txtOriginal = await txtArea.inputValue();
    await txtArea.fill(`${txtOriginal}\n${TXT_MARKER}`);
    await page.getByRole('button', { name: 'Save File', exact: true }).click();
    await page.waitForTimeout(500);
    await closeModalIfOpen();
    await txtRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(450);
    out.checks.textSavePersisted = await page.evaluate((marker) => {
      const ta = document.querySelector('textarea[data-editor-source="true"]');
      if (ta && typeof ta.value === 'string') return ta.value.includes(marker);
      return (document.body.innerText || '').includes(marker);
    }, TXT_MARKER);
    await shot('11-text-reopen-post-save');

    const txtAreaRestore = await ensureEditorTextarea();
    await page.waitForTimeout(120);
    await txtAreaRestore.fill(txtOriginal);
    await page.getByRole('button', { name: 'Save File', exact: true }).click();
    await page.waitForTimeout(300);
    await shot('12-final-state');
  } finally {
    out.consoleErrors = consoleErrors;
    out.networkErrors = networkErrors;
    await browser.close();
  }

  return out;
}

async function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const desktop = await runPass({ width: 1440, height: 1000 }, 'desktop');
  const mobile = await runPass({ width: 390, height: 844 }, 'mobile');
  const summary = { createdAt: new Date().toISOString(), baseUrl: BASE_URL, desktop, mobile };
  fs.writeFileSync(path.join(OUT_DIR, 'summary.json'), JSON.stringify(summary, null, 2));
  fs.writeFileSync(
    path.join(OUT_DIR, 'console-errors.json'),
    JSON.stringify({ desktop: desktop.consoleErrors, mobile: mobile.consoleErrors }, null, 2)
  );
  fs.writeFileSync(
    path.join(OUT_DIR, 'network-errors.json'),
    JSON.stringify({ desktop: desktop.networkErrors, mobile: mobile.networkErrors }, null, 2)
  );
  console.log(JSON.stringify({ ok: true, outDir: OUT_DIR }, null, 2));
}

main().catch((err) => {
  const fatal = { at: new Date().toISOString(), error: String(err?.stack || err) };
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, 'fatal.json'), JSON.stringify(fatal, null, 2));
  console.error(fatal.error);
  process.exit(1);
});
