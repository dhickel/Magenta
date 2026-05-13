// Projects — HTMX-first (v=3)
// Project list, editor, detail fragments, save/delete all handled by HTMX endpoints.
// JS skeleton preserved for future interactive affordances.

document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='projects']");
    if (!page) return;
    // HTMX-first: all CRUD and list loading handled via hx-get/hx-post/hx-put/hx-delete endpoints
});
