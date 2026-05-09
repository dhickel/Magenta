import { consumeSse, jsonFetch, renderError } from "./orchestration/api.js";
import { $, $$, escapeHtml } from "./orchestration/dom.js";

const TASK_TYPES = ["string", "long_text", "file_path", "json", "number", "boolean"];

document.addEventListener("DOMContentLoaded", () => {
    const page = $("[data-orchestration-page]");
    if (!page) return;
    if (page.dataset.orchestrationPage === "tasks") initTasks(page);
    if (page.dataset.orchestrationPage === "workflows") initWorkflows(page);
});

function lines(id) {
    const node = document.getElementById(id);
    return node ? node.value.split("\n").map(value => value.trim()).filter(Boolean) : [];
}

function fieldRow(kind, field = {}) {
    const options = TASK_TYPES.map(type => {
        const selected = field.type === type || field.type === type.toUpperCase() ? "selected" : "";
        return `<option value="${type}" ${selected}>${type}</option>`;
    }).join("");
    return `
        <div class="field-row ${kind}-field">
            <input placeholder="name" value="${escapeHtml(field.name || "")}">
            <select>${options}</select>
            <label><input type="checkbox" ${field.required ? "checked" : ""}> required</label>
            <input placeholder="description" value="${escapeHtml(field.description || "")}">
            <input placeholder="example" value="${escapeHtml(field.example || "")}">
        </div>`;
}

function readFields(kind) {
    return $$(`.${kind}-field`).map(row => ({
        name: row.children[0].value,
        type: row.children[1].value,
        required: row.children[2].querySelector("input").checked,
        description: row.children[3].value,
        example: row.children[4].value
    })).filter(field => field.name);
}

function renderFields(kind, fields) {
    const host = document.getElementById(`task-${kind}s`);
    if (host) host.innerHTML = (fields || []).map(field => fieldRow(kind, field)).join("");
}

function renderRunForm() {
    const host = document.getElementById("task-run-form");
    if (!host) return;
    host.innerHTML = readFields("input")
        .map(field => `<label>${escapeHtml(field.name)}<input data-input="${escapeHtml(field.name)}" placeholder="${escapeHtml(field.type)}"></label>`)
        .join("");
}

async function initTasks(root) {
    let currentTask = null;
    const log = $("#task-run-log", root);

    async function loadTasks() {
        const tasks = await jsonFetch("/api/tasks");
        $("#task-list", root).innerHTML = tasks.map(task => `
            <button class="tool-item" data-id="${escapeHtml(task.id)}">
                <strong>${escapeHtml(task.title)}</strong><br>
                <span>${escapeHtml(task.summary || "No summary")}</span>
            </button>`).join("") || `<div class="tool-item">No tasks yet.</div>`;
        $$("#task-list button", root).forEach(button => button.addEventListener("click", () => editTask(button.dataset.id)));
    }

    async function editTask(id) {
        currentTask = await jsonFetch(`/api/tasks/${encodeURIComponent(id)}`);
        $("#task-title", root).value = currentTask.title || "";
        $("#task-summary", root).value = currentTask.summary || "";
        $("#task-goal", root).value = currentTask.goal || "";
        $("#task-steps", root).value = (currentTask.steps || []).map(step => step.text).join("\n");
        $("#task-validation", root).value = (currentTask.validationCriteria || []).join("\n");
        renderFields("input", currentTask.inputs);
        renderFields("output", currentTask.outputs);
        renderRunForm();
    }

    function payload() {
        return {
            id: currentTask && currentTask.id,
            title: $("#task-title", root).value,
            summary: $("#task-summary", root).value,
            goal: $("#task-goal", root).value,
            inputs: readFields("input"),
            outputs: readFields("output"),
            steps: lines("task-steps").map((text, index) => ({ order: index + 1, text })),
            validationCriteria: lines("task-validation")
        };
    }

    $("[data-tool-action='new-task']", root).addEventListener("click", () => {
        currentTask = null;
        $("#task-title", root).value = "";
        $("#task-summary", root).value = "";
        $("#task-goal", root).value = "";
        $("#task-steps", root).value = "";
        $("#task-validation", root).value = "";
        renderFields("input", []);
        renderFields("output", []);
        renderRunForm();
    });
    $("[data-tool-action='add-task-input']", root).addEventListener("click", () => {
        $("#task-inputs", root).insertAdjacentHTML("beforeend", fieldRow("input"));
        renderRunForm();
    });
    $("[data-tool-action='add-task-output']", root).addEventListener("click", () => {
        $("#task-outputs", root).insertAdjacentHTML("beforeend", fieldRow("output"));
    });
    $("[data-tool-action='save-task']", root).addEventListener("click", async () => {
        const body = payload();
        const url = body.id ? `/api/tasks/${encodeURIComponent(body.id)}` : "/api/tasks";
        const method = body.id ? "PUT" : "POST";
        currentTask = await jsonFetch(url, { method, body: JSON.stringify(body) });
        await loadTasks();
        renderRunForm();
    });
    $("[data-tool-action='run-task']", root).addEventListener("click", async () => {
        if (!currentTask) return;
        const inputValues = {};
        $$("[data-input]", root).forEach(input => inputValues[input.dataset.input] = input.value);
        log.textContent = "";
        try {
            const response = await fetch(`/api/tasks/${encodeURIComponent(currentTask.id)}/runs/stream`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    inputValues,
                    agentId: $("#task-run-agent-id", root).value || null,
                    jobId: $("#task-run-job-id", root).value || null,
                    modelOverride: $("#task-run-model", root).value || null,
                    priority: Number($("#task-run-priority", root).value || 0)
                })
            });
            const events = await consumeSse(response);
            log.textContent = events.map(event => `${event.event}: ${JSON.stringify(event.data)}`).join("\n");
        } catch (error) {
            renderError(log, error);
        }
    });

    renderFields("input", []);
    renderFields("output", []);
    await loadTasks();
}

function workflowStepRow(tasks, step = {}, index = 0) {
    const options = tasks.map(task => {
        const selected = task.id === step.taskId ? "selected" : "";
        return `<option value="${escapeHtml(task.id)}" ${selected}>${escapeHtml(task.title)}</option>`;
    }).join("");
    return `
        <div class="workflow-step field-row">
            <input placeholder="step key" value="${escapeHtml(step.stepKey || `step_${index + 1}`)}">
            <select>${options}</select>
            <textarea placeholder="Bindings JSON">${escapeHtml(JSON.stringify(step.inputBindings || []))}</textarea>
        </div>`;
}

async function initWorkflows(root) {
    let workflowId = null;
    let tasks = [];
    const log = $("#workflow-run-log", root);

    async function loadTasks() {
        tasks = await jsonFetch("/api/tasks");
    }

    async function loadWorkflows() {
        const workflows = await jsonFetch("/api/workflows");
        $("#workflow-list", root).innerHTML = workflows.map(workflow => `
            <button class="tool-item" data-id="${escapeHtml(workflow.id)}">
                <strong>${escapeHtml(workflow.title)}</strong><br>
                <span>${escapeHtml(workflow.summary || "No summary")}</span>
            </button>`).join("") || `<div class="tool-item">No workflows yet.</div>`;
        $$("#workflow-list button", root).forEach(button => button.addEventListener("click", () => editWorkflow(button.dataset.id)));
    }

    function addStep(step = {}) {
        const count = $$(".workflow-step", root).length;
        $("#workflow-steps", root).insertAdjacentHTML("beforeend", workflowStepRow(tasks, step, count));
    }

    async function editWorkflow(id) {
        const workflow = await jsonFetch(`/api/workflows/${encodeURIComponent(id)}`);
        workflowId = workflow.id;
        $("#workflow-title", root).value = workflow.title || "";
        $("#workflow-summary", root).value = workflow.summary || "";
        $("#workflow-steps", root).innerHTML = "";
        (workflow.steps || []).forEach(addStep);
        const warnings = await jsonFetch(`/api/workflows/${encodeURIComponent(id)}/warnings`);
        $("#workflow-warnings", root).textContent = warnings.join("\n");
    }

    function payload() {
        return {
            id: workflowId,
            title: $("#workflow-title", root).value,
            summary: $("#workflow-summary", root).value,
            steps: $$(".workflow-step", root).map(row => ({
                stepKey: row.children[0].value,
                taskId: row.children[1].value,
                inputBindings: JSON.parse(row.children[2].value || "[]")
            }))
        };
    }

    $("[data-tool-action='new-workflow']", root).addEventListener("click", () => {
        workflowId = null;
        $("#workflow-title", root).value = "";
        $("#workflow-summary", root).value = "";
        $("#workflow-steps", root).innerHTML = "";
        $("#workflow-warnings", root).textContent = "";
        addStep();
        addStep();
    });
    $("[data-tool-action='add-workflow-step']", root).addEventListener("click", () => addStep());
    $("[data-tool-action='save-workflow']", root).addEventListener("click", async () => {
        const body = payload();
        const url = body.id ? `/api/workflows/${encodeURIComponent(body.id)}` : "/api/workflows";
        const method = body.id ? "PUT" : "POST";
        const saved = await jsonFetch(url, { method, body: JSON.stringify(body) });
        workflowId = saved.id;
        await loadWorkflows();
        await editWorkflow(workflowId);
    });
    $("[data-tool-action='run-workflow']", root).addEventListener("click", async () => {
        if (!workflowId) return;
        log.textContent = "";
        try {
            const response = await fetch(`/api/workflows/${encodeURIComponent(workflowId)}/runs/stream`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    agentId: $("#workflow-run-agent-id", root).value || null,
                    jobId: $("#workflow-run-job-id", root).value || null,
                    modelOverride: $("#workflow-run-model", root).value || null,
                    priority: Number($("#workflow-run-priority", root).value || 0)
                })
            });
            const events = await consumeSse(response);
            log.textContent = events.map(event => `${event.event}: ${JSON.stringify(event.data)}`).join("\n");
        } catch (error) {
            renderError(log, error);
        }
    });

    await loadTasks();
    addStep();
    addStep();
    await loadWorkflows();
}
