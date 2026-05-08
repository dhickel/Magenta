# Frontend Refactor Specification: SimplyPages & HTMX Alignment

## 1. Executive Summary
The current Magenta2 frontend is a "legacy" implementation created by an agent lacking access to the `SimplyPages` documentation. It relies on a "fat client" architecture (58KB JS for chat, 15KB for orchestration) and 1000+ lines of raw HTML strings embedded in Java controllers.

This specification defines a complete transition to a **Server-Side Rendering (SSR) + HTMX Fragment** architecture, leveraging the full modularity and streaming capabilities of the `SimplyPages` library.

---

## 2. Core Architectural Principles

### 2.1 SSR-First (The Mental Model)
- **Eliminate JSON-to-DOM logic**: The client should never receive raw JSON for rendering. It receives HTML fragments.
- **Idempotent Modules**: Use `Module.buildContent()` for static structure and `Template` + `SlotKey` for request-time dynamic data.
- **Thin Controllers**: Controllers should only handle routing, domain service calls, and rendering `Component` or `Template` objects. No raw HTML strings.

### 2.2 HTMX Integration
- **Declarative Actions**: Use `hx-get`, `hx-post`, `hx-delete` with fluent Java methods (e.g., `.withHxPost()`).
- **Fragment Swapping**: Target specific IDs (e.g., `#agent-cards`) instead of full-page reloads.
- **SSE Refresh Pattern**:
    1.  User interaction (HTMX POST).
    2.  Server updates state and emits lightweight SSE event (e.g., `chat-updated`).
    3.  Client (via lightweight `chat-demo.js` pattern) triggers HTMX GET to fetch the updated fragment.

---

## 3. Target: The Chat Interface (`/chat`)

### 3.1 Components & Modules
- **`ChatModule`**: Replace the 500-line `chatInterface` string with `io.mindspice.simplypages.modules.ChatModule`.
- **`MagentaChatMessageComponent`**: Create a custom implementation of `ChatMessageComponent` to handle Magenta-specific message features.

```java
// Example of how to model the Magenta message component
public class MagentaChatMessageComponent extends DefaultChatMessageComponent {
    @Override
    public void buildContent(ChatMessageData data) {
        if (data instanceof MagentaMessage magenta) {
            if (magenta.hasThinking()) {
                withChild(new HtmlTag("details").withClass("chat-thinking")
                    .withChild(new HtmlTag("summary").withText("Show Thinking"))
                    .withChild(new Div().withClass("chat-thinking-body")
                        .withChild(new Markdown(magenta.thinking()))));
            }
            if (magenta.isTool()) {
                withChild(new ToolActivityComponent(magenta.toolActivity()));
            } else {
                withChild(new Div().withClass("chat-message-body")
                    .withChild(new Markdown(magenta.body())));
            }
        }
    }
}
```

- **`ChatTranscriptRenderer`**: Specialized to handle `ChatMessage` records.

### 3.2 SSE & Transport
- **Deprecate `readSse`**: The complex client-side SSE parser in `chat-client.js` is unnecessary.
- **SSE Refresh Pattern**:
    1.  **Backend**: Emit `chat-updated` event after any state change (message received, tool finished).
    2.  **Controller**: Implement `@GetMapping("/api/fragments/chat/transcript")` that returns the rendered transcript.
    3.  **Frontend**: Use the `bindSse` helper to trigger `htmx.ajax('GET', ...)` on `chat-updated`.

```java
// Controller refresh pattern
@GetMapping("/api/fragments/chat/transcript")
public String getTranscript(@RequestParam String conversationId) {
    List<ChatMessage> history = chatService.history(conversationId);
    return magentaTranscriptRenderer.render(history).render();
}
```

---

## 4. Target: Orchestration Pages (`/agents`, `/jobs`, etc.)

### 4.1 UI Componentization
- **`AgentCardGrid`**: A module that renders a `CardGrid` of agent statuses.
- **`JobTable`**: Use the `Table` component for listing jobs with HTMX-driven pagination and filtering.
- **Eliminate Inline Scripts**: High-priority remediation for `/tasks` and `/workflows` in `FrontendController.java`, which currently contain 100+ line inline JS blocks for editor state management. These should be replaced by server-rendered SimplyPages `Form` components.

```java
// Example of a modularized Agent Card
public class AgentCard extends HtmlTag {
    public AgentCard(Agent agent) {
        super("article");
        withClass("orch-card");
        withChild(Header.H3(new Link(agent.name(), "/agents/" + agent.id())));
        withChild(new Badge(agent.status().toString()));
        // ... more children ...
        withChild(new Div().withClass("orch-actions")
            .withChild(new Button("Clone").withHxPost("/api/agents/" + agent.id() + "/clone"))
            .withChild(new Button("Disable").withHxDelete("/api/agents/" + agent.id())));
    }
}
```

### 3.3 Planning Mode Integration
- **The Planning Panel**: Replace the JS-rendered `#chat-planning-panel` with a SimplyPages `Module` or `Component`.
- **State-Driven Rendering**: The server should check the `ChatPlanState` for the conversation and render either the standard chat composer or the planning form (questions/approval).
- **Fragment Swapping**:
    - Use `hx-get="/api/fragments/chat/planning"` to fetch the current planning status.
    - Or, include the planning panel as a child of the `ChatModule` and refresh it via the `chat-updated` event.

```java
// Example Planning Form Component
public class PlanningFormComponent extends HtmlTag {
    public PlanningFormComponent(ChatPlanState state) {
        super("form");
        withId("planning-form");
        withHxPost("/api/chat/plan/answers");
        if (state.status() == ChatPlanStatus.READY_FOR_APPROVAL) {
             withChild(new Div().withText("Plan ready for approval"));
             withChild(new Button("Approve").withHxPatch("/api/chat/plan/approve"));
        }
        // ... handle questions ...
    }
}
```

---

## 5. JavaScript Remediation (The "Low Hanging Fruit")

| Current JS Logic | HTMX Replacement |
| :--- | :--- |
| `renderHistory(history)` | `ChatTranscriptRenderer.render(history)` (Server-side) |
| `loadSessions()` + `renderSessions()` | `hx-get="/api/fragments/sessions"` |
| `sendMessage()` (manual fetch/SSE) | `hx-post="/api/chat/messages"` + `chat-updated` SSE trigger |
| `bulkAction(action)` | `hx-post="/api/chat/bulk"` with `hx-include` |
| `initSettings()` (manual populate) | Server-rendered `Form` component |
| `agentCard(agent)` (JS Template) | `AgentCardComponent` (Java) |

**Remaining JS Scope**:
- **Prism.js / Highlight.js**: Client-side code highlighting.
- **Scroll to Bottom**: Small helper to keep chat scrolled (see `chat-demo.js`).
- **Focus management**: Ensuring inputs are focused after swaps.

---

## 6. Senior Engineering Guide for Implementation

### 6.1 The "Grok" List
1.  **Mental Model**: Think in **Fragments**. If it moves, it's a fragment.
2.  **SlotKeys**: Use `public static final SlotKey<T>` for any data that varies per request (Conversation ID, User Name, Flash Messages).
3.  **Shell Templates**: Standardize the app shell. Every page should use `pageShell.renderWithContent(pageComponent)`.
4.  **Composition over Inheritance**: Build complex modules by composing existing components (`Row`, `Column`, `Card`, `Button`).

### 6.2 Implementation Phases
1.  **Phase 1: Foundation**:
    - Standardize `ShellTemplate` in `FrontendController`.
    - Create `Page` classes for each main route to move HTML out of the controller.
2.  **Phase 2: Orchestration Fragments**:
    - Implement `hx-get` fragments for `/agents` and `/jobs`.
    - Delete the corresponding sections of `orchestration/app.js`.
3.  **Phase 3: The Chat Module**:
    - Implement the `ChatModule` and `MagentaChatMessageComponent`.
    - Setup the SSE `chat-updated` hook.
    - Delete `chat-client.js`.
4.  **Phase 4: Validation**:
    - Run Playwright tests to ensure HTMX swaps and SSE events are working correctly.

### 6.3 framework Specifics
- **CSS**: Stop using raw `<style>` blocks. Use SimplyPages `Row`/`Column` for layout. Custom CSS goes into `static/css/magenta.css`.
- **Attributes**: Use `Component.withAttribute("hx-...", "...")` or the specialized `.withHx...()` methods.
### 6.4 Implementor Focus: The Distilled Essentials
- **No Manual DOM**: If you are writing `.innerHTML` or `document.createElement` in JS, you are doing it wrong. Use SimplyPages components in Java.
- **Fragments are Key**: Design your controller to return small HTML fragments when `HX-Request` is present.
- **SSE is a Trigger**: Use SSE to signal the client that it needs to fetch a new fragment, not to send the data for the fragment itself.
- **Leverage the Builders**: `ShellBuilder`, `TopNavBuilder`, and `BannerBuilder` should be the entry point for every page.
- **SlotKeys for Dynamic Data**: Use `SlotKey` to pass request-scoped data into templates.
