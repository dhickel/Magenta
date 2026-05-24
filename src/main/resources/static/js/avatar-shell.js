const AVATAR_RAIL_KEY = "magenta.avatar.chatRailWidthPx";
const AVATAR_RAIL_MIN = 320;
const AVATAR_RAIL_MAX = 640;
const AVATAR_DESKTOP_QUERY = "(min-width: 1181px)";

document.addEventListener("DOMContentLoaded", initAvatarShell);
document.addEventListener("htmx:afterSettle", initAvatarShell);

function initAvatarShell() {
    const shellRoot = document.querySelector("[data-avatar-shell='true']");
    if (!shellRoot || shellRoot.dataset.avatarShellInitialized === "true") {
        return;
    }
    shellRoot.dataset.avatarShellInitialized = "true";

    const railScope = shellRoot.querySelector(".avatar-shell") || shellRoot;
    restoreRailWidth(railScope);
    bindRailResizer(shellRoot, railScope);
}

function bindRailResizer(shellRoot, railScope) {
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
        const move = pointerEvent => applyRailWidth(railScope, window.innerWidth - pointerEvent.clientX);
        const stop = pointerEvent => {
            document.removeEventListener("pointermove", move);
            document.removeEventListener("pointerup", stop);
            document.removeEventListener("pointercancel", stop);
            resizer.releasePointerCapture?.(event.pointerId);
            if (pointerEvent) {
                persistRailWidth(window.innerWidth - pointerEvent.clientX);
            }
        };

        document.addEventListener("pointermove", move);
        document.addEventListener("pointerup", stop);
        document.addEventListener("pointercancel", stop);
    });
}

function restoreRailWidth(shell) {
    if (!window.matchMedia(AVATAR_DESKTOP_QUERY).matches) {
        return;
    }
    const saved = Number.parseInt(window.localStorage.getItem(AVATAR_RAIL_KEY) || "", 10);
    if (Number.isFinite(saved)) {
        applyRailWidth(shell, saved);
    }
}

function persistRailWidth(width) {
    const bounded = clampRailWidth(width);
    window.localStorage.setItem(AVATAR_RAIL_KEY, `${bounded}`);
}

function applyRailWidth(shell, width) {
    const bounded = clampRailWidth(width);
    shell.style.setProperty("--avatar-chat-rail-width", `${bounded}px`);
}

function clampRailWidth(width) {
    const safeWidth = Number.isFinite(width) ? width : AVATAR_RAIL_MIN;
    const viewportMax = Math.max(AVATAR_RAIL_MIN, Math.min(AVATAR_RAIL_MAX, Math.floor(window.innerWidth * 0.45)));
    return Math.max(AVATAR_RAIL_MIN, Math.min(viewportMax, safeWidth));
}
