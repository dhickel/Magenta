import { jsonFetch, renderError } from "./api.js";
import { $, $$, bindTabs, chip, escapeHtml, formValue, modelOptions } from "./dom.js";
import { initAgentChat } from "./agent-chat.js";

const ORCHESTRATION_ENDPOINTS = ["/api/settings/runtime", "/api/agents", "/api/jobs", "/api/tasks", "/api/workflows"];

document.addEventListener("DOMContentLoaded", () => {
    initAgentChat();
    const page = $("[data-orchestration-page]");
    if (!page) return;
    const name = page.dataset.orchestrationPage;
    if (name === "settings") initSettings(page);
    if (name === "agents") initAgents(page);
    if (name === "agent-detail") initAgentDetail(page);
    if (name === "jobs") initJobs(page);
    if (name === "job-detail") initJobDetail(page);
});

async function modelCatalog() {
    const [settings, agents] = await Promise.all([
        jsonFetch("/api/settings/runtime").catch(() => ({})),
        jsonFetch("/api/agents").catch(() => [])
    ]);
    return [...new Set([
        settings.defaultModel,
        settings.planningModel,
        settings.summaryModel,
        settings.compactionModel,
        ...agents.map(agent => agent.defaultModel)
    ].filter(Boolean))];
}

async function initSettings(root) {
    const status = $("#settings-status", root);
    try {
        const [settings, models] = await Promise.all([jsonFetch("/api/settings/runtime"), modelCatalog()]);
        for (const [key, value] of Object.entries(settings)) {
            const field = $(`[name="${key}"]`, root);
            if (field) field.value = value ?? "";
        }
        ["defaultModel", "planningModel", "summaryModel", "compactionModel"].forEach(key => {
            const field = $(`[name="${key}"]`, root);
            if (field) field.innerHTML = modelOptions(models, settings[key]);
        });
        $("#settings-model-list", root).innerHTML = models.map(model => `<span class="orch-chip">${escapeHtml(model)}</span>`).join("");
        $("[data-action='save-settings']", root).addEventListener("click", async () => {
            const body = {
                defaultAgentId: formValue(root, "defaultAgentId"),
                defaultAgentName: formValue(root, "defaultAgentName"),
                defaultModel: formValue(root, "defaultModel"),
                planningModel: formValue(root, "planningModel"),
                summaryModel: formValue(root, "summaryModel"),
                compactionModel: formValue(root, "compactionModel"),
                contextBufferPercent: formValue(root, "contextBufferPercent")
            };
            await jsonFetch("/api/settings/runtime", { method: "PUT", body: JSON.stringify(body) });
            status.textContent = "Saved.";
        });
    } catch (error) {
        renderError(status, error);
    }
}

async function initAgents(root) {
    const cards = $("#agent-cards", root);
    async function load() {
        const agents = await jsonFetch("/api/agents");
        const filter = $("#agent-filter", root).value.toLowerCase();
        cards.innerHTML = agents
            .filter(agent => !filter || agent.name.toLowerCase().includes(filter))
            .map(agentCard)
            .join("");
        $$("[data-action='clone-agent']", cards).forEach(button => button.addEventListener("click", async () => {
            await jsonFetch(`/api/agents/${button.dataset.agentId}/clone`, { method: "POST" });
            await load();
        }));
        $$("[data-action='delete-agent']", cards).forEach(button => button.addEventListener("click", async () => {
            if (!confirm("Delete or disable this agent?")) return;
            await jsonFetch(`/api/agents/${button.dataset.agentId}`, { method: "DELETE" });
            await load();
        }));
    }
    $("[data-action='reload-agents']", root).addEventListener("click", load);
    $("#agent-filter", root).addEventListener("input", load);
    $("[data-action='create-agent']", root).addEventListener("click", async () => {
        const now = Date.now().toString(36);
        const created = await jsonFetch("/api/agents", {
            method: "POST",
            body: JSON.stringify({
                name: `Agent ${now}`,
                defaultModel: "",
                systemPrompt: "",
                approvedTools: [],
                allowedShellCommands: [],
                directLineEnabled: false
            })
        });
        location.href = `/agents/${created.id}`;
    });
    await load();
}

function agentCard(agent) {
    return `
        <article class="orch-card">
            <h3><a href="/agents/${escapeHtml(agent.id)}">${escapeHtml(agent.name)}</a></h3>
            ${chip(agent.status)}
            <div class="orch-meta">
                <span>Model: ${escapeHtml(agent.defaultModel || "unset")}</span>
                <span>Queue: <span data-agent-queue="${escapeHtml(agent.id)}">-</span></span>
                <span>Inbox: <span data-agent-inbox="${escapeHtml(agent.id)}">-</span></span>
                <span>Recent: <span data-agent-recent="${escapeHtml(agent.id)}">not loaded</span></span>
            </div>
            <div class="orch-actions">
                <a href="/agents/${escapeHtml(agent.id)}">Open</a>
                <button type="button" data-action="clone-agent" data-agent-id="${escapeHtml(agent.id)}">Clone</button>
                <button type="button" data-action="delete-agent" data-agent-id="${escapeHtml(agent.id)}">Delete</button>
            </div>
        </article>`;
}

async function initAgentDetail(root) {
    const agentId = root.dataset.agentId;
    const [agent, models] = await Promise.all([jsonFetch(`/api/agents/${agentId}`), modelCatalog()]);
    $("#agent-detail-title", root).textContent = agent.name;
    renderAgentProfile(root, agent, models);
    renderAssignmentForm(root, agentId, models);
    bindTabs(root, tab => renderAgentTab(root, agentId, tab));
    await renderAgentTab(root, agentId, "dashboard");
}

function renderAgentProfile(root, agent, models) {
    $("#agent-profile-form", root).innerHTML = `
        <label>Name<input name="name" value="${escapeHtml(agent.name)}"></label>
        <label>Status<select name="status"><option>ACTIVE</option><option>DISABLED</option></select></label>
        <label>Default Model<select name="defaultModel">${modelOptions(models, agent.defaultModel)}</select></label>
        <label>System Prompt<textarea name="systemPrompt">${escapeHtml(agent.systemPrompt || "")}</textarea></label>
        <label>Approved Tools<input name="approvedTools" value="${escapeHtml((agent.approvedTools || []).join(", "))}"></label>
        <label>Shell Allowlist<input name="allowedShellCommands" value="${escapeHtml((agent.allowedShellCommands || []).join(", "))}"></label>
        <label><input type="checkbox" name="directLineEnabled" ${agent.directLineEnabled ? "checked" : ""}> Direct line enabled</label>`;
    $("[name='status']", root).value = agent.status || "ACTIVE";
    $("[data-action='save-agent']", root).addEventListener("click", async () => {
        await jsonFetch(`/api/agents/${agent.id}`, {
            method: "PUT",
            body: JSON.stringify({
                ...agent,
                name: formValue(root, "name"),
                status: formValue(root, "status"),
                defaultModel: formValue(root, "defaultModel"),
                systemPrompt: formValue(root, "systemPrompt"),
                approvedTools: csv(formValue(root, "approvedTools")),
                allowedShellCommands: csv(formValue(root, "allowedShellCommands")),
                directLineEnabled: formValue(root, "directLineEnabled")
            })
        });
        location.reload();
    });
}

function renderAssignmentForm(root, agentId, models) {
    $("#agent-assignment-form", root).innerHTML = `
        <label>Type<select name="assignmentType"><option>TASK_RUN</option><option>WORKFLOW_RUN</option><option>JOB_RUN</option><option>REPORT</option></select></label>
        <label>Priority<input name="priority" type="number" value="0"></label>
        <label>Model Override<select name="modelOverride"><option value="">Default</option>${modelOptions(models, "")}</select></label>
        <label>Input JSON<textarea name="assignmentInput">{}</textarea></label>
        <button type="button" data-action="submit-assignment">Submit Assignment</button>`;
    $("[data-action='submit-assignment']", root).addEventListener("click", async () => {
        await jsonFetch(`/api/agents/${agentId}/assignments`, {
            method: "POST",
            body: JSON.stringify({
                assignmentType: formValue(root, "assignmentType"),
                priority: formValue(root, "priority"),
                modelOverride: formValue(root, "modelOverride") || null,
                input: JSON.parse(formValue(root, "assignmentInput") || "{}")
            })
        });
        await renderAgentTab(root, agentId, "queue");
    });
}

async function renderAgentTab(root, agentId, tab) {
    const panel = $("#agent-tab-panel", root);
    if (tab === "dashboard") {
        const [inbox, queue] = await Promise.all([
            jsonFetch(`/api/agents/${agentId}/inbox`),
            jsonFetch(`/api/agents/${agentId}/assignments`)
        ]);
        panel.innerHTML = `<h2>Dashboard</h2><div class="orch-meta"><span>Inbox ${inbox.length}</span><span>Queue ${queue.length}</span></div>`;
        return;
    }
    const endpoint = tab;
    if (["inbox", "queue"].includes(tab)) {
        const rows = await jsonFetch(`/api/agents/${agentId}/${endpoint}`);
        panel.innerHTML = `<h2>${title(tab)}</h2>${rows.map(row => `<pre class="orch-row">${escapeHtml(JSON.stringify(row, null, 2))}</pre>`).join("") || "No records."}`;
        return;
    }
    if (tab === "jobs") {
        const jobs = await jsonFetch(`/api/jobs?agentId=${encodeURIComponent(agentId)}`);
        panel.innerHTML = `<h2>Jobs</h2>${jobs.map(job => `<div class="orch-row"><a href="/jobs/${escapeHtml(job.id)}">${escapeHtml(job.title)}</a> ${chip(job.status)}</div>`).join("") || "No jobs."}`;
        return;
    }
    if (tab === "workspace") {
        const workspace = await jsonFetch(`/api/agents/${agentId}/workspace`);
        panel.innerHTML = `<h2>Workspace</h2><pre class="orch-row">${escapeHtml(JSON.stringify(workspace, null, 2))}</pre>`;
        return;
    }
    panel.innerHTML = "<h2>History</h2><div id=\"agent-history\">Run history appears as assignments and job events are persisted.</div>";
}

async function initJobs(root) {
    const select = $("#jobs-agent-select", root);
    const agents = await jsonFetch("/api/agents");
    select.innerHTML = agents.map(agent => `<option value="${escapeHtml(agent.id)}">${escapeHtml(agent.name)}</option>`).join("");
    async function load() {
        if (!select.value) return;
        const jobs = await jsonFetch(`/api/jobs?agentId=${encodeURIComponent(select.value)}`);
        $("#job-list", root).innerHTML = jobs.map(job => `<div class="orch-row"><h3><a href="/jobs/${escapeHtml(job.id)}">${escapeHtml(job.title)}</a></h3>${chip(job.status)}<p>${escapeHtml(job.summary || "")}</p></div>`).join("") || "No jobs.";
    }
    select.addEventListener("change", load);
    $("[data-action='reload-jobs']", root).addEventListener("click", load);
    $("[data-action='create-job']", root).addEventListener("click", async () => {
        const job = await jsonFetch("/api/jobs", { method: "POST", body: JSON.stringify({ ownerAgentId: select.value, title: "New Job", summary: "", status: "QUEUED" }) });
        location.href = `/jobs/${job.id}`;
    });
    await load();
}

async function initJobDetail(root) {
    const jobId = root.dataset.jobId;
    const [job, items, runs, events, models] = await Promise.all([
        jsonFetch(`/api/jobs/${jobId}`),
        jsonFetch(`/api/jobs/${jobId}/items`),
        jsonFetch(`/api/jobs/${jobId}/runs`),
        jsonFetch(`/api/jobs/${jobId}/events`),
        modelCatalog()
    ]);
    $("#job-detail-title", root).textContent = job.title;
    $("#job-editor-form", root).innerHTML = `
        <label>Title<input name="title" value="${escapeHtml(job.title)}"></label>
        <label>Summary<textarea name="summary">${escapeHtml(job.summary || "")}</textarea></label>
        <label>Owner Agent<input name="ownerAgentId" value="${escapeHtml(job.ownerAgentId)}"></label>
        <label>Default Model<select name="defaultModel"><option value="">Default</option>${modelOptions(models, job.defaultModel)}</select></label>
        <label>Workspace<input name="workspaceId" value="${escapeHtml(job.workspaceId || "")}"></label>`;
    $("#job-item-editor", root).innerHTML = items.map(itemEditor).join("");
    $("#job-runs", root).innerHTML = runs.map(run => `<pre class="orch-row">${escapeHtml(JSON.stringify(run, null, 2))}</pre>`).join("") || "No runs.";
    $("#job-events", root).innerHTML = events.map(event => `<pre class="orch-row">${escapeHtml(JSON.stringify(event, null, 2))}</pre>`).join("") || "No events.";
    $("[data-action='run-job']", root).addEventListener("click", async () => {
        await jsonFetch(`/api/jobs/${jobId}/runs`, { method: "POST", body: JSON.stringify({ priority: 0 }) });
        location.reload();
    });
    $("[data-action='save-job']", root).addEventListener("click", async () => {
        await jsonFetch("/api/jobs", { method: "POST", body: JSON.stringify({ ...job, title: formValue(root, "title"), summary: formValue(root, "summary"), defaultModel: formValue(root, "defaultModel"), workspaceId: formValue(root, "workspaceId") }) });
        location.reload();
    });
    $("[data-action='add-job-item']", root).addEventListener("click", async () => {
        await jsonFetch(`/api/jobs/${jobId}/items`, { method: "POST", body: JSON.stringify({ itemOrder: $$(".job-item", root).length + 1, itemType: "REPORT", priority: 0, config: { report: true } }) });
        location.reload();
    });
}

function itemEditor(item) {
    return `<pre class="orch-row job-item">${escapeHtml(JSON.stringify(item, null, 2))}</pre>`;
}

function csv(value) {
    return String(value || "").split(",").map(item => item.trim()).filter(Boolean);
}

function title(value) {
    return value.replace(/(^|-)([a-z])/g, (_m, _dash, char) => " " + char.toUpperCase()).trim();
}
