package com.magenta.session;

import com.magenta.io.Command;
import com.magenta.io.Writer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.jline.utils.InfoCmp;


public sealed interface InteractionMode {
    void processIteration(AgentSession session);

    default void handleCommand(AgentSession session, Command cmd) {
        var io = session.io();
        switch (cmd) {
            case Command.Exit() -> session.setExit(true);
            case Command.Help() -> io.println("Help on the way...");
            case Command.Clear() -> io.terminal().puts(InfoCmp.Capability.clear_screen);
            case Command.History() -> io.info("History not yet implemented");
            case Command.Unknown(String raw) -> io.warn("Unknown command: " + raw);
        }
    }


    record StreamingChat() implements InteractionMode {
        @Override
        public void processIteration(AgentSession session) {
            var agentState = session.agentState();
            String input = session.io().readLine("magenta> ");
            if (input == null || input.isBlank()) { return; }

            // Check for commands
            if (input.startsWith("/")) {
                Command cmd = Command.parse(input);
                if (cmd != null) {
                    handleCommand(session, cmd);
                }
                return;
            }

            // Process as chat message
            int delay = session.state().config().streamDelayMs();
            agentState.addMessage(UserMessage.from(input));


            Writer writer = session.io().startStream(agentState.getColor(), delay);
            agentState.model().generate(agentState.conversationHistory(), writer)
                    .thenAccept(response -> agentState.addMessage(AiMessage.from(response)))
                    .join();
        }
    }


}
