// plans.js — v3 HTMX-first plan editing.
// All plan CRUD, field add/remove, list editing, submit-to-agent, and
// continue-in-chat are handled via HTMX endpoints.
//
// The small textarea autosize helper is intentionally client-side: textarea
// scrollHeight is the browser's source of truth after wrapping, fonts, and HTMX
// swaps have settled.
document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='plans']");
    if (!page) {
        return;
    }

    function autosizeTextarea(textarea) {
        if (!textarea || textarea.dataset.autosize !== "true") {
            return;
        }
        textarea.style.height = "auto";
        textarea.style.overflowY = "hidden";
        textarea.style.height = textarea.scrollHeight + "px";
    }

    function autosizeAll(root = page) {
        root.querySelectorAll("textarea[data-autosize='true']").forEach(autosizeTextarea);
    }

    autosizeAll();

    page.addEventListener("input", (event) => {
        autosizeTextarea(event.target);
    });

    document.body.addEventListener("htmx:afterSwap", (event) => {
        if (!page.contains(event.target)) {
            return;
        }
        window.requestAnimationFrame(() => autosizeAll(event.target));
    });
});
