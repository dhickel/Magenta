const WORKAREA_HISTORY_DEBOUNCE_MS = 180;
const WORKAREA_PREVIEW_DEBOUNCE_MS = 260;
const WORKAREA_HISTORY_LIMIT = 120;

document.addEventListener("DOMContentLoaded", initAvatarWorkAreaEditors);
document.addEventListener("htmx:afterSettle", initAvatarWorkAreaEditors);

function initAvatarWorkAreaEditors() {
    const editors = document.querySelectorAll("[data-avatar-workarea-editor='true']");
    for (const editor of editors) {
        bindWorkAreaEditor(editor);
    }
}

function bindWorkAreaEditor(editor) {
    if (editor.dataset.editorBound === "true") {
        return;
    }
    editor.dataset.editorBound = "true";

    const source = editor.querySelector("[data-editor-source='true']");
    if (!source) {
        return;
    }

    const previewPane = editor.querySelector("[data-editor-preview-pane='true']");
    const status = editor.querySelector("[data-editor-status='true']");
    const modeButtons = Array.from(editor.querySelectorAll("[data-editor-mode]"));
    const undoButton = editor.querySelector("[data-editor-undo='true']");
    const redoButton = editor.querySelector("[data-editor-redo='true']");
    const revertButton = editor.querySelector("[data-editor-revert='true']");
    const previewUrl = editor.dataset.editorPreviewUrl || "";
    const markdown = editor.dataset.viewerKind === "markdown";

    const state = {
        savedValue: source.value,
        history: [source.value],
        historyIndex: 0,
        applyingSnapshot: false,
        historyTimer: null,
        previewTimer: null,
        previewAbortController: null
    };

    const setStatus = (message, isError) => {
        if (!status) {
            return;
        }
        status.textContent = message;
        status.classList.toggle("avatar-status-error", Boolean(isError));
    };

    const setMode = mode => {
        const normalized = normalizeMode(mode, markdown);
        editor.dataset.activeTab = normalized;
        editor.classList.remove("mode-edit", "mode-preview", "mode-split");
        editor.classList.add(`mode-${normalized}`);

        for (const button of modeButtons) {
            const active = button.dataset.editorMode === normalized;
            button.classList.toggle("avatar-tab-active", active);
            button.classList.toggle("button-secondary", !active);
            button.setAttribute("aria-pressed", active ? "true" : "false");
            button.setAttribute("aria-selected", active ? "true" : "false");
            button.setAttribute("tabindex", active ? "0" : "-1");
        }

        if (markdown && normalized !== "edit") {
            queuePreviewSync(true);
        }
    };

    const updateButtons = () => {
        if (undoButton) {
            undoButton.disabled = state.historyIndex <= 0;
        }
        if (redoButton) {
            redoButton.disabled = state.historyIndex >= state.history.length - 1;
        }
    };

    const updateDirtyState = () => {
        const dirty = source.value !== state.savedValue;
        editor.dataset.editorDirty = dirty ? "true" : "false";
        if (revertButton) {
            revertButton.disabled = !dirty;
        }
        if (dirty) {
            setStatus("Unsaved changes.", false);
        } else {
            setStatus("Saved copy loaded.", false);
        }
    };

    const pushSnapshot = nextValue => {
        if (state.history[state.historyIndex] === nextValue) {
            return;
        }
        if (state.historyIndex < state.history.length - 1) {
            state.history = state.history.slice(0, state.historyIndex + 1);
        }
        state.history.push(nextValue);
        if (state.history.length > WORKAREA_HISTORY_LIMIT) {
            state.history.shift();
        }
        state.historyIndex = state.history.length - 1;
        updateButtons();
    };

    const flushHistoryCapture = () => {
        if (state.applyingSnapshot) {
            return;
        }
        if (state.historyTimer !== null) {
            window.clearTimeout(state.historyTimer);
            state.historyTimer = null;
        }
        pushSnapshot(source.value);
    };

    const applySnapshot = (nextValue, statusMessage) => {
        if (state.historyTimer !== null) {
            window.clearTimeout(state.historyTimer);
            state.historyTimer = null;
        }
        state.applyingSnapshot = true;
        source.value = nextValue;
        state.applyingSnapshot = false;
        updateDirtyState();
        updateButtons();
        if (statusMessage) {
            setStatus(statusMessage, false);
        }
        if (markdown && currentMode(editor) !== "edit") {
            queuePreviewSync(true);
        }
    };

    const queueHistoryCapture = () => {
        if (state.applyingSnapshot) {
            return;
        }
        if (state.historyTimer !== null) {
            window.clearTimeout(state.historyTimer);
        }
        state.historyTimer = window.setTimeout(() => {
            state.historyTimer = null;
            pushSnapshot(source.value);
        }, WORKAREA_HISTORY_DEBOUNCE_MS);
    };

    const queuePreviewSync = immediate => {
        if (!markdown || !previewPane || !previewUrl) {
            return;
        }
        window.clearTimeout(state.previewTimer);
        state.previewTimer = window.setTimeout(() => {
            syncPreview(previewUrl, source.value, previewPane, setStatus, state);
        }, immediate ? 10 : WORKAREA_PREVIEW_DEBOUNCE_MS);
    };

    source.addEventListener("input", () => {
        updateDirtyState();
        queueHistoryCapture();
        if (markdown) {
            queuePreviewSync(false);
        }
    });

    for (const button of modeButtons) {
        button.addEventListener("click", () => {
            setMode(button.dataset.editorMode || "edit");
        });
    }

    undoButton?.addEventListener("click", () => {
        flushHistoryCapture();
        if (state.historyIndex <= 0) {
            return;
        }
        state.historyIndex -= 1;
        applySnapshot(state.history[state.historyIndex], "Undo applied.");
    });

    redoButton?.addEventListener("click", () => {
        if (state.historyIndex >= state.history.length - 1) {
            return;
        }
        state.historyIndex += 1;
        applySnapshot(state.history[state.historyIndex], "Redo applied.");
    });

    revertButton?.addEventListener("click", () => {
        if (source.value === state.savedValue) {
            return;
        }
        pushSnapshot(state.savedValue);
        applySnapshot(state.savedValue, "Unsaved changes reverted.");
    });

    setMode(editor.dataset.activeTab || "edit");
    updateDirtyState();
    updateButtons();
}

function syncPreview(previewUrl, content, previewPane, setStatus, state) {
    state.previewAbortController?.abort();
    const abortController = new AbortController();
    state.previewAbortController = abortController;

    const payload = new URLSearchParams();
    payload.set("content", content);

    fetch(previewUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            "HX-Request": "true"
        },
        body: payload.toString(),
        signal: abortController.signal
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`preview request failed: ${response.status}`);
            }
            return response.text();
        })
        .then(html => {
            if (state.previewAbortController !== abortController) {
                return;
            }
            previewPane.innerHTML = html;
            setStatus("Preview updated from unsaved text.", false);
        })
        .catch(error => {
            if (error?.name === "AbortError") {
                return;
            }
            previewPane.innerHTML = "<div class=\"avatar-status-error\">Preview unavailable.</div>";
            setStatus("Preview unavailable.", true);
        });
}

function currentMode(editor) {
    return normalizeMode(editor.dataset.activeTab, editor.dataset.viewerKind === "markdown");
}

function normalizeMode(mode, markdown) {
    if (!markdown) {
        return "edit";
    }
    if (mode === "split") {
        return "split";
    }
    if (mode === "preview") {
        return "preview";
    }
    return "edit";
}
