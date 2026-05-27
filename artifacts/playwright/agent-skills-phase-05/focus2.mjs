import { chromium } from 'playwright';
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
await page.goto('http://localhost:18080/skills');
await page.waitForTimeout(1000);
await page.locator('#skills-list').getByText('valid-skill').first().click();
await page.waitForTimeout(800);
const input=page.locator('input[name="agentId"]').first();
await input.click();
await input.fill('phase05');
await page.waitForTimeout(1000);
const options = page.locator('.entity-selector-option, #entity-selector-agent-agentId-results button, #entity-selector-agent-agentId-results [role="option"]');
console.log('optCount', await options.count());
if (await options.count()) {
  await options.first().click();
  await page.waitForTimeout(400);
}
await page.getByRole('button',{name:'Assign'}).first().click();
await page.waitForTimeout(1200);
console.log((await page.locator('#skills-assignment-panel').innerText()).replace(/\s+/g,' ').slice(0,500));
await page.screenshot({path:'artifacts/playwright/agent-skills-phase-05/desktop-focus2-after-assign.png',fullPage:true});
await browser.close();
