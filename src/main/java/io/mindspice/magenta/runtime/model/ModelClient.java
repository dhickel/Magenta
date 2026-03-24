package io.mindspice.magenta.runtime.model;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.mindspice.magenta.runtime.config.RuntimeConfig;

import java.util.function.Consumer;

public interface ModelClient {

    ChatResponse chatBlocking(RuntimeConfig.ModelConfig modelConfig, ChatRequest request);

    ChatResponse chatStreaming(RuntimeConfig.ModelConfig modelConfig, ChatRequest request, Consumer<String> tokenCallback);
}
