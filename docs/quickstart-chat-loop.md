# Quick Start: Single-Session Chat Loop (No Tools)

This guide shows the full setup for running one local chat session through the public `Magenta` API with routed input/output.

## 1) Prerequisites

- Java 25
- Maven 3.9+
- Local Ollama endpoint available for the configured model

## 2) Minimal config

Use `configs/magenta.yaml` (or your own path) and set `baseAgentId` and `compactionAgentId` explicitly:

```yaml
instance:
  baseAgentId: "default-agent"
  compactionAgentId: "default-agent"
  maxTurns: 8

models:
  include:
    - "models/*.yaml"

prompts:
  include:
    - "prompts/**/*.md"

agents:
  include:
    - "agents/*.yaml"
```

## 3) Add a chat loop class

Create `src/main/java/example/QuickStartChatLoop.java`:

```java
package example;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.*;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;

public final class QuickStartChatLoop {

    private final Magenta magenta;
    private final SessionHandle handle;
    private final Consumer<SessionInput.MessageInput> messageIn;

    public QuickStartChatLoop(Path configPath) {
        RuntimeConfig runtimeConfig = RuntimeConfig.load(configPath);
        this.magenta = new Magenta(runtimeConfig);

        SessionConfig sessionConfig = new SessionConfig(
                SessionParams.ofBlocking(false),
                request -> ToolResult.notHandled(request.toolCall()),
                RoutingEventLevel.FINAL,
                event -> System.err.println("[route] " + event),
                error -> System.err.println("[session-error] " + error.getMessage())
        );

        this.handle = magenta.startBaseSession("quickstart", sessionConfig);

        magenta.addInputRoute(handle, InputRoutePolicy.defaults());

        magenta.addOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                        .build(),
                event -> {
                    if (event.output() instanceof SessionOutput.FinalOutput finalOutput) {
                        System.out.println("assistant> " + finalOutput.text());
                    }
                }
        );

        this.messageIn = magenta.messageInputConsumer(handle);
    }

    public void runConsoleLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Chat started. Type /exit to quit.");
            while (true) {
                System.out.print("you> ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String line = scanner.nextLine();
                if (line == null || line.isBlank()) {
                    continue;
                }
                if ("/exit".equalsIgnoreCase(line.trim())) {
                    break;
                }

                messageIn.accept(SessionInput.userMessage(line));
            }
        } finally {
            magenta.closeSession(handle);
        }
    }

    public static void main(String[] args) {
        Path configPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("configs", "magenta.yaml");

        QuickStartChatLoop app = new QuickStartChatLoop(configPath);
        app.runConsoleLoop();
    }
}
```

## 4) Run it

```bash
mvn -q -DskipTests compile
mvn -q -DskipTests exec:java -Dexec.mainClass=example.QuickStartChatLoop
```

## 5) What this quick start establishes

- One runtime owner (`Magenta`) for lifecycle + routing.
- One chat session (`SessionHandle`) started from base agent config.
- Input attached through route policy (`addInputRoute` + `messageInputConsumer`).
- Output attached through route policy (`addOutputRoute` with `FinalOutput` events only).
- No tool execution path (`SessionParams.ofBlocking(false)` sets `toolsEnabled=false`).

## 6) Terminal Debug Visibility Knobs

For terminal UI debugging (when running `Main` / packaged jar), use:

- `MAGENTA_UI_ROUTE_LOGS=true` to emit detailed route delivery logs.
- `terminal.security.eventVisibility: "all"` in `configs/magenta.yaml` to show every security decision.
- `terminal.security.eventVisibility: "denials_only"` (default) to show only denied/validation decisions.
- `terminal.rendering.colors` to tune terminal style colors for high-contrast debugging sessions.
