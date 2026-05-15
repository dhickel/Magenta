// plans.js — v2 HTMX-first plan editing.
// All plan CRUD, field add/remove, list editing, submit-to-agent, and
// continue-in-chat are handled via HTMX endpoints.
//
// This file is preserved for future vanilla JS affordances
// (keyboard shortcuts, drag-and-drop reorder) that are impractical in pure HTMX.
document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='plans']");
    if (!page)
    // All plan editing is handled via HTMX endpoints.
    // No JS-based rendering, save, or run handlers.
});
