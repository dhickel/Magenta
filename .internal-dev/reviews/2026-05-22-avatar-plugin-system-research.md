# Avatar Plugin System Research Review

## Scope

Research-only review for the Avatar sprint. This review compares Kawa Scheme, a SimplyPages DSL, Java plugin SPI, and Spring AI tool extension. It includes code-shaped examples for later planning, but plugin runtime implementation is out of scope for this sprint.

Sources:

- Kawa overview: https://www.gnu.org/software/kawa/index.html
- Kawa features: https://www.gnu.org/software/kawa/Features.html
- Kawa Java interop: https://www.gnu.org/software/kawa/Method-operations.html
- Spring AI tool calling: https://docs.spring.io/spring-ai/reference/api/tools.html
- Java `ServiceLoader`: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html
- Java scripting API: https://docs.oracle.com/en/java/javase/26/docs/api/java.scripting/javax/script/package-summary.html
- Local SimplyPages docs:
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/01-components-htmltag-and-module-lifecycle.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/security/01-security-boundaries-and-safe-rendering.md`
- Local Magenta anchors:
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`

## Findings

### Kawa Scheme

Kawa is practical for trusted local scripting. Its official docs describe a JVM-hosted language that combines scripting ergonomics with Java platform integration. It supports calling Java methods from Scheme, and the Java scripting API can discover engines through service-provider loading.

The central risk is safety. Kawa's strength is also its danger: direct Java interop gives scripts access to the Java platform. That is acceptable for trusted local customization behind a narrow Magenta host API, but it is not an in-process sandbox for untrusted code.

### SimplyPages DSL

A SimplyPages DSL is the safest user-authored UI extension shape. It can expose declarative panels, forms, slots, and HTMX endpoint contracts while the host remains responsible for rendering and escaping. The local docs emphasize mutable component construction, per-request render context, and safe rendering boundaries. That fits Avatar widgets and settings panels, but not general plugin logic.

### Java Plugin SPI

Java SPI is a good trusted developer-plugin base. `ServiceLoader` locates providers for a known interface and supports module/classpath deployments. For Magenta, SPI should load once at startup into immutable descriptors rather than dynamically throughout request handling.

SPI is not friendly end-user scripting. It is better as the host contract used by Java plugins and by any trusted Kawa adapter later.

### Spring AI Tool Extension

Spring AI tool calling is already close to Magenta's extension surface. Magenta collects `ToolCallback` and `ToolCallbackProvider` beans in `ChatToolRegistry` and filters tools by approved names and tool policy. Plugin-contributed tools should use this path rather than creating another tool registry.

Tool exposure must remain explicit. No plugin tool should become model-visible through wildcard approval or automatic enablement.

## Risk Assessment

- Kawa inside the Magenta JVM is unsafe for untrusted third-party plugins.
- Java SPI plugins are trusted-code plugins and inherit host process privileges.
- A declarative SimplyPages DSL reduces UI risk but cannot cover arbitrary compute/tool behavior.
- Spring AI tools are capability-level extensions, not a plugin lifecycle system.
- Plugin runtime work can easily sprawl into dependency loading, sandboxing, permissions, UI rendering, and persistence; it should remain deferred until Avatar's first-party workflows are stable.

## Recommendations

Use a layered design later:

1. Java SPI `AvatarPlugin` as the trusted host contract.
2. Spring AI `ToolCallback` contribution as an optional capability, gated by existing approved-tool policy.
3. Kawa adapter for trusted local scripts only, behind a narrow host API.
4. Declarative SimplyPages DSL for UI panels/forms once a concrete plugin UI workflow exists.

Keep plugin runtime out of the Avatar sprint. Revisit only after first-party Avatar dashboard, tools, and persistence are working.

## Future Runtime Sketch - Do Not Implement In This Sprint

```java
public interface AvatarPlugin {
    PluginDescriptor descriptor();

    default List<ToolCallback> tools(PluginContext context) {
        return List.of();
    }

    default List<AvatarPanelSpec> panels(PluginContext context) {
        return List.of();
    }
}

public record PluginDescriptor(
    String id,
    String version,
    Set<PluginCapability> capabilities
) {}
```

```java
final class AvatarPluginCatalog {
    private final List<AvatarPlugin> plugins;

    AvatarPluginCatalog() {
        this.plugins = ServiceLoader.load(AvatarPlugin.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();
    }

    List<ToolCallback> toolCallbacks(PluginContext context) {
        return plugins.stream()
            .flatMap(plugin -> plugin.tools(context).stream())
            .toList();
    }
}
```

```java
@Configuration
class AvatarPluginToolConfiguration {
    @Bean
    ToolCallbackProvider avatarPluginToolCallbackProvider(
        AvatarPluginCatalog catalog,
        PluginContext context
    ) {
        return ToolCallbackProvider.from(catalog.toolCallbacks(context));
    }
}
```

Trusted Kawa adapter shape:

```scheme
(define handler
  (object (io.mindspice.magenta2.plugin.ScriptToolHandler)
    ((call (args ::java.util.Map)
           (ctx ::io.mindspice.magenta2.plugin.PluginExecutionContext))
        ::java.lang.String
      (let ((note (invoke args 'get "note")))
        (invoke ctx 'appendAvatarNote note)
        "saved"))))

(invoke magenta 'registerTool
  "avatar_note_append"
  "Append a short note to the active avatar journal."
  "{\"type\":\"object\",\"properties\":{\"note\":{\"type\":\"string\"}},\"required\":[\"note\"]}"
  handler)
```

Declarative UI shape:

```yaml
panel:
  id: avatar-notes
  title: Avatar Notes
  target: "#avatar-notes"
  hxGet: "/avatar/plugins/notes/panel"
  fields:
    - name: note
      type: text
      required: true
  submit:
    hxPost: "/avatar/plugins/notes"
    hxTarget: "#avatar-notes"
    hxSwap: "outerHTML"
```

## Follow-ups

- Verify Kawa runtime compatibility with the repo's actual Java target before adding a dependency.
- Decide whether plugins are trusted local scripts, trusted developer jars, or untrusted third-party packages.
- Define the minimal host API before any Kawa examples become real code.
- Confirm Spring AI API parity against the repo-pinned version before coding.
- If untrusted code is required, research out-of-process execution and OS-level restrictions first.
