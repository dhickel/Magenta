const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const OUT_DIR = path.join(
  process.cwd(),
  '.internal-dev/reviews/artifacts/workarea-tag-revalidation-2026-05-27'
);

async function run() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const errors = [];
  const findings = {
    timestamp: new Date().toISOString(),
    url: 'http://localhost:18080/avatar',
    workAreaId: null,
    selectedPaths: {},
    wrongType: {},
    normalFlow: {},
    chipStyle: {},
    blocked: []
  };

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } });
  const page = await context.newPage();
  page.on('console', m => {
    if (['error', 'warning'].includes(m.type())) errors.push(`console:${m.type()}:${m.text()}`);
  });
  page.on('response', r => {
    if (r.status() >= 400) errors.push(`http:${r.status()}:${r.request().method()}:${r.url()}`);
  });

  try {
    await page.goto(findings.url, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(OUT_DIR, '01-avatar-initial.png'), fullPage: true });

    const entry = page.locator('.avatar-workarea-entry').first();
    await entry.waitFor({ timeout: 10000 });
    const hxGet = await entry.getAttribute('hx-get');
    const idMatch = hxGet && hxGet.match(/\/avatar\/_work-areas\/([^/]+)\/explorer/);
    if (!idMatch) throw new Error(`unable to parse workAreaId from ${hxGet}`);
    const workAreaId = idMatch[1];
    findings.workAreaId = workAreaId;

    await entry.click();
    await page.locator('#avatar-workarea-explorer-shell').waitFor({ timeout: 10000 });
    await page.screenshot({ path: path.join(OUT_DIR, '02-explorer-open.png'), fullPage: true });

    const selection = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('.workspace-explorer-row'));
      let filePath = null;
      let directoryPath = null;
      for (const row of rows) {
        const p = row.getAttribute('data-workarea-path');
        const tds = row.querySelectorAll('td');
        if (!p || tds.length < 2) continue;
        const type = (tds[1].textContent || '').trim().toLowerCase();
        if (!directoryPath && type === 'folder') directoryPath = p;
        if (!filePath && (type === 'text' || type === 'markdown' || type === 'image' || type === 'binary')) filePath = p;
        if (filePath && directoryPath) break;
      }
      return { filePath, directoryPath };
    });
    if (!selection.filePath || !selection.directoryPath) {
      throw new Error(`could not find both file and directory rows: ${JSON.stringify(selection)}`);
    }
    findings.selectedPaths = selection;

    const stamp = Date.now();
    const fileTag = `pw-file-tag-${stamp}`;
    const dirTag = `pw-dir-tag-${stamp}`;

    const fetchText = async (url, init) => page.evaluate(async ({ url, init }) => {
      const r = await fetch(url, init);
      const body = await r.text();
      return { status: r.status, body };
    }, { url, init });

    const fetchJson = async (url, init) => page.evaluate(async ({ url, init }) => {
      const r = await fetch(url, init);
      const body = await r.json();
      return { status: r.status, body };
    }, { url, init });

    await fetchText(`/avatar/_work-areas/${workAreaId}/tags?label=${encodeURIComponent(fileTag)}&targetType=file`, { method: 'POST' });
    await fetchText(`/avatar/_work-areas/${workAreaId}/tags?label=${encodeURIComponent(dirTag)}&targetType=directory`, { method: 'POST' });

    const wrongFileAsDirectory = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.filePath)}&label=${encodeURIComponent(dirTag)}&targetType=directory`,
      { method: 'POST' }
    );
    const wrongDirectoryAsFile = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.directoryPath)}&label=${encodeURIComponent(fileTag)}&targetType=file`,
      { method: 'POST' }
    );
    const fileLabelsAfterWrong = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.filePath)}`
    );
    const dirLabelsAfterWrong = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.directoryPath)}`
    );
    findings.wrongType = {
      filePathWithDirectoryTargetType: {
        responseStatus: wrongFileAsDirectory.status,
        responseHasInspectorError: wrongFileAsDirectory.body.includes('workspace-inspector-error'),
        labelAssigned: (fileLabelsAfterWrong.body || []).some(a => a.label && a.label.slug === dirTag)
      },
      directoryPathWithFileTargetType: {
        responseStatus: wrongDirectoryAsFile.status,
        responseHasInspectorError: wrongDirectoryAsFile.body.includes('workspace-inspector-error'),
        labelAssigned: (dirLabelsAfterWrong.body || []).some(a => a.label && a.label.slug === fileTag)
      }
    };

    const addFile = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.filePath)}&label=${encodeURIComponent(fileTag)}&targetType=file`,
      { method: 'POST' }
    );
    const fileLabelsAfterAdd = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.filePath)}`
    );

    await page.goto(`http://localhost:18080/avatar/_work-areas/${workAreaId}/explorer?path=.&selected=${encodeURIComponent(selection.filePath)}&panel=expanded`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(OUT_DIR, '03-file-tag-added.png'), fullPage: true });
    const fileChipStyle = await page.evaluate((tag) => {
      const chips = Array.from(document.querySelectorAll('.file-entry-tags .tag'));
      const chip = chips.find(el => (el.textContent || '').includes(tag));
      if (!chip) return null;
      const btn = chip.querySelector('.workspace-tag-remove');
      if (!btn) return null;
      const btnStyle = getComputedStyle(btn);
      const chipStyle = getComputedStyle(chip);
      return {
        removeText: (btn.textContent || '').trim(),
        removeColor: btnStyle.color,
        removeLineHeight: btnStyle.lineHeight,
        chipAlignItems: chipStyle.alignItems
      };
    }, fileTag);

    const removeFile = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.filePath)}&label=${encodeURIComponent(fileTag)}`,
      { method: 'DELETE' }
    );
    const fileLabelsAfterRemove = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.filePath)}`
    );

    const addDir = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.directoryPath)}&label=${encodeURIComponent(dirTag)}&targetType=directory`,
      { method: 'POST' }
    );
    const dirLabelsAfterAdd = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.directoryPath)}`
    );

    await page.goto(`http://localhost:18080/avatar/_work-areas/${workAreaId}/explorer?path=.&selected=${encodeURIComponent(selection.directoryPath)}&panel=expanded`, { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(OUT_DIR, '04-directory-tag-added.png'), fullPage: true });
    const dirChipStyle = await page.evaluate((tag) => {
      const chips = Array.from(document.querySelectorAll('.file-entry-tags .tag'));
      const chip = chips.find(el => (el.textContent || '').includes(tag));
      if (!chip) return null;
      const btn = chip.querySelector('.workspace-tag-remove');
      if (!btn) return null;
      const btnStyle = getComputedStyle(btn);
      const chipStyle = getComputedStyle(chip);
      return {
        removeText: (btn.textContent || '').trim(),
        removeColor: btnStyle.color,
        removeLineHeight: btnStyle.lineHeight,
        chipAlignItems: chipStyle.alignItems
      };
    }, dirTag);

    const removeDir = await fetchText(
      `/avatar/_work-areas/${workAreaId}/files/tags?path=${encodeURIComponent(selection.directoryPath)}&label=${encodeURIComponent(dirTag)}`,
      { method: 'DELETE' }
    );
    const dirLabelsAfterRemove = await fetchJson(
      `/api/work-areas/${workAreaId}/files/labels?path=${encodeURIComponent(selection.directoryPath)}`
    );

    findings.normalFlow = {
      file: {
        addStatus: addFile.status,
        addResponseHasSuccessToast: addFile.body.includes('workspace-explorer-success'),
        assignedAfterAdd: (fileLabelsAfterAdd.body || []).some(a => a.label && a.label.slug === fileTag),
        removeStatus: removeFile.status,
        removedAfterDelete: !(fileLabelsAfterRemove.body || []).some(a => a.label && a.label.slug === fileTag)
      },
      directory: {
        addStatus: addDir.status,
        addResponseHasSuccessToast: addDir.body.includes('workspace-explorer-success'),
        assignedAfterAdd: (dirLabelsAfterAdd.body || []).some(a => a.label && a.label.slug === dirTag),
        removeStatus: removeDir.status,
        removedAfterDelete: !(dirLabelsAfterRemove.body || []).some(a => a.label && a.label.slug === dirTag)
      }
    };

    findings.chipStyle = {
      file: fileChipStyle,
      directory: dirChipStyle
    };

    await page.screenshot({ path: path.join(OUT_DIR, '05-final-state.png'), fullPage: true });
  } finally {
    findings.errors = errors;
    fs.writeFileSync(path.join(OUT_DIR, 'evidence-errors.json'), JSON.stringify(errors, null, 2));
    fs.writeFileSync(path.join(OUT_DIR, 'findings.json'), JSON.stringify(findings, null, 2));
    await browser.close();
  }
}

run().catch(err => {
  const out = {
    timestamp: new Date().toISOString(),
    fatal: String(err && err.stack ? err.stack : err)
  };
  fs.writeFileSync(path.join(OUT_DIR, 'fatal.json'), JSON.stringify(out, null, 2));
  console.error(out.fatal);
  process.exit(1);
});
