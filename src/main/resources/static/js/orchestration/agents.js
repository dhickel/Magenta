// Minimal skeleton — agent list, detail, editor, and submit flows are HTMX-driven.
// Kept as page-level dispatch anchor; add JS only when HTMX is materially more complex.

document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='agents']");
    if (!page) return;
    // HTMX handles all agent CRUD, tab loading, editor saves, and submit-to-agent.
    // No JS listeners needed at this time.
});
