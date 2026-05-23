// Compact Avatar chat. JavaScript is limited to POSTing and consuming the SSE
// chat stream; dashboard widgets and editing stay HTMX/server-rendered.

let conversationId = null;

document.addEventListener("DOMContentLoaded", initAvatarChat);
document.addEventListener("htmx:afterSettle", initAvatarChat);

function initAvatarChat() {
    const root = document.querySelector("[data-avatar-chat]");
    if (!root || root.dataset.initialized === "true") return;
    root.dataset.initialized = "true";

    const form = root.querySelector("#avatar-chat-form");
    const input = root.querySelector("#avatar-chat-input");
    const messages = root.querySelector("#avatar-chat-messages");
    const session = root.querySelector("#avatar-chat-session");
    const status = root.querySelector("#avatar-chat-status");
    const submit = form?.querySelector("button[type='submit']");
    const model = root.dataset.defaultModel || null;
    const surface = (root.dataset.chatSurface || "avatar").toUpperCase();

    form?.addEventListener("submit", async event => {
        event.preventDefault();
        const message = input.value.trim();
        if (!message) return;
        clearEmpty(messages);
        append(messages, "user", message);
        input.value = "";
        input.disabled = true;
        if (submit) submit.disabled = true;
        setStatus(status, "Sending");
        let visibleResponse = false;
        const waitingNotice = window.setTimeout(() => {
            if (!visibleResponse) {
                visibleResponse = append(messages, "system", "Avatar chat is still waiting for a response.");
            }
        }, 12000);
        try {
            const response = await fetch("/api/chat/stream", {
                method: "POST",
                headers: {
                    "Accept": "text/event-stream",
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ conversationId, message, model, surface })
            });
            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || `${response.status} ${response.statusText}`);
            }
            await consumeSse(response, {
                start: data => {
                    conversationId = data.conversationId || conversationId;
                    if (session) session.textContent = conversationId ? conversationId.slice(0, 8) : "New chat";
                    setStatus(status, "Streaming");
                },
                chunk: data => {
                    visibleResponse = append(messages, "assistant", eventText(data)) || visibleResponse;
                },
                system: data => {
                    visibleResponse = append(messages, "system", eventText(data)) || visibleResponse;
                },
                interrupt: data => append(messages, "user", eventText(data)),
                error: data => {
                    setStatus(status, "Error");
                    visibleResponse = append(messages, "system", eventText(data) || "Avatar chat failed.") || visibleResponse;
                },
                done: data => {
                    conversationId = data.conversationId || conversationId;
                    setStatus(status, "Ready");
                    visibleResponse = append(messages, "assistant", eventText(data)) || visibleResponse;
                }
            });
            if (!visibleResponse) {
                append(messages, "system", "Avatar chat finished without returning a visible response.");
            }
        } catch (error) {
            setStatus(status, "Error");
            append(messages, "system", error.message || "Avatar chat request failed.");
        } finally {
            window.clearTimeout(waitingNotice);
            input.disabled = false;
            if (submit) submit.disabled = false;
            input.focus();
        }
    });
}

async function consumeSse(response, handlers) {
    if (!response.body) {
        return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\n\n+/);
        buffer = blocks.pop() || "";
        for (const block of blocks) {
            dispatchEventBlock(block, handlers);
        }
    }
    if (buffer.trim()) {
        dispatchEventBlock(buffer, handlers);
    }
}

function dispatchEventBlock(block, handlers) {
    const parsed = { event: "message", data: "" };
    for (const line of block.split(/\n/)) {
        if (line.startsWith("event:")) {
            parsed.event = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
            parsed.data += line.slice(5).trim();
        }
    }
    if (!parsed.data) return;
    let data = parsed.data;
    try {
        data = JSON.parse(parsed.data);
    } catch (_ignored) {
    }
    const handler = handlers[parsed.event] || handlers.message;
    if (handler) handler(data);
}

function append(root, role, text) {
    if (!root || !text) return false;
    const item = document.createElement("article");
    item.className = `avatar-chat-message avatar-chat-message-${role}`;
    const label = document.createElement("strong");
    label.textContent = role;
    const body = document.createElement("p");
    body.textContent = text;
    item.append(label, body);
    root.append(item);
    root.scrollTop = root.scrollHeight;
    return true;
}

function clearEmpty(root) {
    root?.querySelector(".avatar-chat-empty")?.remove();
}

function eventText(data) {
    if (!data) return "";
    return data.text || data.message || data.response || data.error || "";
}

function setStatus(root, text) {
    if (root) root.textContent = text;
}
