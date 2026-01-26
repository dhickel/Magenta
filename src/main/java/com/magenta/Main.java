package com.magenta;

import com.magenta.data.DatabaseService;
import com.magenta.domain.TodoService;
import com.magenta.memory.VectorStoreService;
import com.magenta.tools.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;

public class Main {

    public interface ExperimentAgent {
        @SystemMessage("""
                You are a Test Agent for the Magenta framework.
                You are running in an experimental CLI loop to help the developer test tools.
                
                TOOLS:
                - File System: Read/Write/List files.
                - Shell: Execute commands.
                - Web: Fetch content.
                - Todo: Manage tasks.
                - Knowledge: Store/Retrieve vectors.
                
                INSTRUCTIONS:
                - Use tools whenever appropriate.
                - Be concise but helpful.
                - If a tool fails, report the error clearly.
                """)
        String chat(String userMessage);
    }

    public static void main(String[] args) {
        System.out.println("Starting Magenta Experiment CLI...");

        // 1. Services
        DatabaseService dbService = new DatabaseService();
        try {
            dbService.init();
        } catch (Exception e) {
            System.err.println("Warning: DB Init failed (some tools may break): " + e.getMessage());
        }

        TodoService todoService = new TodoService(dbService);
        VectorStoreService vectorStoreService = new VectorStoreService();

        // 2. Tools
        TodoTools todoTools = new TodoTools(todoService);
        FileSystemTools fileSystemTools = new FileSystemTools();
        WebTools webTools = new WebTools();
        ShellTools shellTools = new ShellTools();
        KnowledgeTools knowledgeTools = new KnowledgeTools(vectorStoreService);
        DelegateTool delegateTool = new DelegateTool();

        // 3. Model Configuration
        System.out.println("Connecting to Ollama (glm4.7f)...");
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://192.168.1.232:11434")
                .modelName("glm4.7f")
                .timeout(Duration.ofSeconds(120))
                .temperature(0.25)
                .numCtx(30000)
                .build();

        // 4. Build Agent
        ExperimentAgent agent = AiServices.builder(ExperimentAgent.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .tools(todoTools, fileSystemTools, webTools, shellTools, knowledgeTools, delegateTool)
                .build();

        // 5. Loop
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            PrintWriter writer = terminal.writer();


            while (true) {
                String lineIn = reader.readLine();
                if ("/exit".equalsIgnoreCase(lineIn)) { break; }

                String response = agent.chat(lineIn);
                writer.println(response);
                terminal.flush();
            }
            writer.println("Exiting....");
            terminal.flush();
        } catch (IOException e) { System.err.println("Error creating terminal: " + e.getMessage()); }

    }
}