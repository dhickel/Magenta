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

Why this matters:
- `RuntimeConfig.load(...)` fails fast if either agent ID does not resolve to an enabled agent.
- Compaction uses the configured compaction agent's model + prompts when summarization is needed.

## 3) Add a chat loop class

Create `src/main/java/example/QuickStartChatLoop.java`:

```java
package example;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.*;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;

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

        SessionConfig sessionConfig = SessionConfig.builder()
                .toolsEnabled(false)      // Quick start is chat only.
                .streamingEnabled(false)  // Keep output simple: final response only.
                .onError(error -> System.err.println("[session-error] " + error.getMessage()))
                .build();

        this.handle = magenta.startBaseSession("quickstart", sessionConfig);

        magenta.registerInputRoute(
                handle,
                InputRoutePolicy.defaults(),
                InputRoutingEvent.Level.ERROR,
                event -> System.err.println("[route] " + event.outcome() + " - " + event.reason())
        );

        magenta.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .eventKinds(Set.of(OutputRoutingEvent.Kind.FINAL))
                        .build(),
                event -> {
                    if (event instanceof OutputRoutingEvent.AssistantFinal finalEvent) {
                        System.out.println("assistant> " + finalEvent.text());
                    }
                }
        );

        this.messageIn = magenta.getMessageInputConsumer(handle);
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

If you pass a custom config path:

```bash
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=example.QuickStartChatLoop \
  -Dexec.args="/absolute/path/to/configs/magenta.yaml"
```

## 5) What this quick start establishes

- One runtime owner (`Magenta`) for lifecycle + routing.
- One chat session (`SessionHandle`) started from base agent config.
- Input attached through route policy (`registerInputRoute` + `getMessageInputConsumer`).
- Output attached through route policy (`registerOutputRoute` with `AssistantFinal` events only).
- No tool execution path (`toolsEnabled(false)`).

## 6) API contract notes for dogfooding

- Treat `Magenta` + routed handle/input/output types as the supported integration surface.
- `Magenta` intentionally does not expose internal runtime service getters; compose services directly only for advanced custom wiring.
