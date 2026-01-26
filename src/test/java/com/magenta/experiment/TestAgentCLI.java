package com.magenta.experiment;

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

public class TestAgentCLI {


}
