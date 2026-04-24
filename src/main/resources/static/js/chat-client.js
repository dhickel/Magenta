(function() {
    function byId(id) {
        return document.getElementById(id);
    }

    function root() {
        return document.querySelector('[data-chat-root="true"]');
    }

    async function getJson(url, options) {
        const response = await fetch(url, options);
        const text = await response.text();
        let body = null;
        if (text) {
            body = JSON.parse(text);
        }
        if (!response.ok) {
            const message = body && body.message ? body.message : (response.status + ' ' + response.statusText);
            throw new Error(message);
        }
        return body;
    }

    function activeConversationId() {
        return root().getAttribute('data-active-conversation-id');
    }

    function setActiveConversationId(conversationId) {
        root().setAttribute('data-active-conversation-id', conversationId);
        const activeEl = byId('chat-active-session');
        if (activeEl) {
            activeEl.textContent = conversationId;
        }
    }

    function setStatus() {
        setError('');
    }

    function setError(message) {
        const errorEl = byId('chat-error');
        if (errorEl) {
            errorEl.textContent = message || '';
        }
    }

    function selectedModel() {
        const modelSelect = byId('chat-model-select');
        if (!modelSelect || !modelSelect.value) {
            return null;
        }
        return modelSelect.value;
    }

    function syncModelSelection(model) {
        if (!model) {
            return;
        }
        const modelSelect = byId('chat-model-select');
        if (!modelSelect) {
            return;
        }
        const exists = Array.from(modelSelect.options).some(function(option) {
            return option.value === model;
        });
        if (!exists) {
            const option = document.createElement('option');
            option.value = model;
            option.textContent = model;
            modelSelect.appendChild(option);
        }
        modelSelect.value = model;
    }

    function renderHistory(history) {
        const historyEl = byId('chat-history');
        if (!history || history.length === 0) {
            historyEl.innerHTML = '<p>No messages in this session yet.</p>';
            return;
        }

        historyEl.innerHTML = history.map(function(message) {
            const rawRole = message.role || 'assistant';
            const role = escapeHtml(rawRole);
            const roleClass = rawRole.toLowerCase() === 'user'
                ? 'chat-message-user'
                : 'chat-message-assistant';
            const text = message.renderedHtml
                ? String(message.renderedHtml)
                : formatPlainText(message.text || '');
            const thinking = message.thinkingHtml
                ? '<details class="chat-thinking">'
                    + '<summary class="chat-thinking-toggle">'
                    + '<span class="chat-thinking-show">Show thinking</span>'
                    + '<span class="chat-thinking-hide">Hide thinking</span>'
                    + '</summary>'
                    + '<div class="chat-thinking-body">' + String(message.thinkingHtml) + '</div>'
                    + '</details>'
                : '';
            return '<div class="chat-message ' + roleClass + '">'
                + '<div class="chat-message-role">' + role + '</div>'
                + thinking
                + '<div class="chat-message-body">' + text + '</div>'
                + '</div>';
        }).join('');
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function appendPendingUserMessage(message) {
        const historyEl = byId('chat-history');
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }

        const escapedText = formatPlainText(message);
        historyEl.insertAdjacentHTML(
            'beforeend',
            '<div class="chat-message chat-message-user" data-pending-user="true">'
                + '<div class="chat-message-role">user</div>'
                + '<div class="chat-message-body">' + escapedText + '</div>'
                + '</div>'
        );
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function clearPendingUserMessage() {
        const pending = byId('chat-history').querySelector('[data-pending-user="true"]');
        if (pending) {
            pending.remove();
        }
    }

    function appendStreamingAssistantMessage() {
        const historyEl = byId('chat-history');
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }

        const wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message-assistant';
        wrapper.setAttribute('data-streaming-assistant', 'true');
        wrapper.innerHTML = '<div class="chat-message-role">assistant</div>'
            + '<div class="chat-message-body">...</div>';
        historyEl.appendChild(wrapper);
        historyEl.scrollTop = historyEl.scrollHeight;
        return wrapper;
    }

    function updateStreamingAssistantMessage(messageEl, eventData) {
        if (!messageEl) {
            return;
        }
        const bodyEl = messageEl.querySelector('.chat-message-body');
        if (bodyEl) {
            bodyEl.innerHTML = eventData.renderedHtml
                ? String(eventData.renderedHtml)
                : formatPlainText(eventData.text || '');
        }

        let thinkingEl = messageEl.querySelector('.chat-thinking');
        if (eventData.thinkingHtml) {
            if (!thinkingEl) {
                thinkingEl = document.createElement('details');
                thinkingEl.className = 'chat-thinking';
                thinkingEl.innerHTML = '<summary class="chat-thinking-toggle">'
                    + '<span class="chat-thinking-show">Show thinking</span>'
                    + '<span class="chat-thinking-hide">Hide thinking</span>'
                    + '</summary>'
                    + '<div class="chat-thinking-body"></div>';
                const roleEl = messageEl.querySelector('.chat-message-role');
                roleEl.insertAdjacentElement('afterend', thinkingEl);
            }
            thinkingEl.querySelector('.chat-thinking-body').innerHTML = String(eventData.thinkingHtml);
        } else if (thinkingEl) {
            thinkingEl.remove();
        }

        const historyEl = byId('chat-history');
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function removeStreamingAssistantMessage(messageEl) {
        if (messageEl) {
            messageEl.remove();
        }
    }

    function renderSessions(conversationIds) {
        const sessionsEl = byId('chat-session-list');
        const ids = conversationIds || [];
        const activeId = activeConversationId();

        if (ids.length === 0) {
            sessionsEl.innerHTML = '<li class="chat-session-empty">No persisted sessions yet.</li>';
            return;
        }

        sessionsEl.innerHTML = ids.map(function(id) {
            const escaped = escapeHtml(id);
            const activeClass = id === activeId ? ' active' : '';
            return '<li><a href="#" class="chat-session-entry' + activeClass + '" data-switch-id="' + escaped + '"><code>' + escaped + '</code></a></li>';
        }).join('');
    }

    async function loadSessions() {
        const data = await getJson('/api/chat/sessions');
        renderSessions(data.conversationIds);
    }

    async function loadHistory(conversationId) {
        const data = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/history');
        renderHistory(data.messages);
        syncModelSelection(data.model);
    }

    async function sendMessage(message) {
        const payload = {
            conversationId: activeConversationId(),
            message: message,
            model: selectedModel()
        };

        appendPendingUserMessage(message);
        const assistantEl = appendStreamingAssistantMessage();
        setStatus();

        try {
            const response = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: {
                    'Accept': 'text/event-stream',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw await responseError(response);
            }

            let completedConversationId = activeConversationId();
            await readSse(response, async function(event) {
                const data = event.data || {};
                if (event.name === 'start') {
                    setActiveConversationId(data.conversationId);
                    syncModelSelection(data.model);
                    completedConversationId = data.conversationId;
                    return;
                }
                if (event.name === 'chunk') {
                    updateStreamingAssistantMessage(assistantEl, data);
                    return;
                }
                if (event.name === 'done') {
                    updateStreamingAssistantMessage(assistantEl, data);
                    completedConversationId = data.conversationId;
                    return;
                }
                if (event.name === 'error') {
                    throw new Error(data.message || 'stream failed');
                }
            });

            await loadHistory(completedConversationId);
            await loadSessions();
            setStatus();
        } catch (error) {
            clearPendingUserMessage();
            removeStreamingAssistantMessage(assistantEl);
            try {
                await loadHistory(activeConversationId());
                await loadSessions();
            } catch (reloadError) {
                // Keep the original streaming error visible.
            }
            throw error;
        }
    }

    async function sendCommand(command) {
        const payload = {
            conversationId: activeConversationId(),
            command: command
        };

        const data = await getJson('/api/chat/commands', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        setActiveConversationId(data.conversationId);
        syncModelSelection(data.model);
        renderHistory(data.history || []);
        renderSessions(data.conversationIds || []);
        setStatus();
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatPlainText(value) {
        const text = String(value || '');
        return escapeHtml(text).replace(/\n/g, '<br>');
    }

    async function responseError(response) {
        const text = await response.text();
        if (!text) {
            return new Error(response.status + ' ' + response.statusText);
        }
        try {
            const body = JSON.parse(text);
            return new Error(body && body.message ? body.message : text);
        } catch (error) {
            return new Error(text);
        }
    }

    async function readSse(response, onEvent) {
        if (!response.body) {
            throw new Error('streaming responses are not supported by this browser');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const result = await reader.read();
            if (result.done) {
                break;
            }
            buffer += decoder.decode(result.value, { stream: true });
            buffer = buffer.replace(/\r\n/g, '\n');
            buffer = await processSseBuffer(buffer, onEvent);
        }

        buffer += decoder.decode();
        buffer = buffer.replace(/\r\n/g, '\n');
        await processSseBuffer(buffer + '\n\n', onEvent);
    }

    async function processSseBuffer(buffer, onEvent) {
        let boundary = buffer.indexOf('\n\n');
        while (boundary !== -1) {
            const block = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            const event = parseSseBlock(block);
            if (event) {
                await onEvent(event);
            }
            boundary = buffer.indexOf('\n\n');
        }
        return buffer;
    }

    function parseSseBlock(block) {
        const normalized = block.replace(/\r/g, '');
        const lines = normalized.split('\n');
        let name = 'message';
        const dataLines = [];

        lines.forEach(function(line) {
            if (line.startsWith('event:')) {
                name = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
                dataLines.push(line.slice(5).trimStart());
            }
        });

        if (dataLines.length === 0) {
            return null;
        }
        return {
            name: name,
            data: JSON.parse(dataLines.join('\n'))
        };
    }

    async function init() {
        if (!root()) {
            return;
        }

        const form = byId('chat-form');
        const input = byId('chat-input');

        input.addEventListener('keydown', function(event) {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                form.requestSubmit();
            }
        });

        byId('chat-form').addEventListener('submit', async function(event) {
            event.preventDefault();
            const text = input.value.trim();
            if (!text) {
                return;
            }

            input.value = '';
            try {
                if (text.startsWith('/')) {
                    await sendCommand(text);
                } else {
                    await sendMessage(text);
                }
            } catch (error) {
                input.value = text;
                setError(error.message);
            }
        });

        byId('chat-session-list').addEventListener('click', async function(event) {
            const target = event.target.closest('[data-switch-id]');
            if (!target) {
                return;
            }
            event.preventDefault();

            const conversationId = target.getAttribute('data-switch-id');
            try {
                setActiveConversationId(conversationId);
                await loadHistory(conversationId);
                await loadSessions();
                setStatus();
            } catch (error) {
                setError(error.message);
            }
        });

        try {
            await loadHistory(activeConversationId());
            await loadSessions();
        } catch (error) {
            setError(error.message);
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        init();
    });
})();
