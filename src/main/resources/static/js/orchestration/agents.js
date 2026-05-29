// HTMX handles all agent CRUD, selection swaps, tab loading, editor saves, and submit-to-agent.
// JS is used only for selected-row and tab active-state affordances.
document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector("[data-orchestration-page='agents']");
    if (!page) return;

    const tabButtons = () => Array.from(document.querySelectorAll("[data-tab-button='true']"));
    const setActive = (tabName) => {
        tabButtons().forEach((btn) => {
            if (btn.dataset.tab === tabName) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });
    };

    setActive("dashboard");
    document.body.addEventListener("click", (event) => {
        const button = event.target.closest("[data-tab-button='true']");
        if (button) {
            setActive(button.dataset.tab || "");
            return;
        }

        const row = event.target.closest("[data-agent-selector-row='true']");
        if (!row) return;

        document.querySelectorAll("[data-agent-selector-row='true']").forEach((item) => {
            item.classList.toggle("selected", item === row);
        });
        page.dataset.selectedAgentId = row.dataset.agentId || "";
        const selectedInput = document.querySelector("#selected-agent-id");
        if (selectedInput) selectedInput.value = page.dataset.selectedAgentId;
    });
});
