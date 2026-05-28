const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const base = 'http://127.0.0.1:18082';
const outDir = 'artifacts/assistant-dashboard-refactor/playwright/browser-recheck-2-2026-05-28';
fs.mkdirSync(outDir, { recursive: true });

const results = {
  status: 'unknown',
  liveAppUrl: base,
  startedAt: new Date().toISOString(),
  screenshots: [],
  checks: [],
  consoleMessages: [],
  failedRequests: [],
  httpErrors: [],
  visualNotes: [],
  routeUrls: []
};
function record(name, passed, details = {}) { results.checks.push({ name, passed, ...details }); }
function shot(page, name, fullPage = true) {
  const p = path.join(outDir, name);
  results.screenshots.push(p);
  return page.screenshot({ path: p, fullPage });
}
function hasUpperAvatar(text) { return /\bAvatar\b/.test(text); }
function navOrder(text) {
  const labels = ['Home', 'Chat', 'Agents', 'Manage'];
  let pos = -1;
  for (const l of labels) {
    const n = text.indexOf(l, pos + 1);
    if (n < 0) return false;
    pos = n;
  }
  return true;
}
async function metrics(page) {
  return await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    bodyScrollWidth: document.body.scrollWidth,
    bodyClientWidth: document.body.clientWidth,
    viewport: { width: window.innerWidth, height: window.innerHeight }
  }));
}
async function collectRouteAttrs(page) {
  return await page.locator('[hx-get], [hx-post], a[href], form[action], [data-workarea-open-url]').evaluateAll(els => els.map(el => ({
    tag: el.tagName,
    text: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 120),
    href: el.getAttribute('href'),
    hxGet: el.getAttribute('hx-get'),
    hxPost: el.getAttribute('hx-post'),
    action: el.getAttribute('action'),
    dataWorkareaOpenUrl: el.getAttribute('data-workarea-open-url')
  })).filter(x => JSON.stringify(x).includes('work-areas') || JSON.stringify(x).includes('_work-areas')));
}
async function openWorkAreaExplorer(page) {
  await page.goto(`${base}/agents/avatar`, { waitUntil: 'networkidle' });
  await page.getByText('Work-areas', { exact: true }).click();
  await page.waitForSelector('.avatar-workarea-entry', { timeout: 5000 });
  await page.locator('.avatar-workarea-entry').first().click();
  await page.waitForSelector('#avatar-workarea-explorer-shell', { timeout: 5000 });
}
async function workAreaQuality(page, label) {
  const m = await metrics(page);
  const data = await page.evaluate(() => {
    const rect = el => {
      const r = el.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
    };
    const actionButtons = [...document.querySelectorAll('.workspace-explorer-action-button')].map(el => ({
      title: el.getAttribute('title') || el.getAttribute('aria-label'),
      rect: rect(el),
      visible: !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)
    }));
    const toolbarButtons = [...document.querySelectorAll('.workspace-explorer-toolbar .avatar-icon-toolbar-button')].map(el => ({
      title: el.getAttribute('title') || el.getAttribute('aria-label') || el.textContent.trim(),
      rect: rect(el),
      visible: !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)
    }));
    const icons = [...document.querySelectorAll('#avatar-workarea-explorer-shell svg.avatar-control-icon')].map(el => rect(el));
    const longNameButton = [...document.querySelectorAll('.workspace-explorer-name button')].find(el => el.textContent.includes('extremely-long-name'));
    const longNameCell = longNameButton?.closest('td');
    const rowActions = [...document.querySelectorAll('.avatar-row-actions')].map(el => rect(el));
    const tableRegion = document.querySelector('.workspace-explorer-table-region');
    const explorer = document.querySelector('#avatar-workarea-explorer-shell');
    const preview = document.querySelector('.workspace-inspector-preview, .avatar-workarea-preview, .workspace-preview, [class*="preview"]');
    const links = [...document.querySelectorAll('[hx-get], [hx-post], a[href], form[action], [data-workarea-open-url]')].map(el => [el.getAttribute('href'), el.getAttribute('hx-get'), el.getAttribute('hx-post'), el.getAttribute('action'), el.getAttribute('data-workarea-open-url')].filter(Boolean)).flat();
    return {
      actionButtons,
      toolbarButtons,
      icons,
      longNameButton: longNameButton ? rect(longNameButton) : null,
      longNameCell: longNameCell ? rect(longNameCell) : null,
      rowActions,
      tableRegion: tableRegion ? { ...rect(tableRegion), scrollWidth: tableRegion.scrollWidth, clientWidth: tableRegion.clientWidth } : null,
      explorer: explorer ? rect(explorer) : null,
      preview: preview ? rect(preview) : null,
      routes: links.filter(u => u.includes('work-areas') || u.includes('_work-areas')),
      bodyText: document.body.innerText
    };
  });
  const minAction = data.actionButtons.reduce((min, b) => Math.min(min, b.rect.width, b.rect.height), Infinity);
  const minToolbar = data.toolbarButtons.reduce((min, b) => Math.min(min, b.rect.width, b.rect.height), Infinity);
  const maxIcon = data.icons.reduce((max, r) => Math.max(max, r.width, r.height), 0);
  const pageOverflow = m.scrollWidth > m.clientWidth + 2 || m.bodyScrollWidth > m.bodyClientWidth + 2;
  const badRoute = data.routes.some(u => u.includes('/avatar/_work-areas'));
  const expectedRoute = data.routes.some(u => u.includes('/agents/_detail/avatar/work-areas'));
  const longNameOk = !data.longNameButton || (data.rowActions.length && data.rowActions.every(r => r.width >= 120) && data.longNameButton.right <= (data.longNameCell?.right || Infinity) + 1);
  const tableScrollAcceptable = !data.tableRegion || data.tableRegion.scrollWidth <= data.tableRegion.clientWidth + (m.viewport.width < 700 ? 40 : 2);
  const actionOk = isFinite(minAction) && minAction >= 28;
  const toolbarOk = isFinite(minToolbar) && minToolbar >= 28;
  const iconOk = maxIcon <= 28;
  record(`${label}: no document horizontal overflow`, !pageOverflow, { metrics: m });
  record(`${label}: Work Area action buttons visible/stable`, actionOk, { minActionButtonPx: minAction, actionButtons: data.actionButtons.slice(0, 12) });
  record(`${label}: toolbar buttons visible/stable`, toolbarOk, { minToolbarButtonPx: minToolbar, toolbarButtons: data.toolbarButtons });
  record(`${label}: icons/placeholders modest`, iconOk, { maxIconPx: maxIcon, previewRect: data.preview });
  record(`${label}: long names do not crush controls`, !!longNameOk, { longNameButton: data.longNameButton, rowActions: data.rowActions.slice(0, 4), tableRegion: data.tableRegion, tableScrollAcceptable });
  record(`${label}: Work Area routes stay under agent detail`, expectedRoute && !badRoute, { routes: [...new Set(data.routes)].slice(0, 60) });
  results.routeUrls.push({ label, routes: [...new Set(data.routes)] });
  results.visualNotes.push({ label, note: `Explorer uses compact details table with bounded inspector. Action buttons measured min ${minAction}px, toolbar min ${minToolbar}px, max SVG ${maxIcon}px. Document overflow ${pageOverflow ? 'present' : 'absent'}.` });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  context.on('page', p => {
    p.on('console', msg => {
      if (['error', 'warning'].includes(msg.type())) results.consoleMessages.push({ type: msg.type(), text: msg.text(), url: p.url() });
    });
    p.on('requestfailed', req => results.failedRequests.push({ url: req.url(), method: req.method(), failure: req.failure()?.errorText }));
    p.on('response', resp => { if (resp.status() >= 400) results.httpErrors.push({ status: resp.status(), url: resp.url() }); });
  });
  const page = await context.newPage();
  page.on('console', msg => {
    if (['error', 'warning'].includes(msg.type())) results.consoleMessages.push({ type: msg.type(), text: msg.text(), url: page.url() });
  });
  page.on('requestfailed', req => results.failedRequests.push({ url: req.url(), method: req.method(), failure: req.failure()?.errorText }));
  page.on('response', resp => { if (resp.status() >= 400) results.httpErrors.push({ status: resp.status(), url: resp.url() }); });

  await page.goto(`${base}/manage`, { waitUntil: 'networkidle' });
  await shot(page, 'manage-desktop.png');
  const manageText = await page.locator('body').innerText();
  const agentsTableText = await page.locator('text=Agents').locator('xpath=ancestor::*[self::section or self::div][1]').innerText().catch(() => manageText);
  record('/manage desktop Agents table shows Assistant', /\bAssistant\b/.test(manageText) && !/NAME\s+STATUS[\s\S]*\bAvatar\b/.test(manageText), { bodyExcerpt: manageText.slice(0, 1200) });
  record('/manage desktop has no user-facing uppercase Avatar', !hasUpperAvatar(manageText), { avatarOccurrences: manageText.match(/\bAvatar\b/g)?.length || 0 });
  record('/manage top nav order smoke', navOrder(manageText), { expected: 'Home > Chat > Agents > Manage' });
  record('/manage side nav smoke', manageText.includes('SYSTEM') && manageText.includes('ORCHESTRATION') && manageText.includes('TOOLS'), { sections: ['SYSTEM','ORCHESTRATION','TOOLS'] });
  record('/manage recent events render', manageText.includes('Recent Events'), { excerpt: manageText.slice(manageText.indexOf('Recent Events'), manageText.indexOf('Recent Events') + 500) });

  await page.goto(`${base}/agents/avatar`, { waitUntil: 'networkidle' });
  await shot(page, 'agent-avatar-detail-desktop.png');
  const detailText = await page.locator('body').innerText();
  const links = await collectRouteAttrs(page);
  const cssLinks = await page.locator('link[href*="avatar-dashboard.css"]').evaluateAll(els => els.map(el => el.getAttribute('href')));
  record('/agents/avatar desktop primary labels show Assistant', detailText.includes('Agent: Assistant') && detailText.includes('NAME\nAssistant') && !hasUpperAvatar(detailText), { bodyExcerpt: detailText.slice(0, 1800) });
  record('/agents/avatar desktop diagnostic ID remains avatar', /ID\s+avatar/.test(detailText), { idLineFound: /ID\s+avatar/.test(detailText) });
  record('/agents/avatar desktop loads avatar-dashboard.css?v=7', cssLinks.some(h => h.includes('/css/avatar-dashboard.css?v=7') || h.includes('avatar-dashboard.css?v=7')), { cssLinks });
  record('/agents/avatar desktop tab routes use agent detail path', links.some(x => x.hxGet === '/agents/_detail/avatar/work-areas') && !JSON.stringify(links).includes('/avatar/_work-areas'), { links });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${base}/agents/avatar`, { waitUntil: 'networkidle' });
  await shot(page, 'agent-avatar-detail-mobile.png');
  const mobileDetailText = await page.locator('body').innerText();
  const mobileMetrics = await metrics(page);
  record('/agents/avatar mobile primary labels show Assistant', mobileDetailText.includes('Agent: Assistant') && mobileDetailText.includes('NAME\nAssistant') && !hasUpperAvatar(mobileDetailText), { bodyExcerpt: mobileDetailText.slice(0, 1800) });
  record('/agents/avatar mobile no document horizontal overflow', mobileMetrics.scrollWidth <= mobileMetrics.clientWidth + 2, { metrics: mobileMetrics });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await openWorkAreaExplorer(page);
  await shot(page, 'agent-workareas-tab-explorer-desktop.png');
  const workText = await page.locator('body').innerText();
  record('/agents/avatar Work Areas tab opens selected explorer desktop', workText.includes('Work Areas') && workText.includes('Home') && workText.includes('NAME') && await page.locator('#avatar-workarea-explorer-shell').count() === 1, { bodyExcerpt: workText.slice(0, 1800) });
  await workAreaQuality(page, '/agents/avatar Work Area explorer desktop');

  await page.setViewportSize({ width: 390, height: 844 });
  await openWorkAreaExplorer(page);
  await shot(page, 'agent-workareas-tab-explorer-mobile.png');
  await workAreaQuality(page, '/agents/avatar Work Area explorer mobile');

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`${base}/`, { waitUntil: 'networkidle' });
  await shot(page, 'root-desktop.png');
  const rootText = await page.locator('body').innerText();
  record('/ root loads', rootText.includes('Assistant') && rootText.includes('Dashboard'), { bodyExcerpt: rootText.slice(0, 1000) });
  await page.locator('.dashboard-create-button').click();
  await page.waitForSelector('#dashboard-create-modal', { timeout: 5000 });
  await shot(page, 'create-dashboard-modal-open.png');
  const input = page.locator('#dashboard-create-modal input[name="name"]');
  const required = await input.evaluate(el => el.required === true);
  await page.locator('#dashboard-create-modal button[type="submit"]').click();
  await page.waitForTimeout(500);
  const invalid = await input.evaluate(el => el.matches(':invalid') && el.validationMessage.length > 0);
  await shot(page, 'create-dashboard-modal-blank-invalid.png');
  record('create-dashboard modal has required name input', required, { required });
  record('create-dashboard blank submit stays invalid', invalid, { invalid });

  await page.goto(`${base}/dashboards/assistant?edit=true`, { waitUntil: 'networkidle' });
  await shot(page, 'assistant-edit-mode-desktop.png');
  const editText = await page.locator('body').innerText();
  const editM = await metrics(page);
  record('Assistant edit mode loads', editText.includes('Assistant') && (editText.includes('Add Widget') || editText.includes('Insert Row') || await page.locator('[class*="edit"], [hx-get*="_layout"], [hx-post*="_layout"]').count() > 0), { bodyExcerpt: editText.slice(0, 1500), metrics: editM });
  record('Assistant edit mode no document horizontal overflow', editM.scrollWidth <= editM.clientWidth + 2, { metrics: editM });

  results.endedAt = new Date().toISOString();
  results.status = results.checks.every(c => c.passed) && results.consoleMessages.length === 0 && results.failedRequests.length === 0 && results.httpErrors.length === 0 ? 'passed' : 'failed';
  fs.writeFileSync(path.join(outDir, 'results.json'), JSON.stringify(results, null, 2));
  await browser.close();
  if (results.status !== 'passed') process.exitCode = 1;
})().catch(err => {
  results.status = 'error';
  results.error = { message: err.message, stack: err.stack };
  fs.writeFileSync(path.join(outDir, 'results.json'), JSON.stringify(results, null, 2));
  console.error(err);
  process.exit(1);
});
