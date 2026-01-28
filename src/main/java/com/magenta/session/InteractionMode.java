package com.magenta.session;

import com.magenta.io.Command;
import com.magenta.io.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;


public sealed interface InteractionMode {
    void processIteration(Session session, Agent agent);

    default void handleCommand(Session session, Command cmd) {
        var io = session.io();
        switch (cmd) {
            case Command.Exit() -> session.setExit(true);
            case Command.Help() -> io.println("Help on the way...");
            case Command.Clear() -> {

            }
            case Command.History() -> io.println("History not yet implemented");
            case Command.Unknown(String raw) -> io.println("Unknown command: " + raw);
            case Command.Message ignored -> { /* Should not happen as Message commands are handled in processIteration */ }
        }
    }


    record StreamingChat() implements InteractionMode {
        @Override
        public void processIteration(Session session, Agent agent) {
            Command cmd = session.io().read("magenta> ");

            if (cmd instanceof Command.Message(String text)) {
                if (text.isBlank()) { return; }

                // Process as chat message
                agent.addMessage(UserMessage.from(text));

                // Get reusable handler from agent (encapsulates IO and config)
                int delay = session.context().config().streamDelayMs();
                ResponseHandler handler = agent.getResponseHandler(session.io(), delay);

                agent.model().generate(agent.conversationHistory(), handler)
                        .thenAccept(response -> agent.addMessage(AiMessage.from(response)))
                        .join();
            } else {
                handleCommand(session, cmd); // Handle non-Message commands
            }
        }
    }


}
