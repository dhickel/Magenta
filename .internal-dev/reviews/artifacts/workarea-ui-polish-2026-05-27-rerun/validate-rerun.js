const { chromium, devices } = require('playwright');
const fs = require('fs/promises');
const path = require('path');

const BASE = 'http://localhost:18083';
const URL = `${BASE}/avatar?tab=work-areas`;
const OUT = path.resolve('.internal-dev/reviews/artifacts/workarea-ui-polish-2026-05-27-rerun');
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

function extIsImage(name) {
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(name || '');
}

async function run(view, ctxOpts) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext(ctxOpts);
  const page = await context.newPage();

  const result = {
    view,
    url: URL,
    screenshots: [],
    checks: {},
    consoleErrors: [],
    consoleWarnings: [],
    network4xx5xx: [],
    notes: [],
    failedSteps: []
  };

  page.on('console', (m) => {
    if (m.type() === 'error') result.consoleErrors.push(m.text());
    if (m.type() === 'warning') result.consoleWarnings.push(m.text());
  });
  page.on('response', (r) => {
    if (r.status() >= 400) {
      result.network4xx5xx.push({ status: r.status(), method: r.request().method(), url: r.url() });
    }
  });

  const shot = async (name) => {
    const file = `${view}-${name}.png`;
    await page.screenshot({ path: path.join(OUT, file), fullPage: true });
    result.screenshots.push(file);
  };

  const step = async (name, fn) => {
    try {
      await fn();
    } catch (e) {
      result.failedSteps.push({ step: name, error: String(e && e.message ? e.message : e) });
    }
  };

  async function openWorkArea() {
    await page.locator('.avatar-workarea-entry').first().click({ timeout: 5000 });
    await wait(900);
  }

  async function openDemoFixturesFolder() {
    const row = page.locator('.workspace-explorer-row').filter({ hasText: 'demo-fixtures' }).first();
    if (await row.count()) {
      const openFolder = row.locator("button[aria-label='Open folder']").first();
      if (await openFolder.count()) {
        await openFolder.click();
        await wait(700);
        return true;
      }
      await row.click({ position: { x: 20, y: 16 } }).catch(() => {});
      await wait(500);
      return true;
    }
    return false;
  }

  async function closeModalByButton() {
    const close = page
      .locator("#avatar-workarea-modal button[aria-label='Close'], #avatar-workarea-modal button[title='Close'], #avatar-workarea-modal button:has-text('Close')")
      .first();
    if (await close.count()) {
      await close.click({ timeout: 3000 });
      await wait(400);
      return true;
    }
    await page.keyboard.press('Escape').catch(() => {});
    await wait(300);
    return false;
  }

  await page.goto(URL, { waitUntil: 'domcontentloaded' });
  await wait(1400);
  await openWorkArea();
  await shot('01-workareas-open');

  await step('open-demo-fixtures', async () => {
    const ok = await openDemoFixturesFolder();
    result.checks.demoFixturesOpened = ok;
    await shot('02-demo-fixtures-open');
  });

  await step('markdown-modal-stack-and-controls', async () => {
    const mdRow = page.locator('.workspace-explorer-row').filter({ hasText: 'briefing.md' }).first();
    if (!(await mdRow.count())) throw new Error('briefing.md row not found');
    await mdRow.locator("button[aria-label='View file']").first().click({ timeout: 3000 });
    await wait(600);
    await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click({ timeout: 3000 }).catch(() => {});
    await wait(350);
    await shot('03-markdown-modal-edit-open');

    const z = await page.evaluate(() => {
      const modal = document.querySelector('#avatar-workarea-modal .avatar-modal-workarea-editor, #avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell');
      const nav = document.querySelector('nav, .avatar-shell-nav, .top-nav, .app-nav, header');
      const mz = modal ? getComputedStyle(modal).zIndex : null;
      const nz = nav ? getComputedStyle(nav).zIndex : null;
      return { modalZ: mz, navZ: nz, hasModal: !!modal, hasNav: !!nav };
    });
    result.checks.modalStacking = z;

    const overlapProbe = await page.evaluate(() => {
      const modal = document.querySelector('#avatar-workarea-modal .avatar-modal-workarea-editor, #avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell');
      if (!modal) return { ok: false, reason: 'modal-missing' };
      const r = modal.getBoundingClientRect();
      const sampleX = Math.floor(r.left + 24);
      const sampleY = Math.floor(r.top + 24);
      const topEl = document.elementFromPoint(sampleX, sampleY);
      const coveredByNav = !!(topEl && topEl.closest('nav, .avatar-shell-nav, .top-nav, .app-nav, header') && !topEl.closest('#avatar-workarea-modal'));
      return { ok: true, coveredByNav, topTag: topEl ? topEl.tagName : null, topClass: topEl ? topEl.className : null };
    });
    result.checks.modalNavOverlapProbe = overlapProbe;

    await page.locator("#avatar-workarea-modal button:has-text('Preview')").first().click({ timeout: 3000 }).catch(() => {});
    await wait(300);
    await shot('04-markdown-modal-preview-open');

    const titleText = await page.locator('#avatar-workarea-modal .avatar-workarea-editor-modal-title').first().textContent().catch(() => null);
    result.checks.modalCompactTitleVisible = !!(titleText && titleText.trim().length > 0);
    result.checks.modalCompactTitleText = titleText ? titleText.trim() : null;

    const controls = ['Save', 'Undo', 'Redo', 'Revert Unsaved', 'Close'];
    const probe = {};
    for (const name of controls) {
      const loc = page
        .locator(`#avatar-workarea-modal button[aria-label='${name}'], #avatar-workarea-modal button[title='${name}'], #avatar-workarea-modal button:has-text('${name}')`)
        .first();
      const found = (await loc.count()) > 0;
      probe[name] = found
        ? {
            found,
            ariaLabel: await loc.getAttribute('aria-label'),
            title: await loc.getAttribute('title'),
            text: (await loc.innerText().catch(() => '')).trim()
          }
        : { found: false };
    }
    result.checks.modalControlProbe = probe;

    const boxEdit = await page.locator('#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell').first().boundingBox();
    await page.locator("#avatar-workarea-modal button:has-text('Split')").first().click({ timeout: 3000 }).catch(() => {});
    await wait(300);
    await shot('05-markdown-modal-split-unsaved');
    const boxSplit = await page.locator('#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell').first().boundingBox();
    result.checks.markdownModalStableDimensions = !!(boxEdit && boxSplit && Math.abs(boxEdit.width - boxSplit.width) < 8 && Math.abs(boxEdit.height - boxSplit.height) < 8);

    await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
    await wait(200);
    const ta = page.locator('#avatar-workarea-modal textarea').first();
    if (await ta.count()) {
      await ta.fill('# pw rerun heading\n\n- one\n- two\n\n> quote');
      await wait(200);
      await page.locator("#avatar-workarea-modal button:has-text('Preview')").first().click().catch(() => {});
      await wait(220);
      result.checks.markdownUnsavedPreview = (await page.locator('#avatar-workarea-modal').innerText()).includes('pw rerun heading');
    } else {
      result.checks.markdownUnsavedPreview = false;
      result.notes.push('markdown textarea not found for unsaved preview check');
    }

    await closeModalByButton();
    const modalState = await page.evaluate(() => {
      const root = document.querySelector('#avatar-workarea-modal');
      if (!root) return { exists: false };
      const cs = getComputedStyle(root);
      const rect = root.getBoundingClientRect();
      return {
        exists: true,
        display: cs.display,
        visibility: cs.visibility,
        pointerEvents: cs.pointerEvents,
        opacity: cs.opacity,
        width: rect.width,
        height: rect.height
      };
    });
    result.checks.modalStateAfterClose = modalState;

    const plainRow = page.locator('.workspace-explorer-row').filter({ hasText: 'plain-text-fixture.txt' }).first();
    if (await plainRow.count()) {
      await plainRow.click({ position: { x: 28, y: 16 }, timeout: 3000 });
      await wait(350);
      const selectedPath = await page.locator('.workspace-inspector-path').first().innerText().catch(() => '');
      result.checks.postCloseRowClickInterception = {
        plainTextRowClickable: /plain-text-fixture\.txt/i.test(selectedPath),
        selectedPath
      };
    } else {
      result.checks.postCloseRowClickInterception = { plainTextRowClickable: false, reason: 'plain-text row missing' };
    }
  });

  await step('plaintext-inspector-and-modal-checks', async () => {
    const plainRow = page.locator('.workspace-explorer-row').filter({ hasText: 'plain-text-fixture.txt' }).first();
    if (!(await plainRow.count())) throw new Error('plain-text-fixture.txt row not found');
    await plainRow.click({ position: { x: 24, y: 16 } });
    await wait(350);

    const pre = page.locator('.workspace-inspector-preview pre, .workspace-inspector-preview code, .workspace-inspector-preview').first();
    const previewBox = await pre.boundingBox().catch(() => null);
    result.checks.plainTextPreviewBounded = !!(previewBox && previewBox.width > 0 && previewBox.width < 1200);
    result.checks.plainTextPreviewBox = previewBox;
    await shot('06-plaintext-inspector-selected');

    await plainRow.locator("button[aria-label='View file']").first().click({ timeout: 3000 });
    await wait(500);
    await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
    await wait(250);

    const hasPreview = (await page.locator("#avatar-workarea-modal button:has-text('Preview')").count()) > 0;
    const hasSplit = (await page.locator("#avatar-workarea-modal button:has-text('Split')").count()) > 0;
    result.checks.plainTextModalEditOnly = !hasPreview && !hasSplit;
    await shot('07-plaintext-modal-edit-only');
    await closeModalByButton();
  });

  await step('markdown-reopen-and-modes-check', async () => {
    const mdRow = page.locator('.workspace-explorer-row').filter({ hasText: 'briefing.md' }).first();
    if (!(await mdRow.count())) throw new Error('briefing.md not found for reopen');
    await mdRow.locator("button[aria-label='View file']").first().click({ timeout: 3000 });
    await wait(450);
    await page.locator("#avatar-workarea-modal button:has-text('Edit')").first().click().catch(() => {});
    await wait(250);

    result.checks.markdownModesReopen = {
      edit: (await page.locator("#avatar-workarea-modal button:has-text('Edit')").count()) > 0,
      preview: (await page.locator("#avatar-workarea-modal button:has-text('Preview')").count()) > 0,
      split: (await page.locator("#avatar-workarea-modal button:has-text('Split')").count()) > 0
    };

    const modalBox1 = await page.locator('#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell').first().boundingBox();
    await page.locator("#avatar-workarea-modal button:has-text('Preview')").first().click().catch(() => {});
    await wait(250);
    await page.locator("#avatar-workarea-modal button:has-text('Split')").first().click().catch(() => {});
    await wait(250);
    const modalBox2 = await page.locator('#avatar-workarea-modal .modal-content, #avatar-workarea-modal .avatar-markdown-editor-shell').first().boundingBox();
    result.checks.markdownReopenStableDimensions = !!(modalBox1 && modalBox2 && Math.abs(modalBox1.width - modalBox2.width) < 8 && Math.abs(modalBox1.height - modalBox2.height) < 8);
    await shot('08-markdown-reopen-split');
    await closeModalByButton();
  });

  await step('long-filename-tag-check', async () => {
    const longRow = page.locator('.workspace-explorer-row').filter({ hasText: /pw-very-long-file-name-for-overflow-and-preview-validation|very-long|long/ }).first();
    result.checks.longFilenameRowPresent = (await longRow.count()) > 0;

    const tagChip = page.locator('.workspace-explorer-row .tag-chip, .workspace-explorer-row [class*=tag]').first();
    result.checks.anyTagChipVisible = (await tagChip.count()) > 0;
    await shot('09-long-filename-tag-state');
  });

  await step('image-fixture-check', async () => {
    const rows = page.locator('.workspace-explorer-row');
    const n = await rows.count();
    let foundImage = null;
    for (let i = 0; i < n; i++) {
      const t = (await rows.nth(i).innerText().catch(() => '')).trim();
      if (extIsImage(t)) {
        foundImage = { index: i, text: t };
        break;
      }
    }

    if (foundImage) {
      const imgRow = rows.nth(foundImage.index);
      await imgRow.click({ position: { x: 20, y: 16 } }).catch(() => {});
      await wait(350);
      const img = page.locator('.workspace-inspector-preview img, .workarea-preview img, img[alt*=\"preview\" i]').first();
      const imgBox = await img.boundingBox().catch(() => null);
      result.checks.imageFixture = {
        found: true,
        rowText: foundImage.text,
        thumbnailContained: !!(imgBox && imgBox.width > 0 && imgBox.height > 0 && imgBox.width < 1200 && imgBox.height < 800),
        imageBox: imgBox
      };
      await shot('10-image-preview');
    } else {
      result.checks.imageFixture = {
        found: false,
        covered: false,
        reason: 'No image fixture row discovered in current demo-fixtures listing'
      };
      result.notes.push('No image fixture detected; image thumbnail validation not covered in this rerun.');
    }
  });

  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    hasHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
  }));
  result.checks.pageOverflowFinal = overflow;
  await shot('11-final-state');

  await browser.close();
  return result;
}

(async () => {
  await fs.mkdir(OUT, { recursive: true });
  const desktop = await run('desktop', { viewport: { width: 1440, height: 1000 } });
  const mobile = await run('mobile', { ...devices['iPhone 12'] });

  const summary = {
    generatedAt: new Date().toISOString(),
    baseUrl: URL,
    artifactDir: OUT,
    desktop,
    mobile
  };

  await fs.writeFile(path.join(OUT, 'summary.json'), JSON.stringify(summary, null, 2));
  await fs.writeFile(path.join(OUT, 'console-errors.json'), JSON.stringify({ desktop: desktop.consoleErrors, mobile: mobile.consoleErrors }, null, 2));
  await fs.writeFile(path.join(OUT, 'network-errors.json'), JSON.stringify({ desktop: desktop.network4xx5xx, mobile: mobile.network4xx5xx }, null, 2));

  const modalProbe = {
    desktop: {
      modalControlProbe: desktop.checks.modalControlProbe,
      modalStacking: desktop.checks.modalStacking,
      modalNavOverlapProbe: desktop.checks.modalNavOverlapProbe,
      modalStateAfterClose: desktop.checks.modalStateAfterClose,
      postCloseRowClickInterception: desktop.checks.postCloseRowClickInterception
    },
    mobile: {
      modalControlProbe: mobile.checks.modalControlProbe,
      modalStacking: mobile.checks.modalStacking,
      modalNavOverlapProbe: mobile.checks.modalNavOverlapProbe,
      modalStateAfterClose: mobile.checks.modalStateAfterClose,
      postCloseRowClickInterception: mobile.checks.postCloseRowClickInterception
    }
  };
  await fs.writeFile(path.join(OUT, 'modal-probe.json'), JSON.stringify(modalProbe, null, 2));

  console.log('Rerun validation complete');
})();
