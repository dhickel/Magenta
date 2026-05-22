# Avatar Simple DSL Research Review

## Scope

Research-only follow-up for the Avatar plugin-system track. This document evaluates mock DSL shapes for future Avatar widget and panel specs, with emphasis on formats that are simple to parse, validate, and map into SimplyPages/HTMX rendering.

This does not implement runtime behavior, parser code, routes, plugin loading, Kawa integration, or production UI. It extends the prior plugin-system research in `.internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md`, which already concluded that plugin runtime remains deferred and that Kawa is trusted-local only unless a real sandbox exists.

Source inputs reviewed:

- `.internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md`
- `.internal-dev/plans/avatar-dashboard-sprint/README.md`
- `.internal-dev/plans/avatar-dashboard-sprint/phase-04-avatar-assistant-behaviors.md`
- `.internal-dev/plans/avatar-dashboard-sprint/phase-05-avatar-dashboard-ui.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarDashboardWidget.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `docs/technical/frontend-htmx.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/01-components-htmltag-and-module-lifecycle.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/security/01-security-boundaries-and-safe-rendering.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/components-and-modules-catalog.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/pages/HtmxEditingDemoPage.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
- GNU Kawa docs: https://www.gnu.org/software/kawa/index.html and https://www.gnu.org/software/kawa/Method-operations.html
- Java `ServiceLoader` docs: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html

## Findings

### Current Avatar Context

Avatar now has a first-party persistence boundary under `avatar.sqlite`. The current model already includes profile, preferences, dashboard layout, todos, daily tasks, calendar items, notes, facts, and events. `AvatarDashboardWidget` stores `widgetId`, position, size, enabled/collapsed state, and `settings`, which is enough to support first-party widget layout before any plugin system exists.

There is no production `/avatar` dashboard route or plugin-powered widget runtime in the current source context. The sprint plan says `/avatar` should be a SimplyPages/HTMX-first page with stable widget IDs and fragment routes, and it explicitly excludes plugin-powered widgets during the sprint.

The architecture focus and decisions also keep Avatar on existing chat/tool/runtime services. A future UI DSL should therefore describe renderable panels and widget contracts, not define a second runtime, new tool registry, or arbitrary execution surface.

### SimplyPages Constraints

SimplyPages favors server-side component/module composition. Its docs call out these constraints that matter for a DSL:

- `HtmlTag`, `Module`, and `RenderContext` are mutable during construction and should not be shared across concurrent requests.
- Stable reusable structures should be compiled as `Template` and fed per-request values through `RenderContext`/`SlotKey`.
- Text, attributes, and slot output are escaped by default.
- `withUnsafeHtml(...)` is explicitly dangerous and should not receive raw user input.
- HTMX endpoints should own stable target IDs and return user-visible fragments for errors.
- Standard form submissions, widget refreshes, modal bodies, and out-of-band multi-target updates are natural HTMX flows.

A future DSL should compile to host-owned component construction, not directly to raw HTML. The DSL should describe "what panel exists", "what data source it displays", and "which host endpoint/action it can call"; Magenta should remain responsible for rendering, escaping, authorization, CSRF, and persistence.

### Mock Target Model

All candidate DSLs should parse into one internal model before validation and rendering. The format should be replaceable without changing the host rendering and security model.

```java
record AvatarUiSpec(
    String id,
    String version,
    List<PanelSpec> panels,
    List<ActionSpec> actions,
    PermissionsSpec permissions
) {}

record PanelSpec(
    String id,
    String title,
    String target,
    String route,
    List<WidgetSpec> widgets,
    List<FormSpec> forms,
    RefreshSpec refresh
) {}

record WidgetSpec(
    String id,
    String type,
    String title,
    String source,
    String size,
    Map<String, Object> settings
) {}

record FormSpec(
    String id,
    String title,
    String method,
    String action,
    String target,
    String swap,
    List<FieldSpec> fields
) {}
```

The model is intentionally declarative. It has no Java class names, no Spring bean references, no arbitrary HTML, no tool definitions, and no executable expressions.

## DSL Approaches Compared

| Approach | Parse simplicity | Authoring experience | Safety profile | SimplyPages/HTMX mapping | Main risk |
| --- | --- | --- | --- | --- | --- |
| YAML/JSON declarative | Best with existing Jackson/YAML dependency and records | Familiar, good comments in YAML, easy schema docs | Good if polymorphic typing and arbitrary HTML are disallowed | Direct map to spec records and component builders | YAML indentation, anchors/aliases, and weak line-aware semantic errors if not wrapped carefully |
| S-expression/parenthesized | Very simple custom lexer/parser; balanced parens catch many errors | Compact and close to the user's parens idea, but niche | Good if parsed as data only, never evaluated | Direct AST to spec records | Bad UX for non-Lisp users; malformed nesting can be noisy without excellent diagnostics |
| Line-oriented command DSL | Simple tokenizer and line parser; no nested tree parser required | Friendly for short widget specs and edits | Good if command allowlist is strict | Commands append panels/widgets/forms to the spec model | Nested UI gets awkward; quoting and multiline text need rules |
| Java/Kawa host API builder | Java has no custom parser; Kawa has its own language parser | Best for trusted developers, bad for non-developers | Java/Kawa code is trusted code with host privileges | Builder can directly produce spec records or components | Becomes plugin runtime/code execution, not a safe data DSL |

### Approach 1: YAML/JSON Declarative

This is the lowest-friction first implementation if a parser is ever needed. The repo already uses Jackson and includes `jackson-dataformat-yaml`; `ExternalAiConfigLoader` already loads JSON/YAML config through `ObjectMapper` and `YAMLFactory`.

Good fit:

- Plugin or widget manifests checked into local config.
- Specs that need nested panels, widgets, fields, and HTMX action contracts.
- Future schema validation and documentation.
- JSON import/export if a UI editor later generates specs.

Weak points:

- YAML indentation mistakes are common.
- YAML has features such as anchors and aliases that are not needed here.
- Semantic validation still needs custom diagnostics. Jackson can tell where syntax failed, but unknown widget source, bad target ID, or unsafe route needs Magenta-owned error messages.

Mock grammar sketch:

```text
avatar-ui:
  id: IDENT
  version: SEMVER
  permissions?: Permissions
  panels: Panel+

Panel:
  id: IDENT
  title: STRING
  target: CSS_ID
  route?: LOCAL_ROUTE
  refresh?: Refresh
  widgets?: Widget*
  forms?: Form*

Widget:
  id: IDENT
  type: WIDGET_TYPE
  title?: STRING
  source?: DATA_SOURCE
  size?: small | medium | large | full
  settings?: OBJECT

Form:
  id: IDENT
  method: GET | POST | PUT | PATCH | DELETE
  action: LOCAL_ROUTE
  target: CSS_ID
  swap: innerHTML | outerHTML | none
  fields: Field*
```

Example Avatar notes/todos panel:

```yaml
id: avatar-organizer
version: 0.1.0
permissions:
  data:
    - avatar.todos.read
    - avatar.todos.write
    - avatar.notes.read
    - avatar.notes.write
panels:
  - id: daily-focus
    title: Daily Focus
    target: "#avatar-widget-daily-focus"
    route: "/avatar/_widgets/daily-focus"
    refresh:
      method: GET
      action: "/avatar/_widgets/daily-focus"
      target: "#avatar-widget-daily-focus"
      swap: outerHTML
    widgets:
      - id: today-tasks
        type: checklist
        title: Today
        source: avatar.dailyTasks.today
        size: medium
      - id: open-todos
        type: list
        title: Open Todos
        source: avatar.todos.open
        size: medium
    forms:
      - id: add-todo
        title: Add Todo
        method: POST
        action: "/avatar/todos"
        target: "#avatar-widget-daily-focus"
        swap: outerHTML
        fields:
          - name: title
            type: text
            required: true
            maxLength: 160
          - name: priority
            type: select
            options: [LOW, NORMAL, HIGH]
```

### Approach 2: S-Expression / Parenthesized DSL

This is the cleanest version of the "parennns()())?(.)" style idea from the email. The raw email shape is intentionally not a usable syntax because unmatched parentheses and punctuation would make diagnostics terrible. A constrained s-expression grammar keeps the spirit of simple parentheses while making parsing deterministic.

Important boundary: this must be parsed as data, not evaluated as Lisp, Scheme, or Kawa. Treat every list as an AST node. Do not expose arithmetic, symbols that resolve to Java classes, macros, imports, or function calls.

Good fit:

- A tiny in-repo parser with excellent line/column diagnostics.
- Specs generated or edited by power users.
- A future "simple parser experiment" without adding ANTLR or a scripting runtime.

Weak points:

- Less familiar than YAML.
- Nested close parens are visually dense.
- Requires custom formatter or canonical printer if users will edit it often.

Lexer sketch:

```text
token := LPAREN | RPAREN | STRING | SYMBOL | NUMBER | BOOLEAN | COMMENT
LPAREN := "("
RPAREN := ")"
STRING := '"' escaped-char* '"'
SYMBOL := [A-Za-z_][A-Za-z0-9_.:/#-]*
COMMENT := ";" not-newline*
```

Grammar sketch:

```text
document     := plugin
plugin       := "(" "avatar-ui" string form* ")"
form         := permission | panel | action
permission   := "(" "allow" capability+ ")"
panel        := "(" "panel" ident attr* panel-child* ")"
panel-child  := widget | form-spec | refresh
widget       := "(" "widget" ident attr* ")"
form-spec    := "(" "form" ident attr* field* ")"
field        := "(" "field" ident field-type attr* ")"
refresh      := "(" "refresh" attr* ")"
action       := "(" "action" ident attr* ")"
attr         := "(" ident value ")"
value        := string | symbol | number | boolean | list
list         := "(" value* ")"
```

Example Avatar panel:

```scheme
(avatar-ui "avatar-organizer"
  (allow avatar.dailyTasks.read avatar.todos.read avatar.todos.write)

  (panel daily-focus
    (title "Daily Focus")
    (target "#avatar-widget-daily-focus")
    (route "/avatar/_widgets/daily-focus")

    (refresh
      (method GET)
      (action "/avatar/_widgets/daily-focus")
      (target "#avatar-widget-daily-focus")
      (swap outerHTML))

    (widget today-tasks
      (type checklist)
      (title "Today")
      (source avatar.dailyTasks.today)
      (size medium))

    (widget open-todos
      (type list)
      (title "Open Todos")
      (source avatar.todos.open)
      (size medium))

    (form add-todo
      (title "Add Todo")
      (method POST)
      (action "/avatar/todos")
      (target "#avatar-widget-daily-focus")
      (swap outerHTML)
      (field title text (required true) (maxLength 160))
      (field priority select (options (LOW NORMAL HIGH))))))
```

Parsing strategy:

1. Lex to tokens with line, column, and absolute offset.
2. Parse balanced lists into a generic `SNode`.
3. Convert `SNode` to `AvatarUiSpec` with a strict node-name allowlist.
4. Run semantic validation over the resulting spec.
5. Return all semantic diagnostics in one pass when possible.

Do not use Kawa's reader for this safe DSL. The Kawa docs emphasize Java-platform integration and Java method invocation from Scheme, which is useful for trusted host scripting but is the wrong trust boundary for an untrusted or semi-trusted UI manifest.

### Approach 3: Line-Oriented Command DSL

A line-oriented DSL can be simpler than YAML and less visually dense than s-expressions. It is closest to a CLI command log or router config: one operation per line, with indentation optional and `end` markers closing nested blocks.

Good fit:

- Short widget packs.
- Human-readable diffs.
- Fast custom parser with precise "line N command X" errors.

Weak points:

- Deep nesting is clumsy.
- Multi-line Markdown/help text requires here-doc or escaped strings.
- Users must learn command ordering.

Grammar sketch:

```text
document   := statement*
statement  := plugin | allow | panel | widget | form | field | refresh | end
plugin     := "plugin" IDENT STRING?
allow      := "allow" CAPABILITY+
panel      := "panel" IDENT kv*
widget     := "widget" IDENT kv*
form       := "form" IDENT kv*
field      := "field" IDENT FIELD_TYPE kv*
refresh    := "refresh" kv*
end        := "end" IDENT?
kv         := IDENT "=" (STRING | ATOM)
comment    := "#" not-newline*
```

Example Avatar calendar/todos panel:

```text
plugin avatar-organizer "Avatar Organizer"
allow avatar.calendar.read avatar.todos.read avatar.todos.write

panel daily-focus title="Daily Focus" target=#avatar-widget-daily-focus route=/avatar/_widgets/daily-focus
  refresh method=GET action=/avatar/_widgets/daily-focus target=#avatar-widget-daily-focus swap=outerHTML
  widget today-tasks type=checklist title="Today" source=avatar.dailyTasks.today size=medium
  widget open-todos type=list title="Open Todos" source=avatar.todos.open size=medium
  form add-todo title="Add Todo" method=POST action=/avatar/todos target=#avatar-widget-daily-focus swap=outerHTML
    field title text required=true maxLength=160
    field priority select options=LOW,NORMAL,HIGH
  end form
end panel
```

Parsing strategy:

1. Strip comments and blank lines.
2. Tokenize each line into command, positional arguments, and key-value pairs.
3. Maintain a small stack of open blocks: plugin, panel, form.
4. Reject commands in the wrong parent block.
5. Convert directly to `AvatarUiSpec`.

This is easy to parse and diagnose, but it is less future-proof for complex UI trees than YAML or s-expressions.

### Approach 4: Java/Kawa Host API Builder

This is not a safe user-authored data DSL. It is a trusted developer extension style.

Java builder shape:

```java
AvatarUiSpec spec = AvatarUi.plugin("avatar-organizer")
    .version("0.1.0")
    .allow("avatar.dailyTasks.read", "avatar.todos.read", "avatar.todos.write")
    .panel("daily-focus", panel -> panel
        .title("Daily Focus")
        .target("#avatar-widget-daily-focus")
        .route("/avatar/_widgets/daily-focus")
        .widget("today-tasks", widget -> widget
            .type("checklist")
            .title("Today")
            .source("avatar.dailyTasks.today")
            .size("medium"))
        .form("add-todo", form -> form
            .title("Add Todo")
            .post("/avatar/todos")
            .target("#avatar-widget-daily-focus")
            .swap("outerHTML")
            .field("title", "text", field -> field.required().maxLength(160))))
    .build();
```

Kawa-flavored trusted builder shape:

```scheme
(avatar-ui "avatar-organizer"
  (version "0.1.0")
  (allow "avatar.dailyTasks.read" "avatar.todos.write")
  (panel "daily-focus"
    (title "Daily Focus")
    (target "#avatar-widget-daily-focus")
    (route "/avatar/_widgets/daily-focus")
    (widget "today-tasks"
      (type "checklist")
      (source "avatar.dailyTasks.today"))
    (form "add-todo"
      (post "/avatar/todos")
      (target "#avatar-widget-daily-focus")
      (field "title" "text" required: #t maxLength: 160))))
```

Good fit:

- Trusted in-repo or local developer plugins.
- Compile-time Java tests for spec generation.
- Future adapter layer behind a Java SPI `AvatarPlugin`.

Weak points:

- Java is too verbose for a simple user-facing DSL.
- Kawa can call Java methods and interact deeply with the Java platform. That is useful for trusted local scripts, but unsafe as an in-process sandbox.
- This crosses from "manifest parser" into "plugin runtime", which is out of scope for the Avatar sprint.

## Parsing Strategy

Recommended later implementation sequence if this work is resumed:

1. Define the internal spec records first.
   - Keep the model declarative.
   - Include `sourceLocation` metadata at each node if custom parsers are used.
   - Keep route/action/source fields as strings until semantic validation.

2. Implement YAML/JSON first if a production parser is needed.
   - Reuse Jackson/YAML already present in the repo.
   - Disable or avoid polymorphic type handling.
   - Bind to records or DTOs with unknown-property rejection.
   - Convert to immutable validated records.

3. Prototype s-expression parsing only as a focused research/test artifact.
   - A 100-200 line lexer/parser is enough for balanced lists, strings, atoms, comments, and line/column metadata.
   - Do not reuse Kawa's evaluator or reader for a safe manifest parser.
   - Run the same semantic validator as YAML.

4. Treat the line-oriented DSL as an ergonomic experiment.
   - It may be the simplest parser, but it is not obviously better than YAML for nested specs.
   - Keep it off the critical path unless user testing shows the syntax is more approachable.

5. Keep Java/Kawa builder behind trusted plugin boundaries only.
   - It can create the same `AvatarUiSpec` model.
   - It must not be enabled for untrusted downloaded plugins or user-pasted scripts.

## Validation And Security Model

Validation must be a separate phase after parsing and before rendering or persistence.

Structural validation:

- `id` fields match a conservative regex such as `[a-z][a-z0-9-]{1,63}`.
- Every `target` is a local CSS id selector such as `#avatar-widget-daily-focus`.
- Every target referenced by a form, refresh, or action exists in the same panel or in a host-declared safe target set.
- `method` is one of `GET`, `POST`, `PUT`, `PATCH`, or `DELETE`.
- `swap` is one of `innerHTML`, `outerHTML`, or `none`.
- `size` is one of host-defined widget sizes.
- `field.type` is from a host allowlist such as `text`, `textarea`, `date`, `datetime`, `select`, `checkbox`, `hidden`.
- Field names match `[a-z][a-zA-Z0-9_]{0,63}`.
- Maximum counts are enforced: panels, widgets per panel, forms per panel, fields per form, options per select, string length.

Capability validation:

- `source` values come from an allowlist, for example:
  - `avatar.dailyTasks.today`
  - `avatar.todos.open`
  - `avatar.calendar.upcoming`
  - `avatar.notes.recent`
  - `avatar.outputs.recent`
  - `avatar.alerts.inbox`
- Route values must be local paths under approved prefixes such as `/avatar/` or `/avatar/_widgets/`.
- External URLs are rejected.
- No shell commands, filesystem paths, class names, bean names, SQL, scripts, or model prompts are accepted in the UI DSL.
- Tool exposure stays outside this UI DSL. Future plugin-contributed tools must go through `ChatToolRegistry` and existing approved-tool filtering, not through widget specs.

Rendering security:

- DSL text maps to `withInnerText`, `Slot`, or safe form component properties.
- The DSL must not expose `withUnsafeHtml`.
- Markdown, if ever allowed, must be opt-in per trusted source and go through the same sanitizer path used elsewhere.
- HTMX state-changing endpoints must use the same auth and CSRF handling as first-party Avatar endpoints.
- HTMX authorization or validation failures should return controlled fragments, not stack traces or transport-only errors.

Persistence security:

- Store imported specs separately from `avatar_dashboard_layout` until the runtime model is designed.
- Persist only normalized specs or approved widget instances, not raw unvalidated source as executable runtime input.
- Keep `avatar.sqlite` data ownership intact; do not create cross-database foreign keys.

## Error Reporting

Parser and validator errors should be first-class outputs. A useful diagnostic record:

```java
record DslDiagnostic(
    String code,
    Severity severity,
    String message,
    SourceRange range,
    String path,
    String hint
) {}
```

Examples:

```text
AVDSL001 error line 12, column 5: expected ')' to close panel 'daily-focus'
  hint: add one closing parenthesis after the add-todo form

AVDSL014 error panels[0].forms[0].action: route must start with /avatar/
  found: https://example.com/callback

AVDSL021 error panels[0].widgets[1].source: unknown Avatar data source
  found: avatar.files.rawPath
  allowed: avatar.todos.open, avatar.notes.recent, avatar.outputs.recent
```

Format-specific notes:

- YAML/JSON: preserve Jackson syntax errors, then wrap them into `DslDiagnostic`; semantic errors should use object paths like `panels[0].widgets[1].source`.
- S-expression: custom parser should report unmatched parens, unexpected atoms, duplicate keys, and invalid node names with line/column and a short source excerpt.
- Line DSL: report command name, line number, illegal parent block, and unknown key-value pairs.
- Java/Kawa builder: rely on Java tests and builder validation for Java; Kawa errors should be treated as trusted-script failures, not user-safe manifest diagnostics.

## HTMX And SimplyPages Mapping

The DSL should map to SimplyPages at a coarse component level:

| DSL element | Host render target | HTMX behavior |
| --- | --- | --- |
| `panel` | `Div` or `ContentModule` wrapper with stable id | Optional `hx-get` refresh route and stable fragment endpoint |
| `widget` type `list` | `SimpleListModule` or custom reusable Avatar widget module | Refresh button targets widget root with `outerHTML` |
| `widget` type `checklist` | Form/list component over Avatar tasks | Complete/reopen actions use HTMX `POST`/`PUT` to widget target |
| `widget` type `table` | `DataTable` or table primitive | Filters and row actions use HTMX fragments |
| `form` | `Form.create()` plus fields | `withHxPost`/`withHxTarget`/`withHxSwap`; state-changing routes require CSRF |
| `refresh` | `Button` or panel attribute | `hx-get`, `hx-target`, `hx-swap` |
| `modal` future extension | `Modal` into one container such as `#avatar-modal-container` | `innerHTML` open, OOB close/update on save |

Mapping rules:

- Stable widget IDs should follow the planned `/avatar` convention, for example `avatar-widget-daily-tasks`.
- Standard CRUD and widget refreshes should be HTMX-first.
- JavaScript should remain limited to Avatar compact chat/SSE or interactions where HTMX is genuinely awkward.
- Repeated static panel structures can become `Template` instances with per-request `RenderContext` slots.
- Mutable component trees must be built per request or compiled before reuse; plugin specs must not cause shared mutable `Module` instances across requests.

## Risk Assessment

YAML/JSON risks:

- Medium authoring risk from YAML indentation and hidden type coercion.
- Low parser implementation risk because Jackson/YAML is already present.
- Medium validation risk if unknown properties or unsafe route/source values are not rejected.

S-expression risks:

- Low parser implementation risk if it remains data-only.
- Medium user-experience risk from unfamiliar syntax and dense nesting.
- High security risk if anyone tries to "just use Kawa" and accidentally turns the manifest into executable code.

Line DSL risks:

- Low parser implementation risk for simple specs.
- Medium future-complexity risk once panels need nested conditional sections, rich settings, or multi-line content.
- Medium migration risk if early examples spread before the grammar stabilizes.

Java/Kawa builder risks:

- Low risk for trusted in-repo Java code.
- High risk for untrusted or semi-trusted user scripts.
- High scope risk because it pulls in plugin loading, dependency management, lifecycle, sandboxing, and code-execution policy.

Common risks:

- Runtime implementation can easily sprawl from "simple widget manifest" into a plugin marketplace, a second tool registry, or arbitrary script execution.
- Poor diagnostics will make even a simple grammar feel brittle.
- Allowing raw HTML or arbitrary endpoints would undermine SimplyPages safe defaults and HTMX auth expectations.
- Tool exposure through UI specs would bypass the explicit approved-tool model.

## Recommendations

1. Use YAML/JSON as the production-oriented manifest candidate if this resumes.
   - It uses dependencies already present in the repo.
   - It is easiest to validate with records and schema-like checks.
   - It is easiest for a future UI editor to import/export.

2. Keep the parenthesized idea as a data-only s-expression research prototype.
   - This is the best simple-to-parse custom grammar.
   - It should parse to AST, never evaluate.
   - It should share the same validator as YAML/JSON.

3. Use the line-oriented DSL only for ergonomic testing.
   - It is easy to parse but likely weaker for nested UI.
   - It may be useful for quick examples or a migration/input format later.

4. Keep Java/Kawa builder APIs trusted-only.
   - Java builders fit future developer plugins.
   - Kawa fits trusted local customization only.
   - Neither should be treated as a safe user-authored manifest.

5. Define the spec model and validator before choosing a syntax permanently.
   - The syntax should be an input adapter.
   - The validator and SimplyPages renderer should be format-independent.

6. Do not implement runtime plugin loading during the Avatar sprint.
   - First-party `/avatar` widgets, persistence, chat behavior, and HTMX routes are already enough sprint scope.
   - Plugin runtime requires separate architecture for installation, versioning, permissions, auth, CSRF, testing, diagnostics, and possibly sandboxing.

## Why Runtime Implementation Remains Out Of This Sprint

Runtime implementation is intentionally out of scope because the Avatar sprint still needs first-party delivery:

- `/avatar` dashboard route and fragments.
- Compact Avatar chat over the existing chat runtime.
- HTMX-first layout editing and widget CRUD.
- Organizer tools and services over `avatar.sqlite`.
- Output/file viewing through existing confined services.
- Browser validation and screenshots for the new user-facing surface.

Adding plugin runtime now would create unresolved decisions before the first-party surface proves itself:

- Where plugin specs are stored and versioned.
- Whether plugins are trusted local files, developer jars, or untrusted third-party packages.
- How plugin permissions are reviewed and granted.
- How HTMX endpoints are namespaced and authorized.
- How plugin failures appear in the UI.
- How specs migrate when Avatar widget contracts change.
- Whether Kawa or Java SPI is allowed at all.
- How to test untrusted or malformed plugin packages.

The right sprint outcome is a first-party Avatar dashboard whose stable widget/component boundaries later make plugin specs obvious.

## Follow-ups

- Draft `AvatarUiSpec` records and validator as a planning artifact before any parser implementation.
- If a custom parser is still desired, prototype the s-expression parser in an isolated test-only package or `.internal-dev` note first.
- Create three golden sample specs: notes quick-add, daily planning, and output review.
- Decide whether imported specs are local-admin only or user-editable before designing persistence.
- Keep plugin-contributed tools separate from UI specs and require explicit approved-tool configuration.
- Revisit Kawa only after the trusted-vs-untrusted plugin policy is settled.
