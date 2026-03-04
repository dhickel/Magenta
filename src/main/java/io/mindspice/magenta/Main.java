package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;

public class Main {

    public static void main(String[] args) {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("configs", "magenta.yaml");
        RuntimeConfig config = RuntimeConfig.load(configPath);
        Magenta magenta = new Magenta(config);
        var handle = magenta.startBaseSession(
                "dogfood-repl",
                new SessionConfig(
                        SessionParams.ofStreaming(false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        error -> System.err.println("[session-error] " + Arrays.toString(error.getStackTrace()))
                )
        );

        magenta.registerInputRoute(
                handle,
                InputRoutePolicy.defaults(),
                InputRoutingEvent.Level.FAILURE,
                event -> System.err.println("[route] " + event.outcome() + " - " + event.reason())
        );

        magenta.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                        .build(),
                event -> {
                    if (event.output() instanceof SessionOutput.FinalOutput(String text)) {
                        System.out.println("assistant> " + text);
                    }
                }
        );

        Consumer<SessionInput.MessageInput> messageIn = magenta.getMessageInputConsumer(handle);
        Consumer<SessionInput.EventInput> eventIn = magenta.getEventInputConsumer(handle);

        System.out.println("Magenta chat REPL started.");
        System.out.println("sessionId=" + handle.sessionId());
        System.out.println("Commands: /help, /session, /event <text>, /exit");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("you> ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String line = scanner.nextLine();
                if (line == null || line.isBlank()) {
                    continue;
                }

                String trimmed = line.trim();
                if ("/exit".equalsIgnoreCase(trimmed) || "/quit".equalsIgnoreCase(trimmed)) {
                    break;
                }
                if ("/help".equalsIgnoreCase(trimmed)) {
                    System.out.println("Commands: /help, /session, /event <text>, /exit");
                    continue;
                }
                if ("/session".equalsIgnoreCase(trimmed)) {
                    System.out.println("sessionId=" + handle.sessionId() + " active=" + handle.isActive());
                    continue;
                }
                if (trimmed.startsWith("/event ")) {
                    String eventText = trimmed.substring("/event ".length()).trim();
                    if (eventText.isEmpty()) {
                        System.out.println("usage: /event <text>");
                    } else {
                        eventIn.accept(new SessionInput.SysEvent(eventText, "cli-system", false));
                    }
                    continue;
                }

                messageIn.accept(SessionInput.userMessage(line));
            }
        } finally {
            magenta.closeSession(handle);
        }
    }
}
