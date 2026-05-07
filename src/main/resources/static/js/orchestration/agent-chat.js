import { consumeSse, renderError } from "./api.js";
import { $, escapeHtml } from "./dom.js";

export function initAgentChat(root = document) {
    const host = $("[data-agent-chat-panel]", root);
    if (!host) return;
    host.innerHTML = `
        <aside class="agent-chat-panel collapsed" id="agent-chat-panel">
            <div class="agent-chat-header">
                <strong>Agent Chat</strong>
                <div>
                    <button type="button" data-agent-chat-toggle>Open</button>
                </div>
            </div>
            <div class="agent-chat-body" id="agent-chat-messages"></div>
            <form class="agent-chat-form" id="agent-chat-form">
                <input id="agent-chat-input" placeholder="Ask the selected agent">
                <button type="submit">Send</button>
            </form>
        </aside>`;

    const panel = $("#agent-chat-panel");
    const toggle = $("[data-agent-chat-toggle]", panel);
    const messages = $("#agent-chat-messages", panel);
    const form = $("#agent-chat-form", panel);
    const input = $("#agent-chat-input", panel);
    const page = $("[data-orchestration-page]");
    const context = host.dataset.pageContext || page?.dataset.orchestrationPage || "orchestration page";

    toggle.addEventListener("click", () => {
        panel.classList.toggle("collapsed");
        toggle.textContent = panel.classList.contains("collapsed") ? "Open" : "Close";
    });

    $("[data-action='open-agent-chat']")?.addEventListener("click", () => {
        panel.classList.remove("collapsed");
        toggle.textContent = "Close";
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        const agentId = page?.dataset.agentId || $("#jobs-agent-select")?.value || $("#settings-default-agent-id")?.value;
        const message = input.value.trim();
        if (!agentId || !message) return;
        append(messages, "user", message);
        input.value = "";
        try {
            const response = await fetch(`/api/agents/${encodeURIComponent(agentId)}/chat/stream`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ message, pageContext: context })
            });
            await consumeSse(response, {
                start: data => append(messages, "system", `Connected to ${data.agentId}`),
                chunk: data => append(messages, "assistant", data.message || data.response || ""),
                done: data => append(messages, "assistant", data.message || data.response || "Done."),
                error: data => append(messages, "system", data.error || "Agent chat failed.")
            });
        } catch (error) {
            renderError(messages, error);
        }
    });
}

function append(root, role, text) {
    root.insertAdjacentHTML("beforeend", `<div class="agent-chat-message"><strong>${escapeHtml(role)}</strong><br>${escapeHtml(text)}</div>`);
    root.scrollTop = root.scrollHeight;
}
