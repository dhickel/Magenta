const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
  const outDir = 'artifacts/playwright/final-tag-recheck';
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await page.goto('http://localhost:18080/avatar', { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${outDir}/discover-initial.png` });
  const bodyText = await page.locator('body').innerText();
  fs.writeFileSync(`${outDir}/discover-body.txt`, bodyText);
  const classHits = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('[class*="workspace"]')).slice(0,200).map(el => ({
      tag: el.tagName,
      cls: el.className,
      text: (el.textContent || '').trim().slice(0,120)
    }));
  });
  fs.writeFileSync(`${outDir}/discover-workspace-classes.json`, JSON.stringify(classHits, null, 2));
  console.log('body chars', bodyText.length, 'workspace class hits', classHits.length);
  await browser.close();
})();
