const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
  const outDir = 'artifacts/playwright/final-tag-recheck';
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

  const consoleMsgs = [];
  const networkErrs = [];

  page.on('console', (msg) => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      consoleMsgs.push({ type: msg.type(), text: msg.text() });
    }
  });

  page.on('response', (resp) => {
    if (resp.status() >= 400) {
      networkErrs.push({ status: resp.status(), url: resp.url() });
    }
  });

  await page.goto('http://localhost:18080/avatar', { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForTimeout(1400);

  const workAreaCard = page.getByText('pw-dir-', { exact: false }).first();
  if (await workAreaCard.count()) {
    await workAreaCard.click({ timeout: 5000 }).catch(() => {});
  }
  await page.waitForTimeout(1400);

  const remove = page.locator('.workspace-tag-remove.button.button-link, .workspace-tag-remove').first();
  await remove.waitFor({ state: 'visible', timeout: 15000 });

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

  console.log(JSON.stringify({ style, consoleErrorCount: consoleMsgs.length, networkErrorCount: networkErrs.length }, null, 2));

  await browser.close();
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
