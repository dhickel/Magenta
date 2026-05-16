const assert = require("node:assert/strict");
const { chromium } = require("playwright");

const base = process.env.MAGENTA_VALIDATION_BASE_URL || "http://localhost:18080";
const executablePath = process.env.MAGENTA_VALIDATION_CHROMIUM
    || "/home/hickelpickle/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome";

(async () => {
    const browser = await chromium.launch({ headless: true, executablePath });
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const consoleMessages = [];
    const responses = [];
    page.on("console", msg => consoleMessages.push({ type: msg.type(), text: msg.text() }));
    page.on("response", res => responses.push({ url: res.url(), status: res.status() }));

    async function api(path, options = {}) {
        return page.evaluate(async ({ base, path, options }) => {
            const response = await fetch(base + path, {
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    ...(options.headers || {})
                },
                ...options
            });
            const text = await response.text();
            let body = text;
            try {
                body = text ? JSON.parse(text) : null;
            } catch (_ignored) {
            }
            return { status: response.status, ok: response.ok, body };
        }, { base, path, options });
    }

    await page.goto(base + "/chat", { waitUntil: "networkidle" });
    assert.equal(await page.title(), "Magenta Chat");
    for (const selector of [
        "[data-chat-root=true]",
        "#chat-form",
        "#chat-input",
        "#chat-model-select",
        "#chat-planning-model-select",
        "#chat-history",
        "#chat-planning-panel"
    ]) {
        assert.equal(await page.locator(selector).count(), 1, `missing ${selector}`);
    }
    const htmxResponse = await page.goto(base + "/webjars/htmx.org/dist/htmx.min.js");
    assert.equal(htmxResponse.status(), 200);

    await page.goto(base + "/agents", { waitUntil: "networkidle" });
    assert.equal(await page.locator("#agents-page").count(), 1);
    const seededAgents = (await api("/api/agents")).body;
    assert.ok(seededAgents.length >= 1);

    const createdAgent = (await api("/api/agents", {
        method: "POST",
        body: JSON.stringify({
            name: `Validation Agent ${Date.now()}`,
            defaultModel: "local-qwen",
            systemPrompt: "validate",
            approvedTools: ["file_read"],
            allowedShellCommands: ["printf"],
            directLineEnabled: true
        })
    })).body;
    assert.equal(createdAgent.status, "ACTIVE");
    const cloned = (await api(`/api/agents/${createdAgent.id}/clone`, { method: "POST" })).body;
    assert.notEqual(cloned.id, createdAgent.id);
    const updated = (await api(`/api/agents/${createdAgent.id}`, {
        method: "PUT",
        body: JSON.stringify({ ...createdAgent, systemPrompt: "updated", allowedShellCommands: ["echo"] })
    })).body;
    assert.equal(updated.systemPrompt, "updated");

    await page.goto(base + "/agents", { waitUntil: "networkidle" });
    await page.locator(".agent-card-name", { hasText: createdAgent.name }).click();
    await page.waitForSelector(".agent-chat-accordion");
    for (const selector of ["#agent-detail-page", "#agent-profile-form", "#agent-assignment-form", ".agent-chat-accordion"]) {
        assert.equal(await page.locator(selector).count(), 1, `missing ${selector}`);
    }
    assert.equal(await page.locator(".agent-chat-accordion[open]").count(), 0);
    await page.click(".agent-chat-accordion > summary");
    assert.equal(await page.locator(".agent-chat-accordion[open]").count(), 1);
    assert.ok(await page.locator("#agent-chat-panel").evaluate(node => node.getBoundingClientRect().height > 250));
    assert.equal(await page.locator(".agent-event-log").count(), 1);
    await page.click("[data-tab=workspace]");
    await page.waitForTimeout(250);
    assert.match(await page.locator("#agent-tab-panel").innerText(), /rootRelativePath/);

    const inbox = await api(`/api/agents/${createdAgent.id}/inbox`, {
        method: "POST",
        body: JSON.stringify({ fromId: "validation", messageType: "note", body: "hello", metadata: { validation: true } })
    });
    assert.equal(inbox.ok, true);

    const waiting = (await api(`/api/agents/${createdAgent.id}/assignments`, {
        method: "POST",
        body: JSON.stringify({ assignmentType: "WAIT_FOR_MESSAGE", priority: 5, input: {} })
    })).body;
    await page.waitForTimeout(2500);
    let assignments = (await api(`/api/agents/${createdAgent.id}/assignments`)).body;
    assert.ok(assignments.some(a => a.id === waiting.id && ["WAITING", "RUNNING", "QUEUED"].includes(a.status)));
    assert.equal((await api(`/api/agents/${createdAgent.id}/assignments/${waiting.id}/pause`, { method: "POST" })).body.status, "PAUSED");
    assert.equal((await api(`/api/agents/${createdAgent.id}/assignments/${waiting.id}/resume`, { method: "POST" })).body.status, "QUEUED");

    const cancelTarget = (await api(`/api/agents/${createdAgent.id}/assignments`, {
        method: "POST",
        body: JSON.stringify({ assignmentType: "WAIT_FOR_MESSAGE", priority: -5, input: {} })
    })).body;
    assert.ok(["CANCELLED", "CANCEL_REQUESTED"].includes(
        (await api(`/api/agents/${createdAgent.id}/assignments/${cancelTarget.id}/cancel`, { method: "POST" })).body.status
    ));

    assert.equal((await api(`/api/agents/${createdAgent.id}/schedules`, {
        method: "POST",
        body: JSON.stringify({ assignmentTemplate: {}, cronExpression: "bad cron", timezone: "UTC", enabled: true })
    })).status, 400);
    assert.equal((await api(`/api/agents/${createdAgent.id}/event-reactions`, {
        method: "POST",
        body: JSON.stringify({
            eventType: "INBOX_MESSAGE_RECEIVED",
            filter: { messageType: "validation_ping" },
            actionType: "ENQUEUE_ASSIGNMENT",
            assignmentTemplate: { assignmentType: "REPORT", input: { message: "reacted" } },
            enabled: true
        })
    })).ok, true);
    await api(`/api/agents/${createdAgent.id}/inbox`, {
        method: "POST",
        body: JSON.stringify({ fromId: "validation", messageType: "validation_ping", body: "wake" })
    });
    assignments = (await api(`/api/agents/${createdAgent.id}/assignments`)).body;
    assert.ok(assignments.some(a => a.assignmentType === "REPORT"));

    await page.goto(base + "/jobs", { waitUntil: "networkidle" });
    assert.equal(await page.locator("#jobs-page").count(), 1);
    const job = (await api("/api/jobs", {
        method: "POST",
        body: JSON.stringify({
            ownerAgentId: createdAgent.id,
            title: "Validation Job",
            summary: "browser validation",
            defaultModel: "local-qwen",
            status: "QUEUED"
        })
    })).body;
    assert.ok(job.workspaceId);
    await api(`/api/jobs/${job.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemOrder: 1, itemType: "REPORT", priority: 0, config: { message: "first" } })
    });
    await api(`/api/jobs/${job.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemOrder: 2, itemType: "REPORT", priority: 0, config: { message: "second" }, modelOverride: "local-qwen" })
    });
    const run = (await api(`/api/jobs/${job.id}/runs`, {
        method: "POST",
        body: JSON.stringify({ priority: 3 })
    })).body;
    assert.equal(run.assignmentType, "JOB_RUN");
    await page.waitForTimeout(3500);
    const runs = (await api(`/api/jobs/${job.id}/runs`)).body;
    assert.ok(runs.some(r => r.id === run.id && ["COMPLETED", "RUNNING", "QUEUED"].includes(r.status)));

    await page.goto(base + `/jobs/${job.id}`, { waitUntil: "networkidle" });
    for (const selector of ["#job-detail-page", "#job-editor-form", "#job-item-editor", "#job-runs", "[data-agent-chat-toggle]"]) {
        assert.equal(await page.locator(selector).count(), 1, `missing ${selector}`);
    }

    await page.goto(base + "/tasks", { waitUntil: "networkidle" });
    assert.equal(await page.locator("#tasks-page").count(), 1);
    assert.equal(await page.locator("#task-run-agent-id").count(), 1);
    assert.equal(await page.locator("[data-agent-chat-toggle]").count(), 1);

    await page.goto(base + "/workflows", { waitUntil: "networkidle" });
    assert.equal(await page.locator("#workflows-page").count(), 1);
    assert.equal(await page.locator("#workflow-run-agent-id").count(), 1);
    assert.equal(await page.locator("[data-agent-chat-toggle]").count(), 1);

    await api(`/api/agents/${cloned.id}`, { method: "DELETE" });
    assert.equal((await api(`/api/agents/${cloned.id}`)).body.status, "DISABLED");

    const unexpectedConsole = consoleMessages.filter(msg =>
        msg.type === "error" && !msg.text.includes("400") && !msg.text.includes("Failed to load resource")
    );
    assert.deepEqual(unexpectedConsole, []);
    assert.deepEqual(responses.filter(response => response.status >= 500), []);

    console.log(JSON.stringify({
        ok: true,
        agentId: createdAgent.id,
        jobId: job.id,
        runStatuses: runs.map(r => ({ id: r.id, status: r.status, currentItemIndex: r.currentItemIndex }))
    }, null, 2));
    await browser.close();
})().catch(error => {
    console.error(error.stack || error);
    process.exit(1);
});
