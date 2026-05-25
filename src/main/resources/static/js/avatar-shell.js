const AVATAR_RAIL_KEY = "magenta.avatar.chatRailWidthPx";
const AVATAR_PANEL_HEIGHT_KEY = "magenta.avatar.chatPanelHeightPx";
// Keep in sync with CSS: .avatar-shell-grid minmax(22.85rem, var(--avatar-chat-rail-width)).
const AVATAR_RAIL_MIN = 366;
const AVATAR_RAIL_MAX = 640;
const AVATAR_MAIN_MIN = 420;
const AVATAR_PANEL_MIN = 360;
const AVATAR_PANEL_BOTTOM_MARGIN = 24;
const DRAG_THRESHOLD_PX = 3;
const AVATAR_DESKTOP_QUERY = "(min-width: 1181px)";

document.addEventListener("DOMContentLoaded", initAvatarShell);
document.addEventListener("htmx:afterSettle", initAvatarShell);

function initAvatarShell() {
    const shellRoot = document.querySelector("[data-avatar-shell='true']");
    if (!shellRoot || shellRoot.dataset.avatarShellInitialized === "true") {
        return;
    }
    shellRoot.dataset.avatarShellInitialized = "true";

    const grid = shellRoot.querySelector(".avatar-shell-grid");
    const railScope = shellRoot.querySelector(".avatar-shell") || shellRoot;
    const chat = shellRoot.querySelector("[data-avatar-chat='true']");
    if (!grid || !chat) {
        return;
    }

    restoreChatSize(railScope, grid, chat);
    bindCornerResizer(shellRoot, railScope, grid, chat);
}

function bindCornerResizer(shellRoot, railScope, grid, chat) {
    const resizer = shellRoot.querySelector("[data-avatar-chat-corner-resizer='true']");
    if (!resizer) {
        return;
    }

    bindCornerResizerMediaState(resizer);

    resizer.addEventListener("pointerdown", event => {
        if (!window.matchMedia(AVATAR_DESKTOP_QUERY).matches) {
            return;
        }

        event.preventDefault();
        resizer.setPointerCapture?.(event.pointerId);

        const startRect = chat.getBoundingClientRect();
        const startX = event.clientX;
        const startY = event.clientY;
        const startWidth = Math.floor(startRect.width);
        const startHeight = Math.floor(startRect.height);
        let didDrag = false;

        const move = pointerEvent => {
            const nextWidth = startWidth + (pointerEvent.clientX - startX);
            const nextHeight = startHeight + (pointerEvent.clientY - startY);
            if (!didDrag && movedEnough(pointerEvent, startX, startY)) {
                didDrag = true;
            }
            if (didDrag) {
                applyChatSize(railScope, nextWidth, nextHeight, grid, chat);
            }
        };

        const stop = pointerEvent => {
            document.removeEventListener("pointermove", move);
            document.removeEventListener("pointerup", stop);
            document.removeEventListener("pointercancel", stop);
            resizer.releasePointerCapture?.(event.pointerId);
            if (pointerEvent && didDrag) {
                const finalWidth = startWidth + (pointerEvent.clientX - startX);
                const finalHeight = startHeight + (pointerEvent.clientY - startY);
                persistChatSize(finalWidth, finalHeight, grid, chat);
            }
        };

        document.addEventListener("pointermove", move);
        document.addEventListener("pointerup", stop);
        document.addEventListener("pointercancel", stop);
    });
}

function bindCornerResizerMediaState(resizer) {
    const desktopQuery = window.matchMedia(AVATAR_DESKTOP_QUERY);
    const sync = () => {
        const isDesktop = desktopQuery.matches;
        resizer.hidden = !isDesktop;
        resizer.setAttribute("aria-hidden", isDesktop ? "false" : "true");
        resizer.tabIndex = isDesktop ? 0 : -1;
    };

    sync();
    desktopQuery.addEventListener?.("change", sync);
}

function restoreChatSize(shell, grid, chat) {
    if (!window.matchMedia(AVATAR_DESKTOP_QUERY).matches) {
        return;
    }
    const savedWidth = Number.parseInt(window.localStorage.getItem(AVATAR_RAIL_KEY) || "", 10);
    if (Number.isFinite(savedWidth)) {
        applyRailWidth(shell, savedWidth, grid);
    }

    const savedHeight = Number.parseInt(window.localStorage.getItem(AVATAR_PANEL_HEIGHT_KEY) || "", 10);
    if (Number.isFinite(savedHeight)) {
        const boundedHeight = applyPanelHeight(shell, savedHeight, chat);
        if (boundedHeight !== savedHeight) {
            window.localStorage.setItem(AVATAR_PANEL_HEIGHT_KEY, `${boundedHeight}`);
        }
    }
}

function persistChatSize(width, height, grid, chat) {
    const boundedWidth = clampRailWidth(width, grid);
    const boundedHeight = clampPanelHeight(height, chat);
    window.localStorage.setItem(AVATAR_RAIL_KEY, `${boundedWidth}`);
    window.localStorage.setItem(AVATAR_PANEL_HEIGHT_KEY, `${boundedHeight}`);
}

function applyChatSize(shell, width, height, grid, chat) {
    applyRailWidth(shell, width, grid);
    applyPanelHeight(shell, height, chat);
}

function applyRailWidth(shell, width, grid) {
    const bounded = clampRailWidth(width, grid);
    shell.style.setProperty("--avatar-chat-rail-width", `${bounded}px`);
}

function applyPanelHeight(shell, height, chat) {
    const bounded = clampPanelHeight(height, chat);
    shell.style.setProperty("--avatar-chat-panel-max-height", `${maxPanelHeight(chat)}px`);
    shell.style.setProperty("--avatar-chat-panel-height", `${bounded}px`);
    return bounded;
}

function clampRailWidth(width, grid) {
    const safeWidth = Number.isFinite(width) ? Math.floor(width) : AVATAR_RAIL_MIN;
    const rect = grid?.getBoundingClientRect?.();
    const containerWidth = Number.isFinite(rect?.width) ? Math.floor(rect.width) : window.innerWidth;
    const maxFromContainer = containerWidth - AVATAR_MAIN_MIN;
    const boundedMax = Math.min(AVATAR_RAIL_MAX, maxFromContainer);
    const effectiveMax = Math.max(AVATAR_RAIL_MIN, boundedMax);
    return Math.max(AVATAR_RAIL_MIN, Math.min(effectiveMax, safeWidth));
}

function clampPanelHeight(height, chat) {
    const safeHeight = Number.isFinite(height) ? Math.floor(height) : AVATAR_PANEL_MIN;
    const maxHeight = maxPanelHeight(chat);
    return Math.max(AVATAR_PANEL_MIN, Math.min(maxHeight, safeHeight));
}

function maxPanelHeight(chat) {
    const rect = chat?.getBoundingClientRect?.();
    const visibleTop = Number.isFinite(rect?.top) ? Math.max(0, Math.floor(rect.top)) : 0;
    const availableHeight = Math.floor(window.innerHeight - visibleTop - AVATAR_PANEL_BOTTOM_MARGIN);
    return Math.max(AVATAR_PANEL_MIN, availableHeight);
}

function movedEnough(pointerEvent, startX, startY) {
    return Math.abs(pointerEvent.clientX - startX) >= DRAG_THRESHOLD_PX
        || Math.abs(pointerEvent.clientY - startY) >= DRAG_THRESHOLD_PX;
}
