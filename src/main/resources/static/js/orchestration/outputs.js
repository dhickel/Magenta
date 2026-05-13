import { jsonFetch, renderError } from "./api.js";
import { $, escapeHtml } from "./dom.js";

document.addEventListener("DOMContentLoaded", () => {
    const page = $("[data-orchestration-page='outputs']");
    if (!page) return;
    initOutputs(page);
});

async function initOutputs(root) {
    // Load filter options
    await Promise.all([
        loadAgentOptions(root),
        loadJobOptions(root),
        loadProjectOptions(root)
    ]);

    $("[data-action='browse-outputs']", root).addEventListener("click", () => browseOutputs(root));
}

async function loadAgentOptions(root) {
    const select = $("#outputs-agent-select", root);
    if (!select) return;
    try {
        const agents = await jsonFetch("/api/agents");
        select.innerHTML = `<option value="">All agents</option>` +
            agents.map(a => `<option value="${escapeHtml(a.id)}">${escapeHtml(a.name)}</option>`).join("");
    } catch (_ignored) {
        select.innerHTML = `<option value="">-- unavailable --</option>`;
    }
}

async function loadJobOptions(root) {
    const select = $("#outputs-job-select", root);
    if (!select) return;
    try {
        const jobs = await jsonFetch("/api/jobs");
        select.innerHTML = `<option value="">All jobs</option>` +
            jobs.map(j => `<option value="${escapeHtml(j.id)}">${escapeHtml(j.title)}</option>`).join("");
    } catch (_ignored) {
        select.innerHTML = `<option value="">-- unavailable --</option>`;
    }
}

async function loadProjectOptions(root) {
    const select = $("#outputs-project-select", root);
    if (!select) return;
    try {
        const projects = await jsonFetch("/api/projects");
        select.innerHTML = `<option value="">All projects</option>` +
            projects.map(p => `<option value="${escapeHtml(p.id)}">${escapeHtml(p.name)}</option>`).join("");
    } catch (_ignored) {
        select.innerHTML = `<option value="">-- unavailable --</option>`;
    }
}

async function browseOutputs(root) {
    const panel = $("#outputs-list", root);
    if (!panel) return;

    const agentId = $("#outputs-agent-select", root)?.value;
    const jobId = $("#outputs-job-select", root)?.value;
    const projectId = $("#outputs-project-select", root)?.value;
    const runId = $("#outputs-run-id", root)?.value;
    const artifactType = $("#outputs-type-select", root)?.value || "all";

    const params = new URLSearchParams();
    if (agentId) params.set("agentId", agentId);
    if (jobId) params.set("jobId", jobId);
    if (projectId) params.set("projectId", projectId);
    if (runId) params.set("runId", runId);
    if (artifactType && artifactType !== "all") params.set("type", artifactType);

    panel.innerHTML = `<div class="orch-row">Loading outputs...</div>`;

    try {
        params.set("limit", "100");
        const results = await jsonFetch(`/api/outputs?${params.toString()}`);

        renderOutputs(panel, results);
    } catch (error) {
        renderError(panel, error);
    }
}

function renderOutputs(panel, results) {
    if (!results.length) {
        panel.innerHTML = `<div class="orch-row">No outputs found. Select filters and click Browse.</div>`;
        return;
    }

    panel.innerHTML = results.map(item => {
        const source = item.artifactType || item._source || "unknown";
        const badge = `<span class="orch-chip">${escapeHtml(source)}</span>`;
        const title = item.outputName || item.title || item.name || item.id || "Output";
        const filePath = item.filePath || item.outputDirectory || item.outputDirectoryPath || item.path || "";

        return `
            <div class="orch-card output-card">
                <div class="output-card-header">
                    <strong>${escapeHtml(title)}</strong>
                    ${badge}
                </div>
                <div class="orch-meta">
                    <span>ID: ${escapeHtml(item.id || "N/A")}</span>
                    ${item.planId ? `<span>Plan: ${escapeHtml(item.planId)}</span>` : ""}
                    ${item.runId ? `<span>Run: ${escapeHtml(item.runId)}</span>` : ""}
                    ${item.status ? `<span>Status: ${escapeHtml(item.status)}</span>` : ""}
                </div>
                ${filePath ? `<div class="output-path">${escapeHtml(filePath)}</div>` : ""}
                ${item.outputValues ? `<pre class="output-json">${escapeHtml(JSON.stringify(item.outputValues, null, 2))}</pre>` : ""}
                ${item.executionEvidence ? `<pre class="output-json">${escapeHtml(JSON.stringify(item.executionEvidence, null, 2))}</pre>` : ""}
            </div>`;
    }).join("");
}
