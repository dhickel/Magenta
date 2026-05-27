const { test, expect } = require('@playwright/test');
const fs = require('fs');

test('avatar tag remove style recheck', async ({ page }) => {
  const outDir = 'artifacts/playwright/final-tag-recheck';
  fs.mkdirSync(outDir, { recursive: true });

  const consoleMsgs = [];
  const networkErrs = [];

  page.on('console', msg => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      consoleMsgs.push({ type: msg.type(), text: msg.text() });
    }
  });

  page.on('response', resp => {
    if (resp.status() >= 400) {
      networkErrs.push({ status: resp.status(), url: resp.url() });
    }
  });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://localhost:18080/avatar', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);

  const workAreasTrigger = page.getByText('Work Areas').first();
  if (await workAreasTrigger.count()) {
    await workAreasTrigger.click({ timeout: 3000 }).catch(() => {});
  }
  await page.waitForTimeout(800);

  const remove = page.locator('.workspace-tag-remove.button.button-link, .workspace-tag-remove').first();
  await expect(remove).toBeVisible({ timeout: 15000 });

  const style = await remove.evaluate((el) => {
    const cs = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return {
      text: (el.textContent || '').trim(),
      classes: el.className,
      color: cs.color,
      display: cs.display,
      alignItems: cs.alignItems,
      justifyContent: cs.justifyContent,
      width: rect.width,
      height: rect.height,
    };
  });

  await remove.scrollIntoViewIfNeeded();
  await page.waitForTimeout(250);
  await page.screenshot({ path: `${outDir}/avatar-workarea-tag-remove-recheck.png` });

  fs.writeFileSync(`${outDir}/computed-style.json`, JSON.stringify(style, null, 2));
  fs.writeFileSync(`${outDir}/console-errors.json`, JSON.stringify(consoleMsgs, null, 2));
  fs.writeFileSync(`${outDir}/network-errors.json`, JSON.stringify(networkErrs, null, 2));
  fs.writeFileSync(`${outDir}/summary.json`, JSON.stringify({ style, consoleErrorCount: consoleMsgs.length, networkErrorCount: networkErrs.length }, null, 2));
});
