const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:18081';
const OUT_DIR = __dirname;

function out(name) {
  return path.join(OUT_DIR, name);
}

function rel(name) {
  return path.relative(process.cwd(), out(name));
}

async function screenshot(page, name, fullPage = true) {
  await page.screenshot({ path: out(name), fullPage });
  return rel(name);
}

async function visibleText(page, selector = 'body') {
  return await page.locator(selector).evaluate((el) => el.innerText || '');
}

async function pageMetrics(page) {
  return await page.evaluate(() => ({
    url: location.href,
    title: document.title,
    innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    bodyText: document.body.innerText,
    visibleAvatarTextCount: (document.body.innerText.match(/\bAvatar\b/g) || []).length,
    visibleAssistantTextCount: (document.body.innerText.match(/\bAssistant\b/g) || []).length,
    htmxUrls: Array.from(document.querySelectorAll('[hx-get],[hx-post],[hx-put],[hx-delete],form[action],a[href]'))
      .map((el) => ({
        tag: el.tagName.toLowerCase(),
        text: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 120),
        href: el.getAttribute('href'),
        action: el.getAttribute('action'),
        hxGet: el.getAttribute('hx-get'),
        hxPost: el.getAttribute('hx-post'),
        hxPut: el.getAttribute('hx-put'),
        hxDelete: el.getAttribute('hx-delete'),
      }))
      .filter((x) => JSON.stringify(x).includes('/agents') || JSON.stringify(x).includes('/dashboards') || JSON.stringify(x).includes('work-areas')),
  }));
}

async function waitSettled(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(350);
}

async function clickFirst(page, candidates) {
  for (const candidate of candidates) {
    const loc = typeof candidate === 'string' ? page.locator(candidate) : candidate;
    if (await loc.count()) {
      const first = loc.first();
      if (await first.isVisible().catch(() => false)) {
        await first.click();
        await waitSettled(page);
        return true;
      }
    }
  }
  return false;
}

async function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const consoleMessages = [];
  const networkIssues = [];
  page.on('console', (msg) => {
    if (['error', 'warning'].includes(msg.type())) {
      consoleMessages.push({ type: msg.type(), text: msg.text(), url: page.url() });
    }
  });
  page.on('requestfailed', (req) => networkIssues.push({ url: req.url(), failure: req.failure()?.errorText }));
  page.on('response', (res) => {
    if (res.status() >= 400 && !res.url().includes('/favicon')) {
      networkIssues.push({ url: res.url(), status: res.status() });
    }
  });

  const evidence = {
    baseUrl: BASE_URL,
    screenshots: [],
    checks: {},
    consoleMessages,
    networkIssues,
  };

  async function goto(route) {
    await page.goto(`${BASE_URL}${route}`);
    await waitSettled(page);
  }

  await goto('/');
  evidence.screenshots.push(await screenshot(page, 'root-desktop.png'));
  evidence.checks.rootDesktop = await pageMetrics(page);
  evidence.checks.rootDesktop.hasDashboardSelector = await page.locator('[data-dashboard-selector="true"], .dashboard-selector').count();
  evidence.checks.rootDesktop.assistantSelected = await page.locator('.dashboard-selector-item.active', { hasText: 'Assistant' }).count();
  evidence.checks.rootDesktop.hasChatRail = await page.locator('[data-avatar-chat-rail="true"], .avatar-shell-rail, #avatar-chat').count();
  evidence.checks.rootDesktop.hasWorkAreasText = (await visibleText(page)).includes('Work Areas');
  evidence.checks.rootDesktop.noHorizontalOverflow = evidence.checks.rootDesktop.scrollWidth <= evidence.checks.rootDesktop.innerWidth + 2;

  await page.setViewportSize({ width: 390, height: 844 });
  await goto('/');
  evidence.screenshots.push(await screenshot(page, 'root-mobile.png'));
  evidence.checks.rootMobile = await pageMetrics(page);
  evidence.checks.rootMobile.noHorizontalOverflow = evidence.checks.rootMobile.scrollWidth <= evidence.checks.rootMobile.innerWidth + 2;

  await page.setViewportSize({ width: 1440, height: 1000 });
  await goto('/');
  await page.getByRole('button', { name: 'Create dashboard' }).click();
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'create-modal-desktop-open.png'));
  evidence.checks.createDesktop = await page.evaluate(() => {
    const input = document.querySelector('#avatar-edit-container input[name="name"], input[name="name"]');
    const form = input?.closest('form');
    const submit = form?.querySelector('[type="submit"], button:not([type]), button[type="button"]');
    return {
      inputExists: !!input,
      required: !!input?.required,
      requiredAttribute: input?.hasAttribute('required') || false,
      blankCheckValidityBeforeSubmit: input?.checkValidity() ?? null,
      formAction: form?.getAttribute('action') || null,
      hxPost: form?.getAttribute('hx-post') || null,
      submitText: submit?.innerText || submit?.getAttribute('aria-label') || null,
    };
  });
  await page.locator('#avatar-edit-container form [type="submit"], #avatar-edit-container form button').last().click();
  await page.waitForTimeout(250);
  evidence.screenshots.push(await screenshot(page, 'create-modal-desktop-blank-submit.png'));
  evidence.checks.createDesktop.afterBlankSubmit = await page.evaluate(() => {
    const input = document.querySelector('#avatar-edit-container input[name="name"], input[name="name"]');
    return {
      url: location.href,
      inputInvalid: input?.matches(':invalid') ?? null,
      validationMessage: input?.validationMessage || '',
      ariaInvalid: input?.getAttribute('aria-invalid') || null,
      visibleError: document.body.innerText.match(/required|error|invalid|name/i)?.[0] || null,
    };
  });
  const uniqueName = `PW Recheck ${Date.now()}`;
  await page.locator('#avatar-edit-container input[name="name"], input[name="name"]').first().fill(uniqueName);
  await page.locator('#avatar-edit-container form [type="submit"], #avatar-edit-container form button').last().click();
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'created-dashboard-desktop.png'));
  evidence.checks.createDesktop.createdDashboardName = uniqueName;
  evidence.checks.createDesktop.createdDashboardVisible = (await visibleText(page)).includes(uniqueName);
  evidence.checks.createDesktop.createdDashboardSelected = await page.locator('.dashboard-selector-item.active', { hasText: uniqueName }).count();

  await page.setViewportSize({ width: 390, height: 844 });
  await goto('/');
  await page.getByRole('button', { name: 'Create dashboard' }).click();
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'create-modal-mobile-open.png'));
  evidence.checks.createMobile = await page.evaluate(() => {
    const input = document.querySelector('#avatar-edit-container input[name="name"], input[name="name"]');
    const modal = document.querySelector('#avatar-edit-container');
    return {
      required: !!input?.required,
      inputInvalidBlank: input ? !input.checkValidity() : null,
      modalRect: modal ? (() => {
        const r = modal.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height, bottom: r.bottom };
      })() : null,
      noHorizontalOverflow: document.documentElement.scrollWidth <= innerWidth + 2,
    };
  });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await goto('/dashboards/assistant?edit=true');
  evidence.screenshots.push(await screenshot(page, 'assistant-edit-desktop.png'));
  evidence.checks.assistantEdit = {
    text: await visibleText(page),
    rowControls: await page.locator('[data-avatar-row-id] button, .avatar-row-edit-controls button, .avatar-insert-row-section button').count(),
    widgetControls: await page.locator('.avatar-widget-corner-controls button, [data-avatar-widget] button').count(),
    noHorizontalOverflow: await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2),
  };

  await goto('/manage');
  evidence.screenshots.push(await screenshot(page, 'manage-desktop.png'));
  evidence.checks.manage = await pageMetrics(page);
  evidence.checks.manage.topNavOrder = await page.locator('.top-nav a, nav a').evaluateAll((els) => els.map((el) => el.innerText.trim()).filter(Boolean).slice(0, 4));
  evidence.checks.manage.systemBeforeOrchestration = await page.evaluate(() => {
    const text = document.body.innerText;
    return text.indexOf('System') >= 0 && text.indexOf('Orchestration') >= 0 && text.indexOf('System') < text.indexOf('Orchestration');
  });

  await goto('/agents');
  evidence.screenshots.push(await screenshot(page, 'agents-desktop.png'));
  evidence.checks.agents = await pageMetrics(page);
  evidence.checks.agents.avatarRouteLinks = await page.locator('a[href="/agents/avatar"], a[href$="/agents/avatar"]').count();
  evidence.checks.agents.assistantVisible = (await visibleText(page)).includes('Assistant');

  await goto('/agents/avatar');
  evidence.screenshots.push(await screenshot(page, 'agent-avatar-detail-desktop.png'));
  evidence.checks.agentAvatarDesktop = await pageMetrics(page);
  evidence.checks.agentAvatarDesktop.headingText = await page.locator('h1,h2').evaluateAll((els) => els.map((el) => el.innerText.trim()).filter(Boolean).slice(0, 8));

  await page.setViewportSize({ width: 390, height: 844 });
  await goto('/agents/avatar');
  evidence.screenshots.push(await screenshot(page, 'agent-avatar-detail-mobile.png'));
  evidence.checks.agentAvatarMobile = await pageMetrics(page);
  evidence.checks.agentAvatarMobile.noHorizontalOverflow = evidence.checks.agentAvatarMobile.scrollWidth <= evidence.checks.agentAvatarMobile.innerWidth + 2;

  await page.setViewportSize({ width: 1440, height: 1000 });
  await goto('/agents/avatar');
  await clickFirst(page, [
    page.getByRole('link', { name: /work areas/i }),
    page.getByRole('button', { name: /work areas/i }),
    'a:has-text("Work Areas")',
    'button:has-text("Work Areas")',
  ]);
  evidence.screenshots.push(await screenshot(page, 'agent-workareas-tab-desktop.png'));
  evidence.checks.workAreasTab = await pageMetrics(page);
  evidence.checks.workAreasTab.legacyAvatarWorkAreaNameVisible = (await visibleText(page)).includes('Avatar');
  evidence.checks.workAreasTab.agentWorkAreaRoutes = evidence.checks.workAreasTab.htmxUrls
    .filter((x) => JSON.stringify(x).includes('/agents/_detail/avatar/work-areas'));
  evidence.checks.workAreasTab.oldAvatarRoutes = evidence.checks.workAreasTab.htmxUrls
    .filter((x) => JSON.stringify(x).includes('/avatar/_work-areas'));

  await clickFirst(page, [
    'a:has-text("Browse")',
    'button:has-text("Browse")',
    '[hx-get*="/work-areas"][hx-get*="explorer"]',
    '[hx-get*="/work-areas"][hx-get*="browse"]',
    '[href*="/work-areas"]',
  ]);
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'agent-workarea-explorer-desktop.png'));
  evidence.checks.workAreaExplorerDesktop = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('tr, .workspace-file-row, .workarea-file-row, [data-workarea-file-row]'));
    const buttons = Array.from(document.querySelectorAll('button, a'));
    const inspector = document.querySelector('.workspace-inspector, .workarea-inspector, [data-workarea-inspector], [data-file-inspector]');
    const editor = document.querySelector('.workspace-editor, .workarea-editor, [data-workarea-editor], [data-file-editor]');
    const longTextEls = Array.from(document.querySelectorAll('td, .workspace-file-name, .workarea-file-name, [data-file-name]'))
      .map((el) => {
        const r = el.getBoundingClientRect();
        return { text: (el.innerText || '').trim(), width: r.width, scrollWidth: el.scrollWidth };
      })
      .filter((x) => x.text.length > 20)
      .slice(0, 10);
    const tinyControls = buttons.map((el) => {
      const r = el.getBoundingClientRect();
      return { text: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim(), width: r.width, height: r.height, visible: r.width > 0 && r.height > 0 };
    }).filter((b) => b.visible && (b.width < 22 || b.height < 22));
    return {
      url: location.href,
      text: document.body.innerText,
      rowCount: rows.length,
      buttonCount: buttons.length,
      tinyControls,
      longTextEls,
      inspectorPresent: !!inspector,
      inspectorText: inspector?.innerText?.slice(0, 500) || '',
      editorPresent: !!editor,
      noHorizontalOverflow: document.documentElement.scrollWidth <= innerWidth + 2,
      htmxUrls: Array.from(document.querySelectorAll('[hx-get],[hx-post],[hx-put],[hx-delete],form[action],a[href]')).map((el) => ({
        text: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 100),
        href: el.getAttribute('href'),
        action: el.getAttribute('action'),
        hxGet: el.getAttribute('hx-get'),
        hxPost: el.getAttribute('hx-post'),
        hxPut: el.getAttribute('hx-put'),
        hxDelete: el.getAttribute('hx-delete'),
      })).filter((x) => JSON.stringify(x).includes('work-areas')),
    };
  });

  await clickFirst(page, [
    'tr:has-text(".md")',
    'tr:has-text(".txt")',
    '.workspace-file-row',
    '.workarea-file-row',
    '[data-workarea-file-row]',
  ]);
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'agent-workarea-file-selected-desktop.png'));
  evidence.checks.workAreaExplorerSelected = await page.evaluate(() => ({
    selectedRows: document.querySelectorAll('.selected, .is-selected, [aria-selected="true"]').length,
    inspectorText: document.querySelector('.workspace-inspector, .workarea-inspector, [data-workarea-inspector], [data-file-inspector]')?.innerText?.slice(0, 800) || '',
    editorTabs: Array.from(document.querySelectorAll('button, a')).map((el) => (el.innerText || el.getAttribute('aria-label') || '').trim()).filter((t) => /preview|edit|save|undo|redo|revert|raw/i.test(t)).slice(0, 20),
  }));

  await clickFirst(page, [
    'button:has-text("Tags")',
    'a:has-text("Tags")',
    'button[aria-label*="tag" i]',
    'a[aria-label*="tag" i]',
    '[hx-get*="tags"]',
  ]);
  await waitSettled(page);
  evidence.screenshots.push(await screenshot(page, 'agent-workarea-modal-desktop.png'));
  evidence.checks.workAreaModalDesktop = await page.evaluate(() => {
    const modal = document.querySelector('.modal, [role="dialog"], #avatar-edit-container, #workarea-modal');
    if (!modal) return { present: false };
    const r = modal.getBoundingClientRect();
    return {
      present: true,
      text: modal.innerText.slice(0, 800),
      rect: { x: r.x, y: r.y, width: r.width, height: r.height, bottom: r.bottom },
      fitsViewport: r.bottom <= innerHeight + 2 && r.width <= innerWidth + 2,
    };
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await goto('/agents/avatar');
  await clickFirst(page, [
    page.getByRole('link', { name: /work areas/i }),
    page.getByRole('button', { name: /work areas/i }),
    'a:has-text("Work Areas")',
    'button:has-text("Work Areas")',
  ]);
  await clickFirst(page, [
    'a:has-text("Browse")',
    'button:has-text("Browse")',
    '[hx-get*="/work-areas"][hx-get*="explorer"]',
    '[hx-get*="/work-areas"][hx-get*="browse"]',
    '[href*="/work-areas"]',
  ]);
  evidence.screenshots.push(await screenshot(page, 'agent-workarea-explorer-mobile.png'));
  evidence.checks.workAreaExplorerMobile = await page.evaluate(() => ({
    url: location.href,
    text: document.body.innerText,
    noHorizontalOverflow: document.documentElement.scrollWidth <= innerWidth + 2,
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth,
    rowCount: document.querySelectorAll('tr, .workspace-file-row, .workarea-file-row, [data-workarea-file-row]').length,
    buttonCount: document.querySelectorAll('button, a').length,
    htmxWorkAreaUrls: Array.from(document.querySelectorAll('[hx-get],[hx-post],[hx-put],[hx-delete],form[action],a[href]')).map((el) => ({
      text: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim().slice(0, 100),
      href: el.getAttribute('href'),
      action: el.getAttribute('action'),
      hxGet: el.getAttribute('hx-get'),
      hxPost: el.getAttribute('hx-post'),
      hxPut: el.getAttribute('hx-put'),
      hxDelete: el.getAttribute('hx-delete'),
    })).filter((x) => JSON.stringify(x).includes('work-areas')),
  }));

  await goto('/agents');
  const nonAvatarHref = await page.locator('a[href^="/agents/"]').evaluateAll((els) => {
    const hrefs = els.map((el) => el.getAttribute('href')).filter(Boolean);
    return hrefs.find((href) => href !== '/agents/avatar' && !href.includes('_detail')) || null;
  });
  evidence.checks.nonAvatarAgentHref = nonAvatarHref;
  if (nonAvatarHref) {
    await goto(nonAvatarHref);
    evidence.screenshots.push(await screenshot(page, 'agent-detail-non-avatar-desktop.png'));
    evidence.checks.nonAvatarAgentDetail = await pageMetrics(page);
  }

  await browser.close();
  fs.writeFileSync(out('browser-recheck-results.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify({
    resultFile: rel('browser-recheck-results.json'),
    screenshots: evidence.screenshots,
    consoleMessages: consoleMessages.length,
    networkIssues: networkIssues.length,
  }, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
