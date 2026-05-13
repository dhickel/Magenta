import { jsonFetch, renderError } from "./api.js";
import { $, $$, chip, escapeHtml } from "./dom.js";

document.addEventListener("DOMContentLoaded", () => {
    const page = $("[data-orchestration-page='inbox']");
    if (!page) return;
    initInbox(page);
});

async function initInbox(root) {
    // Load agents for the selector
    await loadAgentSelector(root);

    // Load user inbox
    await loadUserInbox(root);

    // Handle agent selector change
    $("#inbox-agent-select", root).addEventListener("change", () => {
        loadAgentInbox(root);
    });
}

async function loadAgentSelector(root) {
    const select = $("#inbox-agent-select", root);
    if (!select) return;
    try {
        const agents = await jsonFetch("/api/agents");
        select.innerHTML = `<option value="">-- select agent --</option>` +
            agents.map(agent => `<option value="${escapeHtml(agent.id)}">${escapeHtml(agent.name)}</option>`).join("");
    } catch (_ignored) {
        select.innerHTML = `<option value="">-- unavailable --</option>`;
    }
}

async function loadUserInbox(root) {
    const panel = $("#user-inbox-messages", root);
    if (!panel) return;
    try {
        const messages = await jsonFetch("/api/users/inbox");
        panel.innerHTML = messages.length
            ? messages.map(msg => inboxMessageHtml(msg, "user")).join("")
            : `<div class="inbox-message inbox-empty">No user messages.</div>`;
        bindInboxActions(root, "user");
    } catch (error) {
        renderError(panel, error);
    }
}

async function loadAgentInbox(root) {
    const agentId = $("#inbox-agent-select", root)?.value;
    const panel = $("#agent-inbox-messages", root);
    if (!panel || !agentId) return;
    try {
        const messages = await jsonFetch(`/api/agents/${encodeURIComponent(agentId)}/inbox`);
        panel.innerHTML = messages.length
            ? messages.map(msg => inboxMessageHtml(msg, "agent")).join("")
            : `<div class="inbox-message inbox-empty">No agent messages.</div>`;
        bindInboxActions(root, "agent");
    } catch (error) {
        renderError(panel, error);
    }
}

function inboxMessageHtml(msg, source) {
    const responded = msg.responded || msg.respondedAt || msg.handled ? "responded" : "";
    const canApprove = source === "user" && !responded;
    return `
        <div class="inbox-message ${responded}" data-message-id="${escapeHtml(msg.id)}" data-source="${source}">
            <div class="inbox-message-header">
                <span class="inbox-message-type">${badge(msg.messageType || "message")}</span>
                <span class="inbox-message-time">${escapeHtml(msg.createdAt || "")}</span>
            </div>
            <div class="inbox-message-body">${escapeHtml(msg.body || "")}</div>
            <div class="inbox-message-meta">
                <span>From: ${escapeHtml(msg.fromAgentId || msg.fromId || "system")}</span>
                ${workflowRunId(msg) ? `<span>Run: ${escapeHtml(workflowRunId(msg))}</span>` : ""}
            </div>
            <div class="inbox-message-actions">
                ${canApprove ? `
                    <button type="button" class="btn btn-primary orch-primary" data-action="approve" data-message-id="${escapeHtml(msg.id)}">Approve</button>
                    <button type="button" class="btn btn-primary" data-action="reject" data-message-id="${escapeHtml(msg.id)}">Reject</button>
                    <input class="inbox-comment" data-message-id="${escapeHtml(msg.id)}" placeholder="comment (optional)">`
                    : source === "agent" && !msg.handled ? `
                        <button type="button" class="btn btn-primary" data-action="mark-read" data-message-id="${escapeHtml(msg.id)}">Read</button>
                        <button type="button" class="btn btn-primary orch-primary" data-action="mark-handled" data-message-id="${escapeHtml(msg.id)}">Handled</button>`
                    : `<span class="orch-chip">${source === "agent" ? "Handled" : "Responded"}</span>`
                }
            </div>
        </div>`;
}

function workflowRunId(msg) {
    if (msg.workflowRunId) return msg.workflowRunId;
    if (!msg.metadataJson) return "";
    try {
        return JSON.parse(msg.metadataJson).workflowRunId || "";
    } catch (_ignored) {
        return "";
    }
}

function badge(type) {
    const normalized = String(type || "message").toLowerCase();
    let cls = "orch-status-chip";
    if (normalized.includes("approval")) cls += " active";
    if (normalized.includes("error")) cls += " failed";
    return `<span class="${cls}">${escapeHtml(type)}</span>`;
}

function bindInboxActions(root, source) {
    // Approve
    $$(`.inbox-message[data-source="${source}"] [data-action="approve"]`, root).forEach(btn => {
        btn.addEventListener("click", async () => {
            const msgId = btn.dataset.messageId;
            const comment = root.querySelector(`.inbox-comment[data-message-id="${msgId}"]`)?.value || "";
            const endpoint = `/api/users/inbox/${encodeURIComponent(msgId)}/respond`;
            try {
                const result = await jsonFetch(endpoint, {
                    method: "POST",
                    body: JSON.stringify({ approved: true, comment })
                });
                // Refresh run state
                await refreshRunState(root, result);
                // Reload inbox
                if (source === "user") await loadUserInbox(root);
                else await loadAgentInbox(root);
            } catch (error) {
                alert(`Failed to respond: ${error.message}`);
            }
        });
    });

    // Reject
    $$(`.inbox-message[data-source="${source}"] [data-action="reject"]`, root).forEach(btn => {
        btn.addEventListener("click", async () => {
            const msgId = btn.dataset.messageId;
            const comment = root.querySelector(`.inbox-comment[data-message-id="${msgId}"]`)?.value || "";
            const endpoint = source === "user"
                ? `/api/users/inbox/${encodeURIComponent(msgId)}/respond`
                : "";
            if (!endpoint) return;
            try {
                const result = await jsonFetch(endpoint, {
                    method: "POST",
                    body: JSON.stringify({ approved: false, comment })
                });
                await refreshRunState(root, result);
                if (source === "user") await loadUserInbox(root);
                else await loadAgentInbox(root);
            } catch (error) {
                alert(`Failed to respond: ${error.message}`);
            }
        });
    });

    $$(`.inbox-message[data-source="agent"] [data-action="mark-read"]`, root).forEach(btn => {
        btn.addEventListener("click", async () => {
            const agentId = $("#inbox-agent-select", root)?.value;
            if (!agentId) return;
            await jsonFetch(`/api/agents/${encodeURIComponent(agentId)}/inbox/${encodeURIComponent(btn.dataset.messageId)}/read`, { method: "POST" });
            await loadAgentInbox(root);
        });
    });

    $$(`.inbox-message[data-source="agent"] [data-action="mark-handled"]`, root).forEach(btn => {
        btn.addEventListener("click", async () => {
            const agentId = $("#inbox-agent-select", root)?.value;
            if (!agentId) return;
            await jsonFetch(`/api/agents/${encodeURIComponent(agentId)}/inbox/${encodeURIComponent(btn.dataset.messageId)}/handled`, { method: "POST" });
            await loadAgentInbox(root);
        });
    });
}

async function refreshRunState(root, result) {
    const panel = $("#inbox-run-state", root);
    if (!panel || !result) return;
    const runId = result.workflowRunId || result.runId;
    panel.innerHTML = `
        <div class="orch-row">
            <strong>Response recorded</strong>
            <p>Approved: ${result.approved !== undefined ? result.approved : "N/A"}</p>
            ${runId ? `<p>Run: <a href="/workflows">${escapeHtml(runId)}</a></p>` : ""}
        </div>`;
    // Attempt to refresh run from API
    if (runId) {
        try {
            const run = await jsonFetch(`/api/workflow-runs/${encodeURIComponent(runId)}`);
            panel.innerHTML += `<pre class="orch-row">${escapeHtml(JSON.stringify(run, null, 2))}</pre>`;
        } catch (_ignored) {}
    }
}
