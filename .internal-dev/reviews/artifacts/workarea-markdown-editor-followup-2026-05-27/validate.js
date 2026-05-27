const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE_URL = 'http://localhost:18082';
const OUT_DIR = path.join(
  process.cwd(),
  '.internal-dev/reviews/artifacts/workarea-markdown-editor-followup-2026-05-27'
);

const UNSAVED_APPENDIX = `

## Unsaved Preview Probe

- Bullet alpha
  - Nested bullet alpha.1
> Unsaved quote block for preview check.
\`inline-unsaved\`

\`\`\`text
unsaved fenced block
with two lines
\`\`\`

| u-col-a | u-col-b |
| --- | --- |
| ua | ub |
`;

async function runPass(viewport, prefix) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const consoleErrors = [];
  const networkErrors = [];

  page.on('console', (m) => {
    if (['error', 'warning'].includes(m.type())) {
      consoleErrors.push({ type: m.type(), text: m.text() });
    }
  });
  page.on('response', (r) => {
    if (r.status() >= 400) {
      networkErrors.push({ status: r.status(), method: r.request().method(), url: r.url() });
    }
  });

  const result = {
    viewport,
    prefix,
    markdownPath: null,
    textPath: null,
    selectedWorkArea: null,
    checks: {},
    screenshots: [],
    createdTextFixture: null,
    consoleErrors: [],
    networkErrors: []
  };

  const shot = async (name, fullPage = true) => {
    const p = path.join(OUT_DIR, `${prefix}-${name}.png`);
    await page.screenshot({ path: p, fullPage });
    result.screenshots.push(p);
  };

  try {
    await page.goto(`${BASE_URL}/avatar?tab=work-areas`, { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: 'Work Areas', exact: true }).click();
    await page.waitForTimeout(300);
    await shot('01-avatar-workareas');

    const entry = page.locator('.avatar-workarea-entry').first();
    await entry.click();
    await page.locator('#avatar-workarea-explorer-shell').waitFor({ timeout: 10000 });
    const hx = await entry.getAttribute('hx-get');
    const match = hx && hx.match(/\/avatar\/_work-areas\/([^/]+)\/explorer/);
    result.selectedWorkArea = match ? match[1] : null;
    await shot('02-explorer-root');

    let rootTextPath = null;
    const rootRows = page.locator('.workspace-explorer-row');
    const rootCount = await rootRows.count();
    for (let i = 0; i < rootCount; i++) {
      const row = rootRows.nth(i);
      const p = await row.getAttribute('data-workarea-path');
      if (p && p.toLowerCase().endsWith('.txt')) {
        rootTextPath = p;
        break;
      }
    }

    await page
      .locator('.workspace-explorer-row[data-workarea-path="demo-fixtures"]')
      .getByRole('button', { name: 'Open' })
      .click();
    await page.waitForTimeout(500);
    await shot('03-demo-fixtures-open');

    const rows = page.locator('.workspace-explorer-row');
    const rowCount = await rows.count();
    let markdownPath = null;
    let textPath = null;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      const p = await row.getAttribute('data-workarea-path');
      const text = (await row.innerText()).toLowerCase();
      if (!markdownPath && p && (p.toLowerCase().endsWith('.md') || text.includes('markdown'))) markdownPath = p;
      if (!textPath && p && (p.toLowerCase().endsWith('.txt') || text.includes(' text'))) textPath = p;
    }
    if (!markdownPath) throw new Error('No markdown file found in demo-fixtures');
    result.markdownPath = markdownPath;
    result.textPath = textPath;

    const mdRow = page.locator(`.workspace-explorer-row[data-workarea-path="${markdownPath}"]`);
    await mdRow.click();
    await mdRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(700);
    await shot('04-markdown-viewer');

    const viewerState = await page.evaluate(() => {
      const root = document.querySelector('#workspace-file-view-panel, .workspace-file-viewer, .markdown-body');
      const panel = root || document.body;
      const body = panel.querySelector('.markdown-body') || panel;
      const hasHeading = !!body.querySelector('h1,h2,h3');
      const hasParagraph = !!body.querySelector('p');
      const hasList = !!body.querySelector('ul li,ol li');
      const hasNestedList = !!body.querySelector('ul ul li,ol ol li,ul ol li,ol ul li');
      const hasBlockquote = !!body.querySelector('blockquote');
      const hasInlineCode = !!body.querySelector('p code,li code');
      const hasFence = !!body.querySelector('pre code,pre');
      const hasTable = !!body.querySelector('table');
      const horizOverflow = document.documentElement.scrollWidth > window.innerWidth + 2;
      return {
        hasHeading, hasParagraph, hasList, hasNestedList, hasBlockquote, hasInlineCode, hasFence, hasTable, horizOverflow
      };
    });
    result.checks.viewerState = viewerState;

    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.waitForTimeout(250);
    await shot('05-editor-edit-mode');
    const textarea = page.locator('textarea[data-editor-source="true"]').first();
    const original = await textarea.inputValue();
    const unsaved = `${original}${UNSAVED_APPENDIX}`;
    await textarea.fill(unsaved);
    await page.waitForTimeout(200);

    await page.getByRole('button', { name: 'Preview', exact: true }).click();
    await page.waitForTimeout(350);
    await shot('06-editor-preview-mode-unsaved');

    const previewUnsaved = await page.evaluate(() => {
      const t = document.body.innerText || '';
      return t.includes('Unsaved Preview Probe') &&
        t.includes('Nested bullet alpha.1') &&
        t.includes('unsaved fenced block') &&
        t.includes('u-col-a');
    });
    result.checks.previewShowsUnsaved = previewUnsaved;

    await page.getByRole('button', { name: 'Split', exact: true }).click();
    await page.waitForTimeout(350);
    await shot('07-editor-split-mode-unsaved');
    result.checks.splitVisible = await page.locator('textarea[data-editor-source="true"]').count() > 0 &&
      await page.locator('.markdown-body, pre, table, blockquote').count() > 0;

    await page.getByRole('button', { name: 'Save File', exact: true }).click();
    await page.waitForTimeout(700);
    const saveState = await page.evaluate(() => {
      const t = document.body.innerText || '';
      return {
        hasUnsavedProbe: t.includes('Unsaved Preview Probe'),
        dirtyLabelPresent: /unsaved|dirty/i.test(t)
      };
    });
    result.checks.afterSaveState = saveState;

    try {
      await page.getByRole('button', { name: 'Close', exact: true }).last().click({ timeout: 1200 });
      await page.waitForTimeout(500);
    } catch {}
    await mdRow.click();
    await mdRow.getByRole('button', { name: 'View' }).click();
    await page.waitForTimeout(600);
    await shot('08-markdown-reopen-after-save');
    result.checks.savedRenderVisible = await page.evaluate(() =>
      (document.body.innerText || '').includes('Unsaved Preview Probe')
    );

    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.waitForTimeout(200);
    const textBeforeUndo = await textarea.inputValue();
    await textarea.click();
    await page.keyboard.type('\nUNDO_REDO_MARKER');
    await page.waitForTimeout(200);
    const undoBtn = page.getByRole('button', { name: 'Undo', exact: true });
    const redoBtn = page.getByRole('button', { name: 'Redo', exact: true });
    const revertBtn = page.getByRole('button', { name: 'Revert Unsaved', exact: true });
    const undoEnabled = await undoBtn.isEnabled();
    let afterUndo = await textarea.inputValue();
    let afterRedo = await textarea.inputValue();
    let afterRevert = await textarea.inputValue();
    if (undoEnabled) {
      await undoBtn.click();
      await page.waitForTimeout(150);
      afterUndo = await textarea.inputValue();
      if (await redoBtn.isEnabled()) {
        await redoBtn.click();
        await page.waitForTimeout(150);
        afterRedo = await textarea.inputValue();
      }
    }
    if (await revertBtn.isEnabled()) {
      await revertBtn.click();
      await page.waitForTimeout(200);
      afterRevert = await textarea.inputValue();
    }
    result.checks.undoRedoRevert = {
      undoEnabled,
      undoWorked: afterUndo === textBeforeUndo,
      redoWorked: afterRedo.endsWith('UNDO_REDO_MARKER'),
      revertMatchesSaved: afterRevert === textBeforeUndo
    };

    await textarea.fill(original);
    await page.getByRole('button', { name: 'Save File', exact: true }).click();
    await page.waitForTimeout(500);

    if (!textPath) textPath = rootTextPath;
    if (!textPath && result.selectedWorkArea) {
      const fixtureName = `pw-text-fixture-${Date.now()}.txt`;
      const createResp = await page.request.post(
        `${BASE_URL}/avatar/_work-areas/${result.selectedWorkArea}/text?kind=text`,
        { form: { path: 'demo-fixtures', panel: 'expanded', name: fixtureName } }
      );
      result.createdTextFixture = {
        name: fixtureName,
        status: createResp.status()
      };
      await page.reload({ waitUntil: 'networkidle' });
      await page.getByRole('button', { name: 'Work Areas', exact: true }).click();
      await page.waitForTimeout(200);
      await entry.click();
      await page.locator('#avatar-workarea-explorer-shell').waitFor({ timeout: 10000 });
      await page
        .locator('.workspace-explorer-row[data-workarea-path="demo-fixtures"]')
        .getByRole('button', { name: 'Open' })
        .click();
      await page.waitForTimeout(500);
      textPath = `demo-fixtures/${fixtureName}`;
    }
    result.textPath = textPath;

    if (textPath) {
      try {
        await page.getByRole('button', { name: 'Close', exact: true }).last().click({ timeout: 1200 });
        await page.waitForTimeout(350);
      } catch {}
      if (!textPath.startsWith('demo-fixtures/')) {
        const up = page.getByRole('button', { name: '..', exact: true });
        if (await up.count()) {
          await up.first().click();
          await page.waitForTimeout(500);
        }
      }
      let txtRow = page.locator('.workspace-explorer-row[data-workarea-path$=".txt"]').first();
      if (await txtRow.count() === 0) {
        result.checks.textFileNoMarkdownModes = 'SKIPPED_TEXT_FILE_NOT_FOUND';
        result.checks.textFileSavePersisted = 'SKIPPED_TEXT_FILE_NOT_FOUND';
      } else {
      try {
        await page.getByRole('button', { name: 'Close', exact: true }).last().click({ timeout: 1200 });
        await page.waitForTimeout(400);
      } catch {}
      await txtRow.click();
      await txtRow.getByRole('button', { name: 'View' }).click();
      await page.waitForTimeout(500);
      await shot('09-text-file-view');

      const hasMarkdownControls = await page.evaluate(() => {
        const labels = ['Edit', 'Preview', 'Split'];
        return labels.every((label) =>
          Array.from(document.querySelectorAll('button')).some((b) => (b.textContent || '').trim() === label)
        );
      });
      result.checks.textFileNoMarkdownModes = !hasMarkdownControls;

      await page.getByRole('button', { name: 'Edit', exact: true }).click();
      await page.waitForTimeout(200);
      const txtArea = page.locator('textarea[data-editor-source="true"]').first();
      const txtOriginal = await txtArea.inputValue();
      await txtArea.fill(`${txtOriginal}\ntext-file-save-probe`);
      await page.getByRole('button', { name: 'Save File', exact: true }).click();
      await page.waitForTimeout(500);
      await page.getByRole('button', { name: 'Close', exact: true }).last().click();
      txtRow = page.locator('.workspace-explorer-row[data-workarea-path$=".txt"]').first();
      await txtRow.click();
      await txtRow.getByRole('button', { name: 'View' }).click();
      await page.waitForTimeout(450);
      result.checks.textFileSavePersisted = await page.evaluate(() =>
        (document.body.innerText || '').includes('text-file-save-probe')
      );

      await page.getByRole('button', { name: 'Edit', exact: true }).click();
      await txtArea.fill(txtOriginal);
      await page.getByRole('button', { name: 'Save File', exact: true }).click();
      await page.waitForTimeout(300);
      }
    } else {
      result.checks.textFileNoMarkdownModes = 'SKIPPED_NO_TEXT_FILE_IN_DEMO_FIXTURES';
      result.checks.textFileSavePersisted = 'SKIPPED_NO_TEXT_FILE_IN_DEMO_FIXTURES';
    }

    await shot('10-final-state');
  } finally {
    result.consoleErrors = consoleErrors;
    result.networkErrors = networkErrors;
    await browser.close();
  }

  return result;
}

async function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const desktop = await runPass({ width: 1440, height: 1000 }, 'desktop');
  const mobile = await runPass({ width: 390, height: 844 }, 'mobile');
  const summary = { createdAt: new Date().toISOString(), baseUrl: BASE_URL, desktop, mobile };
  fs.writeFileSync(path.join(OUT_DIR, 'summary.json'), JSON.stringify(summary, null, 2));
  fs.writeFileSync(path.join(OUT_DIR, 'console-errors.json'), JSON.stringify({
    desktop: desktop.consoleErrors,
    mobile: mobile.consoleErrors
  }, null, 2));
  fs.writeFileSync(path.join(OUT_DIR, 'network-errors.json'), JSON.stringify({
    desktop: desktop.networkErrors,
    mobile: mobile.networkErrors
  }, null, 2));
  console.log(JSON.stringify({
    ok: true,
    markdownPath: desktop.markdownPath,
    textPath: desktop.textPath,
    outDir: OUT_DIR
  }, null, 2));
}

main().catch((err) => {
  const fatal = { at: new Date().toISOString(), error: String(err?.stack || err) };
  fs.writeFileSync(path.join(OUT_DIR, 'fatal.json'), JSON.stringify(fatal, null, 2));
  console.error(fatal.error);
  process.exit(1);
});
