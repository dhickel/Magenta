const { test } = require('@playwright/test');
const fs = require('fs');

test('recon avatar', async ({ page }) => {
  const errors = [];
  page.on('console', m => { if (['error','warning'].includes(m.type())) errors.push(`console:${m.type()}:${m.text()}`); });
  page.on('response', r => { if (r.status() >= 400) errors.push(`http:${r.status()}:${r.url()}`); });
  await page.goto('http://localhost:18080/avatar', { waitUntil: 'networkidle' });
  await page.screenshot({ path: '.internal-dev/reviews/artifacts/workarea-browser-remediation-2026-05-27/01-avatar-desktop-initial.png', fullPage: true });
  fs.writeFileSync('.internal-dev/reviews/artifacts/workarea-browser-remediation-2026-05-27/01-avatar-desktop-initial.html', await page.content());
  const cards = await page.locator('text=Work Areas').count();
  console.log('work areas text count', cards);
  console.log('buttons', (await page.locator('button,a,[role="button"]').allTextContents()).map(t=>t.trim()).filter(Boolean).slice(0,100));
  if (errors.length) console.log('errors', errors);
});
