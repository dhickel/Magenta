import { jsonFetch, renderError } from "./api.js";
import { $, formValue, modelOptions } from "./dom.js";

document.addEventListener("DOMContentLoaded", () => {
    const page = $("[data-orchestration-page]");
    if (!page) return;
    const name = page.dataset.orchestrationPage;
    if (name === "dashboard") initDashboardTicker(page);
    if (name === "settings") initSettings(page);
});

// ── Dashboard freshness ticker (plan explicitly allows lightweight JS ticker) ──

function initDashboardTicker(root) {
    let tickerInterval = setInterval(() => {
        const el = $("#stat-freshness");
        if (!el) {
            clearInterval(tickerInterval);
            return;
        }
        const freshness = el.dataset.freshness;
        if (freshness) {
            el.textContent = formatSince(freshness);
        }
    }, 30000);
}

function formatSince(iso) {
    if (!iso) return "—";
    const diff = Date.now() - new Date(iso).getTime();
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) return `${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
}

// ── Settings (model dropdowns populated via JS, save via fetch) ──

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

        // Populate model dropdowns (these come from API, not server-rendered)
        ["defaultModel", "planningModel", "summaryModel", "compactionModel"].forEach(key => {
            const field = $(`[name="${key}"]`, root);
            if (field) field.innerHTML = modelOptions(models, settings[key]);
        });

        // Model chip list
        const modelList = $("#settings-model-list", root);
        if (modelList) {
            modelList.innerHTML = models.map(model => `<span class="orch-chip">${escapeHtml(model)}</span>`).join("");
        }

        // Save is handled via fetch
        const saveBtn = $("[data-action='save-settings']", root);
        if (saveBtn) {
            saveBtn.addEventListener("click", async (e) => {
                e.preventDefault();
                const body = {
                    defaultAgentId: formValue(root, "defaultAgentId"),
                    defaultAgentName: formValue(root, "defaultAgentName"),
                    defaultModel: formValue(root, "defaultModel"),
                    planningModel: formValue(root, "planningModel"),
                    summaryModel: formValue(root, "summaryModel"),
                    compactionModel: formValue(root, "compactionModel"),
                    contextBufferPercent: formValue(root, "contextBufferPercent")
                };
                try {
                    await jsonFetch("/api/settings/runtime", { method: "PUT", body: JSON.stringify(body) });
                    status.textContent = "Saved.";
                } catch (error) {
                    status.textContent = `Error: ${error.message}`;
                }
            });
        }

        // Wire HTMX after-save event to reload model chips
        document.body.addEventListener("htmx:afterRequest", (evt) => {
            if (evt.detail.pathInfo.requestPath === "/api/settings/runtime" && evt.detail.successful) {
                status.textContent = "Saved.";
            }
        });
    } catch (error) {
        renderError(status, error);
    }
}

// ── Helpers ──

function escapeHtml(value) {
    return String(value || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
