const pickerState = {
    triggerRect: null
};

document.addEventListener("DOMContentLoaded", initAvatarLayoutEdit);
document.addEventListener("htmx:afterSettle", initAvatarLayoutEdit);

function initAvatarLayoutEdit() {
    if (document.body.dataset.avatarLayoutEditBound === "true") {
        return;
    }
    document.body.dataset.avatarLayoutEditBound = "true";

    document.addEventListener("click", event => {
        const trigger = event.target.closest("[data-avatar-width-picker-trigger]");
        if (trigger) {
            pickerState.triggerRect = trigger.getBoundingClientRect();
            return;
        }

        if (event.target.closest("[data-avatar-width-picker-dismiss]")) {
            closeWidthPicker();
        }
    }, true);

    document.body.addEventListener("htmx:afterSwap", event => {
        if (event.target && event.target.id === "avatar-edit-container") {
            positionWidthPicker();
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            closeWidthPicker();
        }
    });
}

function positionWidthPicker() {
    const picker = document.querySelector("[data-avatar-width-picker]");
    if (!picker) {
        return;
    }
    const rect = pickerState.triggerRect;
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const pickerWidth = Math.min(352, viewportWidth - 16);
    const pickerHeight = picker.offsetHeight || 248;
    const defaultLeft = viewportWidth - pickerWidth - 8;
    const defaultTop = 56;

    let left = rect ? rect.right - pickerWidth : defaultLeft;
    let top = rect ? rect.bottom + 10 : defaultTop;

    left = Math.max(8, Math.min(left, viewportWidth - pickerWidth - 8));
    top = Math.max(8, Math.min(top, viewportHeight - pickerHeight - 8));

    picker.style.left = `${left}px`;
    picker.style.top = `${top}px`;
    picker.classList.add("is-positioned");

    const input = picker.querySelector("input[name='columnWidth']");
    if (input) {
        input.focus({ preventScroll: true });
        input.select();
    }
}

function closeWidthPicker() {
    if (!document.querySelector("[data-avatar-width-picker]")) {
        return;
    }
    const container = document.getElementById("avatar-edit-container");
    if (container) {
        container.innerHTML = "";
    }
}
