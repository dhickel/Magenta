(function () {
  const PAGE_SELECTOR = "[data-orchestration-page='workflows']";

  function byId(id) {
    return document.getElementById(id);
  }

  function asJsonText(value) {
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return "{}";
    }
  }

  function parseJsonOrNull(text) {
    if (!text || !text.trim()) return null;
    try {
      return JSON.parse(text);
    } catch {
      return null;
    }
  }

  function textValue(value) {
    return String(value ?? "");
  }

  function textElement(tagName, value, className) {
    const element = document.createElement(tagName);
    if (className) {
      element.className = className;
    }
    element.textContent = textValue(value);
    return element;
  }

  function labeledInput(labelText, inputId, value, disabled = false) {
    const label = document.createElement("label");
    label.appendChild(document.createTextNode(labelText));
    const input = document.createElement("input");
    input.id = inputId;
    input.value = textValue(value);
    input.disabled = disabled;
    label.appendChild(input);
    return label;
  }

  function labeledTextarea(labelText, textareaId, value) {
    const label = document.createElement("label");
    label.appendChild(document.createTextNode(labelText));
    const textarea = document.createElement("textarea");
    textarea.id = textareaId;
    textarea.value = textValue(value);
    label.appendChild(textarea);
    return label;
  }

  class WorkflowGraphComposer {
    constructor(page) {
      this.page = page;
      this.state = {
        workflow: null,
        selectedNodeKey: null,
        diagnostics: { errors: [], warnings: [] }
      };
      this.dragState = null;
      this.bootstrap();
    }

    async bootstrap() {
      this.mount();
      await this.loadLatestWorkflow();
      this.render();
    }

    mount() {
      const container = document.createElement("section");
      container.className = "orch-panel graph-composer";
      container.innerHTML = `
        <header class="graph-composer-header">
          <h2>Workflow V2 Graph Composer</h2>
          <div class="graph-actions">
            <button id="graph-load-latest" type="button">Load Latest</button>
            <button id="graph-new" type="button">New</button>
            <button id="graph-save" type="button">Save</button>
            <button id="graph-validate" type="button">Validate</button>
          </div>
        </header>
        <div class="graph-layout">
          <aside class="graph-palette">
            <h3>Node Palette</h3>
            <div class="graph-palette-buttons" id="graph-palette-buttons"></div>
            <h3>Connect</h3>
            <form id="graph-connect-form" class="graph-connect-form">
              <label>From<select id="graph-route-from"></select></label>
              <label>Source Port<input id="graph-route-source-port" /></label>
              <label>To<select id="graph-route-to"></select></label>
              <label>Target Port<input id="graph-route-target-port" /></label>
              <label>Type
                <select id="graph-route-type">
                  <option value="map_output">map_output</option>
                  <option value="pass_through">pass_through</option>
                  <option value="control">control</option>
                </select>
              </label>
              <label>Control Outcome (for control)
                <select id="graph-route-condition">
                  <option value="APPROVED">APPROVED</option>
                  <option value="REJECTED">REJECTED</option>
                </select>
              </label>
              <button type="submit">Add Route</button>
            </form>
          </aside>
          <div class="graph-canvas-wrap">
            <div class="graph-canvas" id="graph-canvas"></div>
          </div>
          <aside class="graph-side-panel" id="graph-side-panel"></aside>
        </div>
        <section class="graph-diagnostics" id="graph-diagnostics"></section>
      `;
      const host = byId("workflow-editor-container") || this.page;
      host.replaceChildren();
      host.appendChild(container);

      byId("graph-load-latest").addEventListener("click", () => this.loadLatestWorkflow().then(() => this.render()));
      byId("graph-new").addEventListener("click", () => {
        this.state.workflow = this.newWorkflowDraft();
        this.state.selectedNodeKey = null;
        this.state.diagnostics = { errors: [], warnings: [] };
        this.render();
      });
      byId("graph-save").addEventListener("click", () => this.saveWorkflow());
      byId("graph-validate").addEventListener("click", () => this.validateWorkflow());
      byId("graph-connect-form").addEventListener("submit", (e) => {
        e.preventDefault();
        this.addRouteFromForm();
      });

      const palette = ["task", "user_approval", "agent_approval", "validation", "fan_out", "final_output"];
      const paletteButtons = byId("graph-palette-buttons");
      palette.forEach((type) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "graph-palette-button";
        button.textContent = type;
        button.addEventListener("click", () => this.addNode(type));
        paletteButtons.appendChild(button);
      });
    }

    newWorkflowDraft() {
      return {
        id: null,
        schemaVersion: 2,
        title: "Untitled Workflow",
        summary: "",
        maxConcurrency: 4,
        nodes: [],
        routes: [],
        uiLayout: { nodes: {} }
      };
    }

    async loadLatestWorkflow() {
      const response = await fetch("/api/workflows");
      if (!response.ok) {
        this.state.workflow = this.newWorkflowDraft();
        return;
      }
      const list = await response.json();
      this.state.workflow = list && list.length ? list[0] : this.newWorkflowDraft();
      if (!this.state.workflow.uiLayout) {
        this.state.workflow.uiLayout = { nodes: {} };
      }
      if (!this.state.workflow.uiLayout.nodes) {
        this.state.workflow.uiLayout.nodes = {};
      }
      if (!this.state.workflow.schemaVersion) {
        this.state.workflow.schemaVersion = 2;
      }
      if (!this.state.workflow.maxConcurrency) {
        this.state.workflow.maxConcurrency = 4;
      }
    }

    addNode(type) {
      const wf = this.state.workflow;
      if (!wf) return;
      const nodeKey = `${type}_${Math.random().toString(36).slice(2, 8)}`;
      const x = 40 + (wf.nodes.length % 4) * 180;
      const y = 40 + Math.floor(wf.nodes.length / 4) * 140;
      wf.nodes.push({
        key: nodeKey,
        type,
        planId: null,
        label: nodeKey,
        inputName: null,
        inputPorts: [],
        outputPorts: [],
        config: {},
        parallel: false,
        inputBindings: [],
        messageTemplate: null,
        resumePolicy: null
      });
      wf.uiLayout.nodes[nodeKey] = { x, y };
      this.state.selectedNodeKey = nodeKey;
      this.render();
    }

    addRouteFromForm() {
      const wf = this.state.workflow;
      if (!wf) return;
      const fromNodeKey = byId("graph-route-from").value;
      const toNodeKey = byId("graph-route-to").value;
      const routeType = byId("graph-route-type").value;
      const fromOutputName = byId("graph-route-source-port").value.trim() || null;
      const toInputName = byId("graph-route-target-port").value.trim() || null;
      const condition = routeType === "control" ? byId("graph-route-condition").value : null;

      const inlineError = this.inlineRouteError(fromNodeKey, toNodeKey, routeType, fromOutputName, toInputName, condition);
      if (inlineError) {
        this.state.diagnostics = { errors: [inlineError], warnings: [] };
        this.renderDiagnostics();
        return;
      }

      wf.routes.push({
        id: `route_${Math.random().toString(36).slice(2, 10)}`,
        fromNodeKey,
        fromOutputName,
        toNodeKey,
        toInputName,
        routeType,
        condition
      });
      this.state.diagnostics = { errors: [], warnings: [] };
      this.render();
    }

    inlineRouteError(fromNodeKey, toNodeKey, routeType, fromOutputName, toInputName, condition) {
      if (!fromNodeKey || !toNodeKey) return "Route must include source and target nodes.";
      if (fromNodeKey === toNodeKey) return "Self-routes are not allowed.";
      const exists = this.state.workflow.routes.some((r) =>
        r.fromNodeKey === fromNodeKey
        && r.toNodeKey === toNodeKey
        && r.fromOutputName === fromOutputName
        && r.toInputName === toInputName
        && r.routeType === routeType
        && r.condition === condition);
      if (exists) return "Duplicate route is not allowed.";
      if (routeType === "control") {
        if (fromOutputName || toInputName) return "Control routes may not carry data ports.";
        if (condition !== "APPROVED" && condition !== "REJECTED") return "Control routes require APPROVED or REJECTED condition.";
      } else {
        if (!fromOutputName || !toInputName) return "Data routes require source and target ports.";
      }
      if (this.createsCyclePreview(fromNodeKey, toNodeKey)) {
        return "Route would create a cycle.";
      }
      return null;
    }

    createsCyclePreview(fromNodeKey, toNodeKey) {
      const edges = this.state.workflow.routes.map((r) => [r.fromNodeKey, r.toNodeKey]);
      edges.push([fromNodeKey, toNodeKey]);
      const visited = new Set();
      const stack = new Set();
      const walk = (node) => {
        if (stack.has(node)) return true;
        if (visited.has(node)) return false;
        visited.add(node);
        stack.add(node);
        const next = edges.filter((e) => e[0] === node).map((e) => e[1]);
        for (const n of next) {
          if (walk(n)) return true;
        }
        stack.delete(node);
        return false;
      };
      for (const node of this.state.workflow.nodes.map((n) => n.key)) {
        if (walk(node)) return true;
      }
      return false;
    }

    selectNode(nodeKey) {
      this.state.selectedNodeKey = nodeKey;
      this.renderSidePanel();
      this.renderCanvas();
    }

    removeNode(nodeKey) {
      const wf = this.state.workflow;
      wf.nodes = wf.nodes.filter((n) => n.key !== nodeKey);
      wf.routes = wf.routes.filter((r) => r.fromNodeKey !== nodeKey && r.toNodeKey !== nodeKey);
      delete wf.uiLayout.nodes[nodeKey];
      if (this.state.selectedNodeKey === nodeKey) {
        this.state.selectedNodeKey = null;
      }
      this.render();
    }

    updateSelectedNode(updater) {
      const wf = this.state.workflow;
      const idx = wf.nodes.findIndex((n) => n.key === this.state.selectedNodeKey);
      if (idx < 0) return;
      wf.nodes[idx] = updater(wf.nodes[idx]);
      this.renderCanvas();
      this.renderSidePanel();
    }

    render() {
      this.renderRouteSelectors();
      this.renderCanvas();
      this.renderSidePanel();
      this.renderDiagnostics();
    }

    renderRouteSelectors() {
      const wf = this.state.workflow;
      const from = byId("graph-route-from");
      const to = byId("graph-route-to");
      from.replaceChildren();
      to.replaceChildren();
      wf.nodes.forEach((node) => {
        const opt1 = document.createElement("option");
        opt1.value = node.key;
        opt1.textContent = `${node.key} (${node.type})`;
        from.appendChild(opt1);

        const opt2 = document.createElement("option");
        opt2.value = node.key;
        opt2.textContent = `${node.key} (${node.type})`;
        to.appendChild(opt2);
      });
    }

    renderCanvas() {
      const canvas = byId("graph-canvas");
      const wf = this.state.workflow;

      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.setAttribute("class", "graph-edges");
      canvas.replaceChildren(svg);

      wf.routes.forEach((route) => {
        const fromPos = wf.uiLayout.nodes[route.fromNodeKey] || { x: 0, y: 0 };
        const toPos = wf.uiLayout.nodes[route.toNodeKey] || { x: 0, y: 0 };
        const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
        line.setAttribute("x1", String(fromPos.x + 150));
        line.setAttribute("y1", String(fromPos.y + 30));
        line.setAttribute("x2", String(toPos.x));
        line.setAttribute("y2", String(toPos.y + 30));
        line.setAttribute("class", `graph-edge graph-edge-${route.routeType}`);
        line.addEventListener("click", () => {
          this.state.workflow.routes = this.state.workflow.routes.filter((r) => r.id !== route.id);
          this.render();
        });
        svg.appendChild(line);
      });

      wf.nodes.forEach((node) => {
        const pos = wf.uiLayout.nodes[node.key] || { x: 20, y: 20 };
        const card = document.createElement("article");
        card.className = "graph-node-card" + (this.state.selectedNodeKey === node.key ? " selected" : "");
        card.style.left = `${pos.x}px`;
        card.style.top = `${pos.y}px`;
        const header = document.createElement("header");
        header.appendChild(textElement("strong", node.label || node.key));
        card.append(
          header,
          textElement("div", node.type),
          textElement("div", node.key, "graph-node-key")
        );
        card.addEventListener("click", () => this.selectNode(node.key));
        card.addEventListener("pointerdown", (event) => this.beginDrag(node.key, event));
        canvas.appendChild(card);
      });
    }

    beginDrag(nodeKey, event) {
      const wf = this.state.workflow;
      const pos = wf.uiLayout.nodes[nodeKey] || { x: 0, y: 0 };
      this.dragState = {
        nodeKey,
        startX: event.clientX,
        startY: event.clientY,
        originX: pos.x,
        originY: pos.y
      };
      const move = (e) => {
        if (!this.dragState) return;
        const dx = e.clientX - this.dragState.startX;
        const dy = e.clientY - this.dragState.startY;
        wf.uiLayout.nodes[nodeKey] = {
          x: Math.max(0, this.dragState.originX + dx),
          y: Math.max(0, this.dragState.originY + dy)
        };
        this.renderCanvas();
      };
      const up = () => {
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", up);
        this.dragState = null;
      };
      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", up);
    }

    renderSidePanel() {
      const panel = byId("graph-side-panel");
      const wf = this.state.workflow;
      const node = wf.nodes.find((n) => n.key === this.state.selectedNodeKey);
      if (!node) {
        panel.replaceChildren(
          textElement("h3", "Node Config"),
          textElement("p", "Select a node on the canvas.")
        );
        return;
      }

      const actions = document.createElement("div");
      actions.className = "graph-side-actions";
      const apply = document.createElement("button");
      apply.id = "node-apply";
      apply.type = "button";
      apply.textContent = "Apply";
      const del = document.createElement("button");
      del.id = "node-delete";
      del.type = "button";
      del.textContent = "Delete";
      actions.append(apply, del);

      panel.replaceChildren(
        textElement("h3", "Node Config"),
        labeledInput("Key", "node-key", node.key),
        labeledInput("Label", "node-label", node.label || ""),
        labeledInput("Type", "node-type", node.type, true),
        labeledInput("Task Plan ID", "node-plan-id", node.planId || ""),
        labeledTextarea("Message Template", "node-message", node.messageTemplate || ""),
        labeledTextarea("Config JSON", "node-config", asJsonText(node.config || {})),
        labeledTextarea("Input Ports JSON", "node-input-ports", asJsonText(node.inputPorts || [])),
        labeledTextarea("Output Ports JSON", "node-output-ports", asJsonText(node.outputPorts || [])),
        actions
      );

      byId("node-apply").addEventListener("click", () => {
        this.updateSelectedNode((current) => {
          const key = byId("node-key").value.trim() || current.key;
          const config = parseJsonOrNull(byId("node-config").value) || {};
          const inputPorts = parseJsonOrNull(byId("node-input-ports").value) || [];
          const outputPorts = parseJsonOrNull(byId("node-output-ports").value) || [];
          if (key !== current.key) {
            this.renameNodeKey(current.key, key);
          }
          return {
            ...current,
            key,
            label: byId("node-label").value,
            planId: byId("node-plan-id").value.trim() || null,
            messageTemplate: byId("node-message").value.trim() || null,
            config,
            inputPorts,
            outputPorts
          };
        });
      });
      byId("node-delete").addEventListener("click", () => this.removeNode(node.key));
    }

    renameNodeKey(oldKey, newKey) {
      const wf = this.state.workflow;
      if (oldKey === newKey) return;
      if (wf.nodes.some((n) => n.key === newKey)) {
        this.state.diagnostics = { errors: [`Node key '${newKey}' already exists.`], warnings: [] };
        this.renderDiagnostics();
        return;
      }
      wf.routes = wf.routes.map((r) => ({
        ...r,
        fromNodeKey: r.fromNodeKey === oldKey ? newKey : r.fromNodeKey,
        toNodeKey: r.toNodeKey === oldKey ? newKey : r.toNodeKey
      }));
      wf.uiLayout.nodes[newKey] = wf.uiLayout.nodes[oldKey] || { x: 20, y: 20 };
      delete wf.uiLayout.nodes[oldKey];
      this.state.selectedNodeKey = newKey;
    }

    renderDiagnostics() {
      const panel = byId("graph-diagnostics");
      const { errors, warnings } = this.state.diagnostics;
      panel.replaceChildren(textElement("h3", "Compile / Validate"));
      if (!errors.length && !warnings.length) {
        panel.appendChild(textElement("p", "No diagnostics."));
        return;
      }
      const makeList = (title, items, cls) => {
        if (!items.length) return;
        const wrap = document.createElement("div");
        wrap.className = `graph-diag-group ${cls}`;
        wrap.appendChild(textElement("h4", title));
        const ul = document.createElement("ul");
        items.forEach((item) => {
          const li = document.createElement("li");
          li.textContent = item;
          const match = this.state.workflow.nodes.find((n) => item.includes(`'${n.key}'`));
          if (match) {
            li.classList.add("clickable");
            li.addEventListener("click", () => this.selectNode(match.key));
          }
          ul.appendChild(li);
        });
        wrap.appendChild(ul);
        panel.appendChild(wrap);
      };
      makeList("Errors", errors, "error");
      makeList("Warnings", warnings, "warning");
    }

    async validateWorkflow() {
      if (!this.state.workflow) return;
      const wf = this.state.workflow;
      const url = wf.id ? `/api/workflows/${wf.id}/validate` : "/api/workflows/validate";
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(wf)
      });
      if (!response.ok) {
        this.state.diagnostics = { errors: ["Validation request failed"], warnings: [] };
        this.renderDiagnostics();
        return;
      }
      this.state.diagnostics = await response.json();
      this.renderDiagnostics();
    }

    async saveWorkflow() {
      if (!this.state.workflow) return;
      await this.validateWorkflow();
      if (this.state.diagnostics.errors.length) {
        return;
      }

      const wf = this.state.workflow;
      const url = wf.id ? `/api/workflows/${wf.id}` : "/api/workflows";
      const method = wf.id ? "PUT" : "POST";
      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(wf)
      });
      if (!response.ok) {
        const text = await response.text();
        this.state.diagnostics = { errors: [text || "Save failed"], warnings: [] };
        this.renderDiagnostics();
        return;
      }
      this.state.workflow = await response.json();
      this.state.diagnostics = { errors: [], warnings: [] };
      this.render();
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector(PAGE_SELECTOR);
    if (!page) {
      return;
    }
    new WorkflowGraphComposer(page);
  });
})();
