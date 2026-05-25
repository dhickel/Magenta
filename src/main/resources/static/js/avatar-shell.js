const AVATAR_RAIL_KEY = "magenta.avatar.chatRailWidthPx";
// Keep in sync with CSS: .avatar-shell-grid minmax(22.85rem, var(--avatar-chat-rail-width)).
const AVATAR_RAIL_MIN = 366;
const AVATAR_RAIL_MAX = 640;
const AVATAR_MAIN_MIN = 520;
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
    if (!grid) {
        return;
    }

    restoreRailWidth(railScope, grid);
    bindRailResizer(shellRoot, railScope, grid);
}

function bindRailResizer(shellRoot, railScope, grid) {
    const resizer = shellRoot.querySelector("[data-avatar-chat-resizer='true']");
    if (!resizer) {
        return;
    }

    resizer.addEventListener("pointerdown", event => {
        if (!window.matchMedia(AVATAR_DESKTOP_QUERY).matches) {
            return;
        }

        event.preventDefault();
        resizer.setPointerCapture?.(event.pointerId);
        const startX = event.clientX;
        let didDrag = false;
        const move = pointerEvent => {
            const railWidth = resolveRailWidthFromPointer(pointerEvent, grid);
            if (!Number.isFinite(railWidth)) {
                return;
            }
            if (!didDrag && Math.abs(pointerEvent.clientX - startX) >= DRAG_THRESHOLD_PX) {
                didDrag = true;
            }
            if (didDrag) {
                applyRailWidth(railScope, railWidth, grid, resizer);
            }
        };
        const stop = pointerEvent => {
            document.removeEventListener("pointermove", move);
            document.removeEventListener("pointerup", stop);
            document.removeEventListener("pointercancel", stop);
            resizer.releasePointerCapture?.(event.pointerId);
            if (pointerEvent && didDrag) {
                persistRailWidth(resolveRailWidthFromPointer(pointerEvent, grid), grid, resizer);
            }
        };

        document.addEventListener("pointermove", move);
        document.addEventListener("pointerup", stop);
        document.addEventListener("pointercancel", stop);
    });
}

function restoreRailWidth(shell, grid) {
    if (!window.matchMedia(AVATAR_DESKTOP_QUERY).matches) {
        return;
    }
    const saved = Number.parseInt(window.localStorage.getItem(AVATAR_RAIL_KEY) || "", 10);
    if (Number.isFinite(saved)) {
        applyRailWidth(shell, saved, grid);
    }
}

function persistRailWidth(width, grid, resizer) {
    const bounded = clampRailWidth(width, grid, resizer);
    window.localStorage.setItem(AVATAR_RAIL_KEY, `${bounded}`);
}

function applyRailWidth(shell, width, grid, resizer) {
    const bounded = clampRailWidth(width, grid, resizer);
    shell.style.setProperty("--avatar-chat-rail-width", `${bounded}px`);
}

function resolveRailWidthFromPointer(pointerEvent, grid) {
    const rect = grid?.getBoundingClientRect?.();
    if (!rect || !Number.isFinite(rect.left)) {
        return Number.NaN;
    }
    return Math.floor(pointerEvent.clientX - rect.left);
}

function clampRailWidth(width, grid, resizer) {
    const safeWidth = Number.isFinite(width) ? width : AVATAR_RAIL_MIN;
    const rect = grid?.getBoundingClientRect?.();
    const dividerWidth = Math.ceil(resizer?.getBoundingClientRect?.().width || 14);
    const containerWidth = Number.isFinite(rect?.width) ? Math.floor(rect.width) : window.innerWidth;
    const maxFromContainer = containerWidth - dividerWidth - AVATAR_MAIN_MIN;
    const boundedMax = Math.min(AVATAR_RAIL_MAX, maxFromContainer);
    const effectiveMax = Math.max(AVATAR_RAIL_MIN, boundedMax);
    return Math.max(AVATAR_RAIL_MIN, Math.min(effectiveMax, safeWidth));
}
