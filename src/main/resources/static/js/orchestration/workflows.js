// workflows.js — v2 HTMX-first workflow editing.
// All workflow CRUD, node add/remove, route add/remove, validation,
// and submit-to-agent are handled via HTMX endpoints.
//
// This file is preserved for future vanilla JS affordances
// (keyboard shortcuts, drag-and-drop reorder) that are impractical in pure HTMX.
document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='workflows']");
    if (!page) {
        return;
    }
    // All workflow editing is handled via HTMX endpoints.
    // No JS-based rendering, save, run, or validation handlers.
});
