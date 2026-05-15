(function() {
    let requestInFlight = false;
    let activeTurnId = null;
    let activeInterruptToken = null;
    const queuedMessages = [];
    let titlePollTimer = null;
    let editingSessionId = null;
    let latestSessions = [];
    const selectedSessionIds = new Set();
    let lastPlanState = null;
    const savedTaskByConversation = new Map();
    let showAgentSendChooser = false;
    let sendAgentsLoading = false;
    let assignableAgents = [];

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
        const value = root().getAttribute('data-active-conversation-id');
        return value ? value : null;
    }

    function setActiveConversationId(conversationId, title) {
        const displayValue = conversationId || '';
        root().setAttribute('data-active-conversation-id', displayValue);
        clearAgentSendChooser();
        const activeEl = byId('chat-active-session');
        if (activeEl) {
            activeEl.textContent = title || conversationId || 'New chat';
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

    function updateContextUsage(usage) {
        const textEl = byId('chat-token-usage-text');
        const fillEl = byId('chat-token-usage-fill');
        if (!textEl || !fillEl) {
            return;
        }

        if (!usage) {
            textEl.textContent = '0 / 0 (0%)';
            fillEl.style.width = '0%';
            return;
        }

        const used = Number(usage.usedTokens || 0);
        const max = Number(usage.maxTokens || 0);
        const percent = max > 0 ? Math.min(100, Math.max(0, Number(usage.percentUsed || 0))) : 0;
        textEl.textContent = used.toLocaleString() + ' / ' + max.toLocaleString() + ' (' + Math.round(percent) + '%)';
        fillEl.style.width = percent.toFixed(1) + '%';
    }

    function updateContextUsageIfPresent(usage) {
        if (usage) {
            updateContextUsage(usage);
        }
    }

    function updatePlanStatus(planState) {
        lastPlanState = planState || null;
        const statusEl = byId('chat-plan-status');
        const titleEl = byId('chat-plan-title');
        const hintEl = byId('chat-plan-hint');
        const evidenceEl = byId('chat-plan-evidence');
        if (!statusEl || !titleEl || !hintEl || !evidenceEl) {
            return;
        }
        const mode = planState && planState.mode ? String(planState.mode) : 'NORMAL';
        const status = planState && planState.status ? String(planState.status) : '';
        const conversationId = activeConversationId();
        if (status !== 'APPROVED' && status !== 'SAVED_TASK') {
            clearAgentSendChooser();
            if (conversationId) {
                savedTaskByConversation.delete(conversationId);
            }
        }
        if (mode === 'NORMAL' && !(planState && planState.title)) {
            statusEl.classList.remove('active');
            titleEl.textContent = '';
            hintEl.textContent = '';
            evidenceEl.innerHTML = '';
            renderPlanningPanel(planState);
            syncPlanningApprovalPreview(planState);
            return;
        }
        const title = planState && planState.title ? String(planState.title) : (planState && planState.goal ? String(planState.goal) : 'Draft plan');
        const statusLabel = status ? status.toLowerCase() : 'active';
        titleEl.textContent = mode === 'PLAN' ? 'Plan mode: ' + title : 'Plan: ' + title + ' (' + statusLabel + ')';
        hintEl.textContent = mode === 'PLAN'
            ? 'Use the planning panel'
            : (statusLabel === 'needs_review' ? 'Review execution evidence before trusting completion' : 'Saved execution plan');
        const evidence = Array.isArray(planState && planState.executionEvidence) ? planState.executionEvidence : [];
        const validationFeedback = Array.isArray(planState && planState.validationFeedback) ? planState.validationFeedback : [];
        const evidenceItems = evidence.concat(validationFeedback);
        evidenceEl.innerHTML = evidenceItems.length
            ? '<ul>' + evidenceItems.map(function(item) {
                return '<li>' + escapeHtml(item) + '</li>';
            }).join('') + '</ul>'
            : '';
        statusEl.classList.add('active');
        renderPlanningPanel(planState);
        syncPlanningApprovalPreview(planState);
    }

    function syncPlanningApprovalPreview(planState) {
        const historyEl = byId('chat-history');
        if (!historyEl) {
            return;
        }
        historyEl.querySelectorAll('[data-planning-approval-preview="true"]').forEach(function(el) {
            el.remove();
        });
        const status = planState && planState.status ? String(planState.status) : '';
        const rendered = planState && planState.approvalHtml ? String(planState.approvalHtml) : '';
        if (status !== 'READY_FOR_APPROVAL' || !rendered) {
            return;
        }
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }
        const wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message-assistant';
        wrapper.setAttribute('data-planning-approval-preview', 'true');
        wrapper.innerHTML = '<div class="chat-message-role">assistant</div>'
            + '<div class="chat-message-body">'
            + '<div class="planning-preview-document">' + rendered + '</div>'
            + '</div>';
        historyEl.appendChild(wrapper);
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function renderPlanningPanel(planState) {
        const panel = byId('chat-planning-panel');
        if (!panel) {
            return;
        }
        const mode = planState && planState.mode ? String(planState.mode) : 'NORMAL';
        const status = planState && planState.status ? String(planState.status) : '';
        if (mode !== 'PLAN' && status !== 'APPROVED' && status !== 'SAVED_TASK') {
            panel.classList.remove('active');
            panel.innerHTML = '';
            return;
        }

        const question = planState && planState.promptQuestion ? String(planState.promptQuestion) : '';
        let body = '';
        const questionIndex = Number(planState && planState.promptQuestionIndex ? planState.promptQuestionIndex : 0);
        const questionCount = Number(planState && planState.promptQuestionCount ? planState.promptQuestionCount : 0);
        if (question) {
            const progress = questionCount > 0 ? 'Question ' + questionIndex + '/' + questionCount : 'Question';
            body = '<form data-planning-answer-form="questions">'
                + '<div class="planning-panel-progress">' + escapeHtml(progress) + '</div>'
                + '<div class="planning-panel-title">' + escapeHtml(question) + '</div>'
                + '<input type="hidden" name="questionIndex" value="' + escapeHtml(questionIndex) + '">'
                + '<input type="hidden" name="question" value="' + escapeHtml(question) + '">'
                + '<textarea name="answer" rows="3" placeholder="Answer"></textarea>'
                + planningActions('<button type="submit">Submit answer</button>')
                + '</form>';
        } else if (status === 'READY_FOR_APPROVAL') {
            body = '<div class="planning-panel-body">'
                + '<div class="planning-panel-title">Plan ready for approval</div>'
                + planningActions('<button type="button" data-plan-action="approve">Approve plan</button><button type="button" data-plan-action="continue">Continue planning</button>')
                + '</div>';
        } else if (status === 'APPROVED' || status === 'SAVED_TASK') {
            const saveButtonLabel = status === 'SAVED_TASK' ? 'Save another copy' : 'Save to plans';
            body = '<div class="planning-panel-body">'
                + '<div class="planning-panel-title">' + (status === 'SAVED_TASK' ? 'Plan saved' : 'Approved plan') + '</div>'
                + planningActions(
                    '<button type="button" data-plan-action="execute">Execute now</button>'
                    + '<button type="button" data-plan-action="save-task">' + saveButtonLabel + '</button>'
                    + '<button type="button" data-plan-action="send-agent">Send to agent</button>'
                )
                + renderSendAgentChooser()
                + '</div>';
        } else {
            body = '<div class="planning-panel-body">'
                + '<div class="planning-panel-title">Planning active</div>'
                + planningActions('')
                + '</div>';
        }
        panel.innerHTML = body;
        panel.classList.add('active');
    }

    function clearPlanningPanel() {
        const panel = byId('chat-planning-panel');
        if (!panel) {
            return;
        }
        panel.classList.remove('active');
        panel.innerHTML = '';
    }

    function planningActions(primaryButtons) {
        return '<div class="planning-actions">'
            + primaryButtons
            + '<button type="button" data-plan-action="cancel">Cancel planning</button>'
            + '</div>';
    }

    function renderSendAgentChooser() {
        if (!showAgentSendChooser) {
            return '';
        }
        if (sendAgentsLoading) {
            return '<div class="planning-agent-send"><div class="planning-panel-progress">Loading agents...</div></div>';
        }
        if (!assignableAgents.length) {
            return '<div class="planning-agent-send">'
                + '<div class="planning-panel-progress">No active agents available.</div>'
                + '<button type="button" data-plan-action="send-agent-cancel">Close</button>'
                + '</div>';
        }
        const options = assignableAgents.map(function(agent, index) {
            const selected = index === 0 ? ' selected' : '';
            const name = agent && agent.name ? String(agent.name) : String(agent && agent.id ? agent.id : 'agent');
            const id = agent && agent.id ? String(agent.id) : '';
            return '<option value="' + escapeHtml(id) + '"' + selected + '>' + escapeHtml(name) + '</option>';
        }).join('');
        return '<div class="planning-agent-send">'
            + '<label for="chat-plan-agent-select">Agent</label>'
            + '<select id="chat-plan-agent-select">' + options + '</select>'
            + '<div class="planning-actions">'
            + '<button type="button" data-plan-action="send-agent-confirm">Queue plan</button>'
            + '<button type="button" data-plan-action="send-agent-cancel">Cancel</button>'
            + '</div>'
            + '</div>';
    }

    function selectedModel() {
        const modelSelect = byId('chat-model-select');
        if (!modelSelect || !modelSelect.value) {
            return null;
        }
        return modelSelect.value;
    }

    function selectedPlanningModel() {
        const modelSelect = byId('chat-planning-model-select');
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

    function syncPlanningModelSelection(model) {
        if (!model) {
            return;
        }
        const modelSelect = byId('chat-planning-model-select');
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
                : (rawRole.toLowerCase() === 'system'
                    ? 'chat-message-system'
                    : (rawRole.toLowerCase() === 'tool' ? 'chat-message-tool' : 'chat-message-assistant'));
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
            const toolActivity = message.toolActivity ? renderToolActivity(message.toolActivity) : '';
            return '<div class="chat-message ' + roleClass + '">'
                + '<div class="chat-message-role">' + role + '</div>'
                + thinking
                + (toolActivity || '<div class="chat-message-body">' + text + '</div>')
                + '</div>';
        }).join('');
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function renderToolActivity(activity) {
        const name = activity && activity.toolName ? String(activity.toolName) : 'tool';
        const status = activity && activity.status ? String(activity.status) : 'completed';
        const summary = activity && activity.summary ? String(activity.summary) : 'Tool call completed.';
        const callPreview = activity && activity.callPreview ? String(activity.callPreview) : 'No arguments.';
        const callDetail = activity && activity.callDetail ? String(activity.callDetail) : '';
        const resultPreview = activity && activity.resultPreview ? String(activity.resultPreview) : '';
        const resultDetail = activity && activity.resultDetail ? String(activity.resultDetail) : '';
        const createdAt = activity && activity.createdAt ? String(activity.createdAt) : '';
        const callTruncated = activity && activity.callTruncated ? ' <span class="chat-tool-muted">truncated</span>' : '';
        const resultTruncated = activity && activity.resultTruncated ? ' <span class="chat-tool-muted">truncated</span>' : '';

        return '<details class="chat-tool">'
            + '<summary class="chat-tool-toggle">'
            + '<span class="chat-tool-name">' + escapeHtml(name) + '</span>'
            + '<span class="chat-tool-status">' + escapeHtml(status) + '</span>'
            + '<span class="chat-tool-summary">' + escapeHtml(summary) + '</span>'
            + '</summary>'
            + '<div class="chat-tool-body">'
            + (createdAt ? '<div class="chat-tool-meta">' + escapeHtml(createdAt) + '</div>' : '')
            + '<div class="chat-tool-section"><div class="chat-tool-label">Call' + callTruncated + '</div>'
            + '<pre>' + escapeHtml(callDetail || callPreview) + '</pre></div>'
            + '<div class="chat-tool-section"><div class="chat-tool-label">Result' + resultTruncated + '</div>'
            + '<pre>' + escapeHtml(resultDetail || resultPreview || summary) + '</pre></div>'
            + '</div>'
            + '</details>';
    }

    function appendToolActivity(eventData, beforeEl) {
        const historyEl = byId('chat-history');
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }

        const wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message-tool';
        wrapper.innerHTML = '<div class="chat-message-role">tool</div>'
            + renderToolActivity(eventData.toolActivity || {});
        if (beforeEl && beforeEl.parentNode === historyEl) {
            historyEl.insertBefore(wrapper, beforeEl);
        } else {
            historyEl.appendChild(wrapper);
        }
        historyEl.scrollTop = historyEl.scrollHeight;
    }

    function appendSystemMessage(eventData, beforeEl) {
        const historyEl = byId('chat-history');
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }

        const text = eventData && eventData.renderedHtml
            ? String(eventData.renderedHtml)
            : formatPlainText(eventData && eventData.text ? eventData.text : '');
        const wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message-system';
        wrapper.innerHTML = '<div class="chat-message-role">system</div>'
            + '<div class="chat-message-body">' + text + '</div>';
        if (beforeEl && beforeEl.parentNode === historyEl) {
            historyEl.insertBefore(wrapper, beforeEl);
        } else {
            historyEl.appendChild(wrapper);
        }
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

    function planningAnswerMessage(question, answer, notes) {
        let message = 'Planning answer\n\n';
        message += 'Question: ' + String(question || '').trim() + '\n\n';
        if (answer) {
            message += 'Answer: ' + String(answer).trim() + '\n';
        }
        if (notes) {
            message += 'Notes: ' + String(notes).trim() + '\n';
        }
        return message.trim();
    }

    function clearPendingUserMessage() {
        const pending = byId('chat-history').querySelector('[data-pending-user="true"]');
        if (pending) {
            pending.remove();
        }
    }

    function appendTransientAssistantMessage(message) {
        const historyEl = byId('chat-history');
        if (!historyEl) {
            return null;
        }
        if (historyEl.textContent.trim() === 'No messages in this session yet.') {
            historyEl.innerHTML = '';
        }

        const wrapper = document.createElement('div');
        wrapper.className = 'chat-message chat-message-assistant chat-message-transient';
        wrapper.setAttribute('data-transient-assistant', 'true');
        wrapper.innerHTML = '<div class="chat-message-role">assistant</div>'
            + '<div class="chat-message-body">' + formatPlainText(message) + '</div>';
        historyEl.appendChild(wrapper);
        historyEl.scrollTop = historyEl.scrollHeight;
        return wrapper;
    }

    function removeTransientAssistantMessage(messageEl) {
        if (messageEl && messageEl.parentNode) {
            messageEl.remove();
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

    function renderSessions(sessionsOrIds) {
        const sessionsEl = byId('chat-session-list');
        const sessions = (sessionsOrIds || []).map(function(item) {
            if (typeof item === 'string') {
                return { conversationId: item, title: null, titleJobStatus: null };
            }
            return item || {};
        }).filter(function(session) {
            return session.conversationId;
        });
        latestSessions = sessions;
        pruneSelectedSessions(sessions);
        const activeId = activeConversationId();

        if (sessions.length === 0) {
            sessionsEl.innerHTML = '<li class="chat-session-empty">No persisted sessions yet.</li>';
            syncSelectAllCheckbox();
            return;
        }

        sessionsEl.innerHTML = sessions.map(function(session) {
            const id = String(session.conversationId);
            const escaped = escapeHtml(id);
            const title = sessionDisplayName(session);
            const escapedTitle = escapeHtml(title);
            const shortId = shortConversationLabel(id);
            const escapedShortId = escapeHtml(shortId);
            const activeClass = id === activeId ? ' active' : '';
            const checked = selectedSessionIds.has(id) ? ' checked' : '';
            const checkbox = '<label class="chat-session-check" title="Select chat">'
                + '<input type="checkbox" data-bulk-select="' + escaped + '" aria-label="Select ' + escapedTitle + '"' + checked + '>'
                + '</label>';
            if (editingSessionId === id) {
                return '<li class="chat-session-item editing">'
                    + '<div class="chat-session-entry' + activeClass + '">'
                    + '<div class="chat-session-topline">' + checkbox + '</div>'
                    + '<form class="chat-session-rename" data-rename-form="' + escaped + '">'
                    + '<input name="title" value="' + escapedTitle + '" autocomplete="off" aria-label="Chat title">'
                    + '<button type="submit" title="Save title" aria-label="Save title">&#10003;</button>'
                    + '<button type="button" title="Cancel rename" aria-label="Cancel rename" data-cancel-rename="' + escaped + '">&#215;</button>'
                    + '</form></div></li>';
            }
            return '<li class="chat-session-item">'
                + '<div class="chat-session-entry' + activeClass + '">'
                + '<div class="chat-session-topline">'
                + checkbox
                + '<code class="chat-session-inline-hash" title="' + escaped + '">' + escapedShortId + '</code>'
                + '<div class="chat-session-actions">'
                + '<button type="button" class="' + (session.favorite ? 'favorite' : '') + '" title="' + (session.favorite ? 'Remove favorite' : 'Favorite chat') + '" aria-label="' + (session.favorite ? 'Remove favorite ' : 'Favorite chat ') + escapedTitle + '" data-favorite-id="' + escaped + '" data-favorite-next="' + (session.favorite ? 'false' : 'true') + '">' + (session.favorite ? '&#9733;' : '&#9734;') + '</button>'
                + '<button type="button" title="Archive chat" aria-label="Archive chat ' + escapedTitle + '" data-archive-id="' + escaped + '" data-archive-name="' + escapedTitle + '">&#128230;</button>'
                + '<button type="button" title="Rename chat" aria-label="Rename chat ' + escapedTitle + '" data-rename-id="' + escaped + '">&#9998;</button>'
                + '<button type="button" title="Delete chat" aria-label="Delete chat ' + escapedTitle + '" data-delete-id="' + escaped + '" data-delete-name="' + escapedTitle + '">&#128465;</button>'
                + '</div></div>'
                + '<a href="#" class="chat-session-title" data-switch-id="' + escaped + '">'
                + '<span class="chat-session-title-label"><span class="chat-session-title-text">' + escapedTitle + '</span></span>'
                + '</a></div></li>';
        }).join('');
        syncSelectAllCheckbox();

        const activeSession = sessions.find(function(session) {
            return session.conversationId === activeId;
        });
        if (activeSession) {
            setActiveConversationId(activeSession.conversationId, activeSession.title);
        }
        if (editingSessionId) {
            const input = sessionsEl.querySelector('[data-rename-form="' + cssEscape(editingSessionId) + '"] input[name="title"]');
            if (input) {
                input.focus();
                input.select();
            }
        }
    }

    function sessionDisplayName(session) {
        if (session && session.title) {
            return String(session.title);
        }
        return session && session.conversationId ? 'Chat' : 'New chat';
    }

    function shortConversationLabel(conversationId) {
        return '#' + String(conversationId || '').replace(/-/g, '').slice(0, 8);
    }

    function pruneSelectedSessions(sessions) {
        const ids = new Set(sessions.map(function(session) {
            return String(session.conversationId);
        }));
        Array.from(selectedSessionIds).forEach(function(id) {
            if (!ids.has(id)) {
                selectedSessionIds.delete(id);
            }
        });
    }

    function syncSelectAllCheckbox() {
        const checkbox = byId('chat-session-select-all');
        if (!checkbox) {
            return;
        }
        const visibleIds = latestSessions.map(function(session) {
            return String(session.conversationId);
        });
        const selectedVisibleCount = visibleIds.filter(function(id) {
            return selectedSessionIds.has(id);
        }).length;
        checkbox.disabled = visibleIds.length === 0;
        checkbox.checked = visibleIds.length > 0 && selectedVisibleCount === visibleIds.length;
        checkbox.indeterminate = selectedVisibleCount > 0 && selectedVisibleCount < visibleIds.length;
    }

    async function loadSessions() {
        const data = await getJson('/api/chat/sessions');
        renderSessions(data.sessions || data.conversationIds);
        return data;
    }

    async function renameSession(conversationId, title) {
        const session = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/title', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: title })
        });
        editingSessionId = null;
        await loadSessions();
        if (activeConversationId() === conversationId) {
            setActiveConversationId(session.conversationId, session.title);
        }
    }

    async function deleteSession(conversationId, displayName) {
        const confirmed = window.confirm(
            'Delete chat "' + displayName + '"?\n\nConversation ID: ' + conversationId + '\n\nThis cannot be undone.'
        );
        if (!confirmed) {
            return;
        }
        const response = await fetch('/api/chat/' + encodeURIComponent(conversationId), { method: 'DELETE' });
        if (!response.ok) {
            throw await responseError(response);
        }
        if (activeConversationId() === conversationId) {
            setActiveConversationId(null);
            renderHistory([]);
            updateContextUsage(null);
            updatePlanStatus(null);
        }
        await loadSessions();
    }

    async function setFavoriteSession(conversationId, favorite) {
        await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/favorite', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ favorite: Boolean(favorite) })
        });
        await loadSessions();
    }

    async function archiveSession(conversationId, displayName) {
        const confirmed = window.confirm(
            'Archive chat "' + displayName + '"?\n\nConversation ID: ' + conversationId
        );
        if (!confirmed) {
            return;
        }
        await archiveSessionWithoutPrompt(conversationId);
        await loadSessions();
    }

    async function archiveSessionWithoutPrompt(conversationId) {
        await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/archive', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ archived: true })
        });
        selectedSessionIds.delete(conversationId);
        if (activeConversationId() === conversationId) {
            setActiveConversationId(null);
            renderHistory([]);
            updateContextUsage(null);
            updatePlanStatus(null);
        }
    }

    async function bulkAction(action) {
        const selected = latestSessions.filter(function(session) {
            return selectedSessionIds.has(String(session.conversationId));
        });
        if (selected.length === 0) {
            setError('Select at least one chat');
            return false;
        }
        if (action === 'favorite') {
            for (const session of selected) {
                await setFavoriteSession(String(session.conversationId), true);
            }
            selectedSessionIds.clear();
            await loadSessions();
            return true;
        }
        const summary = selected.map(function(session) {
            return '- ' + sessionDisplayName(session) + ' (' + session.conversationId + ')';
        }).join('\n');
        const verb = action === 'archive' ? 'Archive' : 'Delete';
        const warning = action === 'delete' ? '\n\nThis cannot be undone.' : '';
        const confirmed = window.confirm(verb + ' these chats?\n\n' + summary + warning);
        if (!confirmed) {
            return false;
        }
        for (const session of selected) {
            const id = String(session.conversationId);
            if (action === 'archive') {
                await archiveSessionWithoutPrompt(id);
            } else if (action === 'delete') {
                const response = await fetch('/api/chat/' + encodeURIComponent(id), { method: 'DELETE' });
                if (!response.ok) {
                    throw await responseError(response);
                }
                selectedSessionIds.delete(id);
                if (activeConversationId() === id) {
                    setActiveConversationId(null);
                    renderHistory([]);
                    updateContextUsage(null);
                    updatePlanStatus(null);
                }
            }
        }
        selectedSessionIds.clear();
        await loadSessions();
        return true;
    }

    async function loadHistory(conversationId) {
        if (!conversationId) {
            renderHistory([]);
            updateContextUsage(null);
            updatePlanStatus(null);
            return;
        }
        const data = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/history');
        setActiveConversationId(data.conversationId, data.title);
        renderHistory(data.messages);
        syncModelSelection(data.model);
        updateContextUsage(data.contextUsage);
        updatePlanStatus(data.planState);
        return data;
    }

    function pollConversationTitle(conversationId) {
        if (!conversationId) {
            return;
        }
        if (titlePollTimer) {
            window.clearTimeout(titlePollTimer);
            titlePollTimer = null;
        }
        let attempts = 0;
        const poll = async function() {
            attempts += 1;
            try {
                const data = await loadSessions();
                const sessions = data && Array.isArray(data.sessions) ? data.sessions : [];
                const session = sessions.find(function(item) {
                    return item.conversationId === conversationId;
                });
                const status = session && session.titleJobStatus ? String(session.titleJobStatus) : null;
                if (session && session.title) {
                    setActiveConversationId(conversationId, session.title);
                    return;
                }
                if (status === 'SUCCEEDED' || status === 'FAILED' || attempts >= 8) {
                    return;
                }
            } catch (error) {
                if (attempts >= 8) {
                    return;
                }
            }
            titlePollTimer = window.setTimeout(poll, 750);
        };
        titlePollTimer = window.setTimeout(poll, 750);
    }

    async function sendMessage(message) {
        if (requestInFlight) {
            await sendInterruptOrQueue(message);
            return;
        }
        requestInFlight = true;
        setFormBusy(true);
        const payload = {
            conversationId: activeConversationId(),
            message: message,
            model: selectedModel(),
            planningModel: selectedPlanningModel()
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
                    activeTurnId = data.turnId || null;
                    activeInterruptToken = data.interruptToken || null;
                    syncModelSelection(data.model);
                    updatePlanStatus(data.planState);
                    completedConversationId = data.conversationId;
                    return;
                }
                if (event.name === 'chunk') {
                    updateContextUsageIfPresent(data.contextUsage);
                    updateStreamingAssistantMessage(assistantEl, data);
                    return;
                }
                if (event.name === 'tool') {
                    appendToolActivity(data, assistantEl);
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'system') {
                    appendSystemMessage(data, assistantEl);
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'interrupt') {
                    appendPendingUserMessage(data.text || '');
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'context') {
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'interrupt') {
                    appendPendingUserMessage(data.text || '');
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'done') {
                    updateStreamingAssistantMessage(assistantEl, data);
                    updateContextUsage(data.contextUsage);
                    updatePlanStatus(data.planState);
                    completedConversationId = data.conversationId;
                    return;
                }
                if (event.name === 'error') {
                    throw new Error(data.message || 'stream failed');
                }
            });

            await loadHistory(completedConversationId);
            await loadSessions();
            pollConversationTitle(completedConversationId);
            setStatus();
        } catch (error) {
            clearPendingUserMessage();
            removeStreamingAssistantMessage(assistantEl);
            try {
                const activeId = activeConversationId();
                if (activeId) {
                    await loadHistory(activeId);
                }
                await loadSessions();
            } catch (reloadError) {
                // Keep the original streaming error visible.
            }
            throw error;
        } finally {
            requestInFlight = false;
            activeTurnId = null;
            activeInterruptToken = null;
            setFormBusy(false);
            sendNextQueuedMessage();
        }
    }

    async function sendInterruptOrQueue(message) {
        if (!activeTurnId || !activeInterruptToken || !activeConversationId()) {
            queueMessage(message);
            return;
        }
        try {
            const result = await getJson('/api/chat/turns/' + encodeURIComponent(activeTurnId) + '/interrupt', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    conversationId: activeConversationId(),
                    interruptToken: activeInterruptToken,
                    message: message
                })
            });
            if (result && result.status === 'ACCEPTED') {
                setStatus();
                return;
            }
            queueMessage(message);
        } catch (error) {
            queueMessage(message);
        }
    }

    function queueMessage(message) {
        queuedMessages.push(message);
        setError('Message queued for the next turn.');
    }

    function sendNextQueuedMessage() {
        if (requestInFlight || queuedMessages.length === 0) {
            return;
        }
        const nextMessage = queuedMessages.shift();
        window.setTimeout(function() {
            sendMessage(nextMessage).catch(function(error) {
                const input = byId('chat-input');
                if (input && !input.value) {
                    input.value = nextMessage;
                }
                setError(error.message);
            });
        }, 0);
    }

    async function sendCommand(command) {
        if (requestInFlight) {
            return;
        }
        requestInFlight = true;
        setFormDisabled(true);
        const transientEl = command === '/plan'
            ? appendTransientAssistantMessage('Planning mode received. I am setting up the planning workspace...')
            : null;
        const payload = {
            conversationId: activeConversationId(),
            command: command,
            model: selectedModel(),
            planningModel: selectedPlanningModel()
        };

        try {
            const data = await getJson('/api/chat/commands', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            applyCommandResponse(data);
            setStatus();
        } finally {
            removeTransientAssistantMessage(transientEl);
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    function applyCommandResponse(data) {
        setActiveConversationId(data.conversationId);
        syncModelSelection(data.model);
        renderHistory(data.history || []);
        renderSessions(data.sessions || data.conversationIds || []);
        updateContextUsage(data.contextUsage);
        updatePlanStatus(data.planState);
    }

    async function beginPlanFromDefinition(planId) {
        if (!planId || requestInFlight) {
            return;
        }
        requestInFlight = true;
        setFormDisabled(true);
        const transientEl = appendTransientAssistantMessage('Loading plan into planning chat...');
        try {
            const data = await getJson('/api/chat/plans/' + encodeURIComponent(planId) + '/continue', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    conversationId: activeConversationId(),
                    model: selectedModel(),
                    planningModel: selectedPlanningModel()
                })
            });
            applyCommandResponse(data);
            setStatus();
        } finally {
            removeTransientAssistantMessage(transientEl);
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    async function executePlanStream() {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return;
        }
        requestInFlight = true;
        setFormDisabled(true);
        clearPlanningPanel();
        appendTransientAssistantMessage('Execution request received. I am clearing context and starting from the approved plan...');
        renderHistory([]);
        const assistantEl = appendStreamingAssistantMessage();
        try {
            const response = await fetch('/api/chat/' + encodeURIComponent(conversationId) + '/plan/execute/stream', {
                method: 'POST',
                headers: { 'Accept': 'text/event-stream' }
            });
            if (!response.ok) {
                throw await responseError(response);
            }
            await readSse(response, async function(event) {
                const data = event.data || {};
                if (event.name === 'start') {
                    setActiveConversationId(data.conversationId);
                    activeTurnId = data.turnId || null;
                    activeInterruptToken = data.interruptToken || null;
                    syncModelSelection(data.model);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'chunk') {
                    updateContextUsageIfPresent(data.contextUsage);
                    updateStreamingAssistantMessage(assistantEl, data);
                    return;
                }
                if (event.name === 'tool') {
                    appendToolActivity(data, assistantEl);
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'system') {
                    appendSystemMessage(data, assistantEl);
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'context') {
                    updateContextUsageIfPresent(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'done') {
                    updateStreamingAssistantMessage(assistantEl, data);
                    updateContextUsage(data.contextUsage);
                    updatePlanStatus(data.planState);
                    return;
                }
                if (event.name === 'error') {
                    throw new Error(data.message || 'stream failed');
                }
            });
            await loadHistory(conversationId);
            await loadSessions();
            setStatus();
        } catch (error) {
            removeStreamingAssistantMessage(assistantEl);
            try {
                await loadHistory(conversationId);
                await loadSessions();
            } catch (reloadError) {
                // Keep the original streaming error visible.
            }
            throw error;
        } finally {
            requestInFlight = false;
            activeTurnId = null;
            activeInterruptToken = null;
            setFormDisabled(false);
        }
    }

    async function submitPlanningAnswer(form) {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return;
        }
        const formData = new FormData(form);
        const answer = String(formData.get('answer') || '').trim();
        const notes = String(formData.get('notes') || '').trim();
        const questionIndex = Number(formData.get('questionIndex') || 0);
        const question = String(formData.get('question') || '').trim();
        appendPendingUserMessage(planningAnswerMessage(question, answer, notes));
        clearPlanningPanel();
        requestInFlight = true;
        setFormDisabled(true);
        try {
            const data = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/plan/answers', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ answer: answer, notes: notes, questionIndex: questionIndex || null })
            });
            syncModelSelection(data.model);
            updateContextUsage(data.contextUsage);
            updatePlanStatus(data.planState);
            await loadHistory(data.conversationId || conversationId);
            await loadSessions();
            setStatus();
        } finally {
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    function defaultTaskTitle() {
        const planTitle = lastPlanState && lastPlanState.title ? String(lastPlanState.title).trim() : '';
        if (planTitle) {
            return planTitle;
        }
        return 'Untitled Task';
    }

    function clearAgentSendChooser() {
        showAgentSendChooser = false;
        sendAgentsLoading = false;
        assignableAgents = [];
    }

    function savedTaskForConversation(conversationId) {
        if (!conversationId) {
            return null;
        }
        return savedTaskByConversation.get(conversationId) || null;
    }

    function rememberSavedTask(conversationId, taskId, taskTitle) {
        if (!conversationId || !taskId) {
            return;
        }
        savedTaskByConversation.set(conversationId, {
            taskId: String(taskId),
            taskTitle: taskTitle ? String(taskTitle) : defaultTaskTitle()
        });
    }

    function promptForTaskTitle() {
        const title = window.prompt('Name this plan for your plans dashboard:', defaultTaskTitle());
        if (title === null) {
            return null;
        }
        const normalized = String(title).trim();
        if (!normalized) {
            throw new Error('Task name is required');
        }
        return normalized;
    }

    async function savePlanAsTaskWithTitle(taskTitle) {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return null;
        }
        requestInFlight = true;
        setFormDisabled(true);
        try {
            const response = await getJson('/api/chat/' + encodeURIComponent(conversationId) + '/plan/save-task', {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: taskTitle })
            });
            if (!response || !response.taskId) {
                throw new Error('Plan was not saved');
            }
            rememberSavedTask(conversationId, response.taskId, response.taskTitle);
            updatePlanStatus(response.planState);
            await loadHistory(conversationId);
            await loadSessions();
            setStatus();
            return savedTaskForConversation(conversationId);
        } finally {
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    async function ensureSavedTask(forceRename) {
        const conversationId = activeConversationId();
        if (!conversationId) {
            return null;
        }
        const existing = savedTaskForConversation(conversationId);
        const currentStatus = lastPlanState && lastPlanState.status ? String(lastPlanState.status) : '';
        if (existing && !forceRename && currentStatus === 'SAVED_TASK') {
            return existing;
        }
        const title = promptForTaskTitle();
        if (!title) {
            return null;
        }
        return savePlanAsTaskWithTitle(title);
    }

    async function loadAssignableAgents() {
        const agents = await getJson('/api/agents');
        return (Array.isArray(agents) ? agents : []).filter(function(agent) {
            const status = agent && agent.status ? String(agent.status).toUpperCase() : '';
            return status !== 'DISABLED';
        });
    }

    async function openSendAgentChooser() {
        const savedTask = await ensureSavedTask(false);
        if (!savedTask) {
            return;
        }
        showAgentSendChooser = true;
        sendAgentsLoading = true;
        assignableAgents = [];
        renderPlanningPanel(lastPlanState);
        try {
            assignableAgents = await loadAssignableAgents();
        } finally {
            sendAgentsLoading = false;
            renderPlanningPanel(lastPlanState);
        }
    }

    async function confirmSendToAgent() {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return;
        }
        const savedTask = await ensureSavedTask(false);
        if (!savedTask) {
            return;
        }
        const agentSelect = byId('chat-plan-agent-select');
        const agentId = agentSelect && agentSelect.value ? String(agentSelect.value) : null;
        if (!agentId) {
            throw new Error('Select an agent');
        }

        requestInFlight = true;
        setFormDisabled(true);
        try {
            await getJson('/api/plans/' + encodeURIComponent(savedTask.taskId) + '/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ agentId: agentId })
            });
            clearAgentSendChooser();
            renderPlanningPanel(lastPlanState);
            setStatus();
        } finally {
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    async function runPlanningAction(action) {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return;
        }
        if (action === 'cancel') {
            await runPlanningPatch('/plan/cancel');
            return;
        }
        if (action === 'execute') {
            await executePlanStream();
            return;
        }
        if (action === 'save-task') {
            await ensureSavedTask(true);
            return;
        }
        if (action === 'send-agent') {
            await openSendAgentChooser();
            return;
        }
        if (action === 'send-agent-cancel') {
            clearAgentSendChooser();
            renderPlanningPanel(lastPlanState);
            return;
        }
        if (action === 'send-agent-confirm') {
            await confirmSendToAgent();
            return;
        }
        const endpoint = action === 'approve'
            ? '/plan/approve'
            : (action === 'continue' ? '/plan/continue' : null);
        if (!endpoint) {
            return;
        }
        await runPlanningPatch(endpoint);
    }

    async function runPlanningPatch(endpoint) {
        const conversationId = activeConversationId();
        if (!conversationId || requestInFlight) {
            return;
        }
        requestInFlight = true;
        setFormDisabled(true);
        try {
            const data = await getJson('/api/chat/' + encodeURIComponent(conversationId) + endpoint, {
                method: 'PATCH'
            });
            updatePlanStatus(data);
            await loadHistory(conversationId);
            await loadSessions();
            setStatus();
        } finally {
            requestInFlight = false;
            setFormDisabled(false);
        }
    }

    function setFormBusy(busy) {
        const form = byId('chat-form');
        const button = form ? form.querySelector('button[type="submit"]') : null;
        if (button) {
            button.disabled = false;
            button.textContent = busy ? 'Send update' : 'Send';
        }
    }

    function setFormDisabled(disabled) {
        const input = byId('chat-input');
        const form = byId('chat-form');
        const button = form ? form.querySelector('button[type="submit"]') : null;
        if (input) {
            input.disabled = disabled;
        }
        if (button) {
            button.disabled = disabled;
        }
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
                if (text.startsWith('/') && !requestInFlight) {
                    await sendCommand(text);
                } else if (text.startsWith('/')) {
                    input.value = text;
                    setError('Commands are available after the active turn finishes.');
                } else {
                    await sendMessage(text);
                }
            } catch (error) {
                input.value = text;
                setError(error.message);
            }
        });

        const planningPanel = byId('chat-planning-panel');
        if (planningPanel) {
            planningPanel.addEventListener('submit', async function(event) {
                const form = event.target.closest('[data-planning-answer-form]');
                if (!form) {
                    return;
                }
                event.preventDefault();
                try {
                    await submitPlanningAnswer(form);
                } catch (error) {
                    setError(error.message);
                }
            });
        }

        document.addEventListener('click', async function(event) {
            const button = event.target.closest('[data-plan-action]');
            if (!button) {
                return;
            }
            event.preventDefault();
            try {
                await runPlanningAction(button.getAttribute('data-plan-action'));
            } catch (error) {
                setError(error.message);
            }
        });

        byId('chat-session-list').addEventListener('click', async function(event) {
            const favoriteButton = event.target.closest('[data-favorite-id]');
            if (favoriteButton) {
                event.preventDefault();
                try {
                    await setFavoriteSession(
                        favoriteButton.getAttribute('data-favorite-id'),
                        favoriteButton.getAttribute('data-favorite-next') === 'true'
                    );
                    setStatus();
                } catch (error) {
                    setError(error.message);
                }
                return;
            }
            const archiveButton = event.target.closest('[data-archive-id]');
            if (archiveButton) {
                event.preventDefault();
                try {
                    await archiveSession(
                        archiveButton.getAttribute('data-archive-id'),
                        archiveButton.getAttribute('data-archive-name') || archiveButton.getAttribute('data-archive-id')
                    );
                    setStatus();
                } catch (error) {
                    setError(error.message);
                }
                return;
            }
            const renameButton = event.target.closest('[data-rename-id]');
            if (renameButton) {
                event.preventDefault();
                editingSessionId = renameButton.getAttribute('data-rename-id');
                try {
                    await loadSessions();
                    setStatus();
                } catch (error) {
                    setError(error.message);
                }
                return;
            }
            const cancelButton = event.target.closest('[data-cancel-rename]');
            if (cancelButton) {
                event.preventDefault();
                editingSessionId = null;
                try {
                    await loadSessions();
                    setStatus();
                } catch (error) {
                    setError(error.message);
                }
                return;
            }
            const deleteButton = event.target.closest('[data-delete-id]');
            if (deleteButton) {
                event.preventDefault();
                try {
                    await deleteSession(
                        deleteButton.getAttribute('data-delete-id'),
                        deleteButton.getAttribute('data-delete-name') || deleteButton.getAttribute('data-delete-id')
                    );
                    setStatus();
                } catch (error) {
                    setError(error.message);
                }
                return;
            }
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

        byId('chat-session-list').addEventListener('change', function(event) {
            const checkbox = event.target.closest('[data-bulk-select]');
            if (!checkbox) {
                return;
            }
            const id = checkbox.getAttribute('data-bulk-select');
            if (checkbox.checked) {
                selectedSessionIds.add(id);
            } else {
                selectedSessionIds.delete(id);
            }
            syncSelectAllCheckbox();
        });

        const selectAllCheckbox = byId('chat-session-select-all');
        if (selectAllCheckbox) {
            selectAllCheckbox.addEventListener('change', function(event) {
                const checked = event.target.checked;
                latestSessions.forEach(function(session) {
                    const id = String(session.conversationId);
                    if (checked) {
                        selectedSessionIds.add(id);
                    } else {
                        selectedSessionIds.delete(id);
                    }
                });
                renderSessions(latestSessions);
            });
        }

        document.querySelectorAll('[data-bulk-action]').forEach(function(button) {
            button.addEventListener('click', async function() {
                try {
                    const completed = await bulkAction(button.getAttribute('data-bulk-action'));
                    if (completed) {
                        setStatus();
                    }
                } catch (error) {
                    setError(error.message);
                }
            });
        });

        byId('chat-session-list').addEventListener('submit', async function(event) {
            const form = event.target.closest('[data-rename-form]');
            if (!form) {
                return;
            }
            event.preventDefault();
            const conversationId = form.getAttribute('data-rename-form');
            const input = form.querySelector('input[name="title"]');
            const title = input ? input.value.trim() : '';
            if (!title) {
                setError('Title is required');
                return;
            }
            try {
                await renameSession(conversationId, title);
                setStatus();
            } catch (error) {
                setError(error.message);
            }
        });

        try {
            await loadHistory(activeConversationId());
            await loadSessions();
            const continuePlanId = root().getAttribute('data-continue-plan-id');
            if (continuePlanId) {
                await beginPlanFromDefinition(continuePlanId);
            } else if (root().getAttribute('data-start-planning') === 'true') {
                await sendCommand('/plan');
            }
        } catch (error) {
            setError(error.message);
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        init();
    });

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(value);
        }
        return String(value).replace(/"/g, '\\"');
    }
})();
