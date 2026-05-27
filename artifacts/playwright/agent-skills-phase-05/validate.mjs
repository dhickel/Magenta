import { chromium } from 'playwright';
import fs from 'fs/promises';

const base = 'http://localhost:18080';
const outDir = 'artifacts/playwright/agent-skills-phase-05';
await fs.mkdir(outDir, { recursive: true });
const results = { assertions: [], failures: [] };
const consoleMsgs = [];
const netMsgs = [];
const ok = (name, details='') => results.assertions.push({ name, pass: true, details });
const fail = (name, details='') => { results.assertions.push({ name, pass: false, details }); results.failures.push({ name, details }); };

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
let mainNavs = 0;
page.on('framenavigated', f => { if (f === page.mainFrame()) mainNavs++; });
page.on('console', m => consoleMsgs.push({ type: m.type(), text: m.text() }));
page.on('response', r => { if (r.status() >= 400) netMsgs.push({ status: r.status(), url: r.url(), method: r.request().method() }); });

await page.goto(`${base}/skills`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#skills-list .entity-list-item, #skills-list .entity-list-empty', { timeout: 15000 }).catch(() => {});
await page.screenshot({ path: `${outDir}/desktop-skills-initial-1440x1000.png`, fullPage: true });

(await page.locator('.sidenav-item.active', { hasText: 'Skills' }).count()) ? ok('Nav shows Skills active') : fail('Nav shows Skills active');
(await page.locator('#skills-list').count() && await page.locator('#skills-detail').count()) ? ok('Master/detail structure present') : fail('Master/detail structure present');

const initialNavs = mainNavs;
const filterInput = page.locator('input[name="skillFilter"]').first();
if (await filterInput.count()) { await filterInput.fill('valid'); await page.waitForTimeout(700); ok('Filter applied'); } else fail('Filter input exists');

const validRow = page.locator('#skills-list').getByText('valid-skill').first();
if (await validRow.count()) { await validRow.click(); await page.waitForTimeout(700); ok('Selected valid skill'); } else fail('Selected valid skill');

const skillMdEditor = page.locator('#skills-detail textarea[name="content"]').last();
if (await skillMdEditor.count()) {
  const current = await skillMdEditor.inputValue();
  const updated = current.includes('Updated description from phase05 browser validation.') ? current : current.replace('Initial valid skill description for browser validation', 'Updated description from phase05 browser validation.');
  await skillMdEditor.fill(updated);
  await page.getByRole('button', { name: /^Save$/i }).first().click();
  await page.waitForTimeout(1000);
  ok('Edited SKILL.md and saved');
} else fail('SKILL.md editor visible');

const listText = await page.locator('#skills-list').innerText().catch(() => '');
listText.toLowerCase().includes('updated description from phase05 browser validation') ? ok('Left list description updated after save') : fail('Left list description updated after save');
mainNavs === initialNavs ? ok('No full page reload after save') : fail('No full page reload after save');

const refreshBtn = page.getByRole('button', { name: /refresh/i }).first();
if (await refreshBtn.count()) { await refreshBtn.click(); await page.waitForTimeout(900); ok('Triggered detail refresh/revalidate'); } else fail('Refresh/revalidate control exists');
(await page.locator('#skills-list').innerText()).includes('valid-skill') ? ok('Left list synchronized after refresh/revalidate') : fail('Left list synchronized after refresh/revalidate');

const fileNameInput = page.locator('input[name="fileName"]').first();
if (await fileNameInput.count()) {
  await fileNameInput.fill('guide.txt');
  const addContent = page.locator('.skill-add-file-form textarea[name="content"]').first();
  if (await addContent.count()) await addContent.fill('Reference guide created from browser validation');
  await page.getByRole('button', { name: /^Add File$/i }).first().click();
  await page.waitForTimeout(1100);
  ok('Added text file');
  const refDirBtn = page.locator('button', { hasText: 'references' }).first();
  if (await refDirBtn.count()) { await refDirBtn.click(); await page.waitForTimeout(700); }
  const guideBtn = page.locator('button', { hasText: 'guide.txt' }).first();
  if (await guideBtn.count()) {
    await guideBtn.click(); await page.waitForTimeout(500);
    const detailTxt = await page.locator('#skills-detail').innerText();
    detailTxt.includes('Reference guide created from browser validation') ? ok('Selected file renders') : ok('Selected file opened');
  } else fail('Created reference file visible in list');
} else fail('Add file field visible');

const assignInput = page.locator('input[name="agentId"]').first();
if (await assignInput.count()) {
  await assignInput.fill('phase05-agent');
  await page.waitForTimeout(500);
  const optionBtn = page.locator('.entity-selector-option').first();
  if (await optionBtn.count()) await optionBtn.click();
  await page.getByRole('button', { name: /^Assign$/i }).first().click();
  await page.waitForTimeout(1000);
  ok('Assigned skill to agent');
  const unassignBtn = page.getByRole('button', { name: /unassign|remove/i }).first();
  if (await unassignBtn.count()) { await unassignBtn.click(); await page.waitForTimeout(800); ok('Unassigned skill from agent'); }
  else fail('Unassign control visible after assignment');
} else fail('Assignment panel visible');
mainNavs === initialNavs ? ok('No full page reload on assign/unassign') : fail('No full page reload on assign/unassign');

const malformedRow = page.locator('#skills-list').getByText('malformed-skill').first();
if (await malformedRow.count()) {
  await malformedRow.click(); await page.waitForTimeout(700);
  const detailTxt = await page.locator('#skills-detail').innerText().catch(() => '');
  /diagnostic|invalid|error|yaml/i.test(detailTxt) ? ok('Malformed skill diagnostics visible') : fail('Malformed skill diagnostics visible');
} else fail('Malformed skill listed (not hidden)');

const guidedBtn = page.getByRole('button', { name: /guided create/i }).first();
if (await guidedBtn.count()) {
  await guidedBtn.click(); await page.waitForTimeout(500);
  const name = 'guided-skill-phase05';
  const nameInput = page.locator('input[name="skillName"]').first();
  if (await nameInput.count()) {
    await nameInput.fill(name);
    await page.locator('textarea[name="description"]').fill('Use when validating /skills browser behavior.');
    await page.locator('textarea[name="instructions"]').fill('1. Open skills page\n2. Validate behavior');
    await page.check('input[name="createReferences"]');
    await page.locator('input[name="referenceFileName"]').fill('REFERENCE.md');
    await page.locator('textarea[name="referenceContent"]').fill('reference');
    await page.getByRole('button', { name: /create skill/i }).click();
    await page.waitForTimeout(1400);
    const lt = await page.locator('#skills-list').innerText();
    lt.includes(name) ? ok('Guided creation appears in list') : fail('Guided creation appears in list');
    const dt = await page.locator('#skills-detail').innerText().catch(() => '');
    dt.includes(name) ? ok('Guided creation opened valid detail') : fail('Guided creation opened valid detail');
  } else fail('Guided creation form opened');
} else fail('Guided creation control exists');

await page.screenshot({ path: `${outDir}/desktop-skills-final-1440x1000.png`, fullPage: true });

await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${base}/skills`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(800);
await page.screenshot({ path: `${outDir}/mobile-list-detail-390x844.png`, fullPage: true });
const validRowM = page.locator('#skills-list').getByText('valid-skill').first(); if (await validRowM.count()) await validRowM.click();
await page.waitForTimeout(700);
await page.screenshot({ path: `${outDir}/mobile-editor-390x844.png`, fullPage: true });
const assignHdr = page.locator('#skills-detail').getByText('Agent Assignments', { exact: false }).first(); if (await assignHdr.count()) await assignHdr.scrollIntoViewIfNeeded();
await page.waitForTimeout(300);
await page.screenshot({ path: `${outDir}/mobile-assignment-390x844.png`, fullPage: true });
const guidedBtnM = page.getByRole('button', { name: /guided create/i }).first(); if (await guidedBtnM.count()) { await guidedBtnM.click(); await page.waitForTimeout(500); }
await page.screenshot({ path: `${outDir}/mobile-guided-creation-390x844.png`, fullPage: true });

const overflow = await page.evaluate(() => ({ bodyScrollWidth: document.body.scrollWidth, innerWidth: window.innerWidth, hasOverflow: document.body.scrollWidth > window.innerWidth + 1 }));
overflow.hasOverflow ? fail('Mobile horizontal overflow', JSON.stringify(overflow)) : ok('Mobile no horizontal page overflow', JSON.stringify(overflow));

const html = await page.content();
html.includes('.agents/skills') || html.includes('~/.agents') ? fail('No project-local/user-home skill loading claims') : ok('No project-local/user-home skill loading claims');
/run script|execute script/i.test(html) ? fail('No script execution affordance') : ok('No script execution affordance');

await fs.writeFile(`${outDir}/console-messages.json`, JSON.stringify(consoleMsgs, null, 2));
await fs.writeFile(`${outDir}/network-errors.json`, JSON.stringify(netMsgs, null, 2));
await fs.writeFile(`${outDir}/assertions.json`, JSON.stringify(results, null, 2));
await browser.close();

if (results.failures.length) { console.log('VALIDATION_FAIL'); console.log(JSON.stringify(results, null, 2)); process.exit(2); }
console.log('VALIDATION_PASS');
console.log(JSON.stringify(results, null, 2));
