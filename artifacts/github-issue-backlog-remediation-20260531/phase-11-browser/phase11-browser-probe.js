const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = 'http://localhost:18080';
const outDir = path.resolve('artifacts/github-issue-backlog-remediation-20260531/phase-11-browser');
const executablePath = '/home/hickelpickle/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome';

fs.mkdirSync(outDir, { recursive: true });

function file(name) {
  return path.join(outDir, name);
}

function serializeAttrs(attrs) {
  return Object.fromEntries(attrs.map((name) => [name, null]));
}

async function inspectDashboard(page) {
  return await page.evaluate(() => {
    const attrNames = ['href', 'hx-get', 'hx-target', 'hx-swap', 'hx-push-url'];
    const attrs = (el) => {
      if (!el) return null;
      return Object.fromEntries(attrNames.map((name) => [name, el.getAttribute(name)]));
    };
    const selectorItems = [...document.querySelectorAll('#dashboard-selector .dashboard-selector-item')].map((el) => ({
      text: el.textContent.trim(),
      active: el.classList.contains('active'),
      attrs: attrs(el),
    }));
    const dashboardHome = document.querySelectorAll('#dashboard-home').length;
    const dashboardSelector = document.querySelectorAll('#dashboard-selector').length;
    const dashboardPanel = document.querySelectorAll('#dashboard-panel').length;
    const widgetGrid = document.querySelectorAll('#avatar-widget-grid').length;
    const topNavs = document.querySelectorAll('nav, [data-shell-nav], .top-nav, .app-top-nav').length;
    const editLink = document.querySelector('#dashboard-panel [hx-get*="?edit=true"], #dashboard-panel a[href*="?edit=true"]');
    const exitEditLink = document.querySelector('#dashboard-panel [hx-get$="/_page"], #dashboard-panel a[href]:not([href*="?edit=true"])');
    const detailTrigger = document.querySelector('[data-avatar-detail-trigger]');
    const settingsTrigger = document.querySelector('[data-avatar-settings-trigger]');
    return {
      url: location.href,
      title: document.title,
      htmxLoaded: Boolean(window.htmx),
      counts: { dashboardHome, dashboardSelector, dashboardPanel, widgetGrid, topNavs },
      selectorItems,
      selectedTexts: selectorItems.filter((item) => item.active).map((item) => item.text),
      editLink: attrs(editLink),
      exitEditLink: attrs(exitEditLink),
      firstDetail: detailTrigger ? attrs(detailTrigger) : null,
      firstSettings: settingsTrigger ? attrs(settingsTrigger) : null,
      bodyTextSample: document.body.innerText.slice(0, 2000),
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
}

async function saveHtml(page, name) {
  fs.writeFileSync(file(name), await page.content());
}

async function waitForStable(page) {
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(300);
}

(async () => {
  const consoleMessages = [];
  const network = [];
  const browser = await chromium.launch({
    headless: true,
    executablePath,
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('console', (msg) => {
    consoleMessages.push({ type: msg.type(), text: msg.text(), location: msg.location() });
  });
  page.on('response', (response) => {
    network.push({
      status: response.status(),
      method: response.request().method(),
      url: response.url(),
      resourceType: response.request().resourceType(),
    });
  });
  page.on('pageerror', (error) => {
    consoleMessages.push({ type: 'pageerror', text: error.stack || error.message });
  });

  const results = { checks: {}, observations: {} };

  await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  await page.evaluate(async () => {
    const body = new URLSearchParams({ name: 'Browser Swap Check' });
    await fetch('/dashboards', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'HX-Request': 'true' },
      body: body.toString(),
    });
  });
  await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  results.observations.desktopNormal = await inspectDashboard(page);
  await page.screenshot({ path: file('home-dashboard-normal-desktop.png'), fullPage: true });
  await saveHtml(page, 'home-dashboard-normal-desktop.html');

  const secondSelector = page.locator('#dashboard-selector .dashboard-selector-item').nth(1);
  if (await secondSelector.count()) {
    await secondSelector.click();
    await waitForStable(page);
    results.observations.afterSelectorSwap = await inspectDashboard(page);
    await page.screenshot({ path: file('home-dashboard-after-selector-swap-desktop.png'), fullPage: true });
    await saveHtml(page, 'home-dashboard-after-selector-swap-desktop.html');
  } else {
    results.observations.afterSelectorSwap = { skipped: 'Only one dashboard selector item was present.' };
  }

  await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  const activeEditLink = page.locator('#dashboard-panel [hx-get*="?edit=true"]').first();
  if (await activeEditLink.count()) {
    await activeEditLink.click();
    await waitForStable(page);
  } else {
    await page.goto(`${baseUrl}/dashboards/assistant?edit=true`, { waitUntil: 'networkidle' });
    await waitForStable(page);
  }
  results.observations.desktopEdit = await inspectDashboard(page);
  await page.screenshot({ path: file('home-dashboard-edit-desktop.png'), fullPage: true });
  await saveHtml(page, 'home-dashboard-edit-desktop.html');

  await page.goto(`${baseUrl}/dashboards/assistant?edit=true`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  const detailTrigger = page.locator('[data-avatar-detail-trigger]').first();
  if (await detailTrigger.count()) {
    await detailTrigger.click();
    await waitForStable(page);
    results.observations.detailModal = await page.evaluate(() => ({
      editContainerChildren: document.querySelector('#avatar-edit-container')?.children.length ?? null,
      detailModalCount: document.querySelectorAll('#avatar-widget-detail-modal').length,
      fullShellInsideModal: document.querySelectorAll('#avatar-edit-container #dashboard-home, #avatar-edit-container nav').length,
      text: document.querySelector('#avatar-edit-container')?.innerText.slice(0, 1000) ?? '',
    }));
    await page.screenshot({ path: file('widget-detail-modal-desktop.png'), fullPage: true });
    await saveHtml(page, 'widget-detail-modal-desktop.html');
    await page.evaluate(async () => {
      await fetch('/dashboards/_modal/clear', { headers: { 'HX-Request': 'true' } });
      document.querySelector('#avatar-edit-container').innerHTML = '';
    });
  } else {
    results.observations.detailModal = { skipped: 'No widget detail trigger was present.' };
  }

  await page.goto(`${baseUrl}/dashboards/assistant?edit=true`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  const settingsTrigger = page.locator('[data-avatar-settings-trigger]').first();
  if (await settingsTrigger.count()) {
    await settingsTrigger.click();
    await waitForStable(page);
    results.observations.settingsModal = await page.evaluate(() => ({
      editContainerChildren: document.querySelector('#avatar-edit-container')?.children.length ?? null,
      settingsModalCount: document.querySelectorAll('#avatar-widget-settings-modal').length,
      fullShellInsideModal: document.querySelectorAll('#avatar-edit-container #dashboard-home, #avatar-edit-container nav').length,
      text: document.querySelector('#avatar-edit-container')?.innerText.slice(0, 1000) ?? '',
    }));
    await page.screenshot({ path: file('widget-settings-modal-desktop.png'), fullPage: true });
    await saveHtml(page, 'widget-settings-modal-desktop.html');
  } else {
    results.observations.settingsModal = { skipped: 'No widget settings trigger was present.' };
  }

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  results.observations.mobileNormal = await inspectDashboard(page);
  await page.screenshot({ path: file('home-dashboard-normal-mobile.png'), fullPage: true });
  await saveHtml(page, 'home-dashboard-normal-mobile.html');

  await page.goto(`${baseUrl}/dashboards/assistant?edit=true`, { waitUntil: 'networkidle' });
  await waitForStable(page);
  results.observations.mobileEdit = await inspectDashboard(page);
  await page.screenshot({ path: file('home-dashboard-edit-mobile.png'), fullPage: true });
  await saveHtml(page, 'home-dashboard-edit-mobile.html');

  results.consoleMessages = consoleMessages;
  results.network = network;
  const unexpectedConsoleErrors = consoleMessages.filter((msg) => {
    const favicon404 = msg.type === 'error'
      && msg.text.includes('Failed to load resource')
      && msg.location?.url?.endsWith('/favicon.ico');
    return ['error', 'pageerror'].includes(msg.type) && !favicon404;
  });
  results.observations.ignoredConsoleNoise = consoleMessages.filter((msg) => {
    return msg.type === 'error'
      && msg.text.includes('Failed to load resource')
      && msg.location?.url?.endsWith('/favicon.ico');
  });
  results.checks.noJsErrors = unexpectedConsoleErrors.length === 0;
  results.checks.noUnexpected500s = network.every((entry) => entry.status < 500);
  results.checks.htmxLoaded = results.observations.desktopNormal.htmxLoaded;
  results.checks.selectorRootsStable = ['desktopNormal', 'afterSelectorSwap', 'desktopEdit', 'mobileNormal', 'mobileEdit']
    .every((key) => {
      const item = results.observations[key];
      if (!item || item.skipped) return false;
      return item.counts.dashboardHome === 1
        && item.counts.dashboardSelector === 1
        && item.counts.dashboardPanel === 1
        && item.counts.widgetGrid === 1;
    });
  results.checks.selectorAttrsStable = results.observations.desktopNormal.selectorItems.every((item) => (
    item.attrs.href?.startsWith('/dashboards/')
    && item.attrs['hx-get']?.startsWith('/dashboards/')
    && item.attrs['hx-get']?.endsWith('/_page')
    && item.attrs['hx-target'] === '#dashboard-home'
    && item.attrs['hx-swap'] === 'outerHTML'
    && item.attrs['hx-push-url']?.startsWith('/dashboards/')
  ));
  results.checks.selectedStateAfterSwap = Array.isArray(results.observations.afterSelectorSwap.selectedTexts)
    && results.observations.afterSelectorSwap.selectedTexts.length === 1
    && results.observations.afterSelectorSwap.selectedTexts[0] === 'Browser Swap Check';
  results.checks.fragmentNetworkUsed = network.some((entry) => entry.url.includes('/dashboards/') && entry.url.includes('/_page'));
  results.checks.detailFragmentPractical = results.observations.detailModal.skipped
    || (results.observations.detailModal.detailModalCount === 1 && results.observations.detailModal.fullShellInsideModal === 0);
  results.checks.settingsFragmentPractical = results.observations.settingsModal.skipped
    || (results.observations.settingsModal.settingsModalCount === 1 && results.observations.settingsModal.fullShellInsideModal === 0);
  results.checks.noHorizontalOverflowMobile = results.observations.mobileNormal.scrollWidth <= results.observations.mobileNormal.clientWidth + 2
    && results.observations.mobileEdit.scrollWidth <= results.observations.mobileEdit.clientWidth + 2;

  fs.writeFileSync(file('browser-probe-results.json'), JSON.stringify(results, null, 2));
  fs.writeFileSync(file('console-messages.json'), JSON.stringify(consoleMessages, null, 2));
  fs.writeFileSync(file('network-requests.json'), JSON.stringify(network, null, 2));
  await browser.close();

  const failed = Object.entries(results.checks).filter(([, ok]) => !ok);
  if (failed.length) {
    console.error(`Failed checks: ${failed.map(([name]) => name).join(', ')}`);
    process.exit(1);
  }
})().catch((error) => {
  fs.writeFileSync(file('browser-probe-error.txt'), error.stack || String(error));
  console.error(error);
  process.exit(1);
});
