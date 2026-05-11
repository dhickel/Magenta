const { chromium } = require("playwright");
const { execSync } = require("child_process");
const assert = require("node:assert/strict");

const base = "http://localhost:8080";
const executablePath = "/home/hickelpickle/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome";
const dbPath = "chat-memory.db";

function queryDb(sql) {
    const output = execSync(`sqlite3 -json ${dbPath} "${sql}"`).toString();
    return output ? JSON.parse(output) : [];
}

(async () => {
    console.log("Starting DB-UI Synchronization Probe...");
    const browser = await chromium.launch({ headless: true, executablePath });
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });

    page.on('console', msg => console.log(`BROWSER: ${msg.text()}`));
    page.on('pageerror', err => console.error(`BROWSER ERROR: ${err.message}`));

    // 1. Initialize
    console.log("Step 1: Navigating to /chat...");
    await page.goto(base + "/chat", { waitUntil: "networkidle" });
    
    // 2. Action 1: Session Creation (by sending a message)
    console.log("Step 2: Sending message to create session...");
    const testMessage = "Hello, this is a test for DB-UI synchronization. " + Date.now();
    await page.fill("#chat-input", testMessage);
    await page.press("#chat-input", "Enter");

    // Wait for the response to complete. We look for the 'done' event in the UI or just wait for the input to be enabled again.
    // Based on chat-client.js, requestInFlight is true until the stream finishes.
    // We can wait for the submit button to be enabled.
    await page.waitForFunction(() => {
        const btn = document.querySelector('#chat-form button[type="submit"]');
        return btn && !btn.disabled && btn.textContent === 'Send';
    }, { timeout: 30000 });

    const conversationId = await page.evaluate(() => {
        return document.querySelector('[data-chat-root="true"]').getAttribute('data-active-conversation-id');
    });
    console.log(`Created Session ID: ${conversationId}`);

    // Verification 1: Check ai_chat_session_metadata
    console.log("Verifying session metadata in DB...");
    const sessionMetadata = queryDb(`SELECT * FROM ai_chat_session_metadata WHERE conversation_id = '${conversationId}'`);
    assert.equal(sessionMetadata.length, 1, "Session metadata should exist in DB");
    console.log("✓ Session metadata verified.");

    // 3. Action 2: Message Sending Verification
    console.log("Verifying message in ai_chat_memory...");
    const memory = queryDb(`SELECT * FROM ai_chat_memory WHERE conversation_id = '${conversationId}' ORDER BY message_order`);
    // Should have at least 2 messages: user and assistant
    assert.ok(memory.length >= 2, "Should have at least 2 messages in memory");
    assert.ok(memory.some(m => m.message_text.includes(testMessage)), "User message should be in memory");
    console.log("✓ Chat memory verified.");

    console.log("Verifying audit events...");
    const auditEvents = queryDb(`SELECT * FROM audit_event WHERE conversation_id = '${conversationId}' ORDER BY sequence`);
    assert.ok(auditEvents.length > 0, "Audit events should exist for the session");
    console.log(`✓ Found ${auditEvents.length} audit events.`);

    // 4. Action 3: Planning
    console.log("Step 3: Sending /plan command...");
    await page.fill("#chat-input", "/plan");
    await page.press("#chat-input", "Enter");

    // Wait for planning panel to become active
    await page.waitForSelector("#chat-planning-panel.active", { timeout: 30000 });
    console.log("Planning panel is active.");

    // Verification 3: Check ai_chat_plans
    console.log("Verifying plan state in DB...");
    const plans = queryDb(`SELECT * FROM ai_chat_plans WHERE conversation_id = '${conversationId}'`);
    assert.equal(plans.length, 1, "Plan should exist in DB");
    assert.equal(plans[0].mode, "PLAN", "Plan mode should be 'PLAN'");
    console.log("✓ Plan state verified.");

    // 5. Action 4: Status Check
    console.log("Step 4: Verifying session list matches DB...");
    const uiSessions = await page.evaluate(() => {
        return Array.from(document.querySelectorAll("#chat-session-list .chat-session-title-text")).map(el => el.textContent);
    });
    
    const dbSessions = queryDb("SELECT title FROM ai_chat_session_metadata WHERE archived = 0 ORDER BY updated_at DESC");
    const dbTitles = dbSessions.map(s => s.title || "Chat");

    console.log(`UI Sessions: ${uiSessions.length}, DB Sessions: ${dbTitles.length}`);
    // Note: UI might have "New chat" or similar if not persisted, but here we check persisted ones.
    // The session list in UI should contain our new session title.
    const currentSessionTitle = sessionMetadata[0].title || "Chat";
    assert.ok(uiSessions.includes(currentSessionTitle), `UI should show the current session title: ${currentSessionTitle}`);
    
    console.log("✓ Session list consistency verified.");

    console.log("\nProbe Completed Successfully!");
    await browser.close();
})().catch(error => {
    console.error("Probe Failed!");
    console.error(error);
    process.exit(1);
});
