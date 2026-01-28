package com.magenta.model;

import com.magenta.io.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public sealed interface ChatModel permits ChatModel.Streaming, ChatModel.Blocking {


    CompletableFuture<String> generate(List<ChatMessage> messages, ResponseHandler handler);

    boolean isStreaming();


    record Streaming(StreamingChatLanguageModel model) implements ChatModel {

        @Override
        public CompletableFuture<String> generate(List<ChatMessage> messages, ResponseHandler handler) {
            CompletableFuture<String> future = new CompletableFuture<>();

            model.generate(messages, new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    handler.write(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    handler.complete();
                    future.complete(handler.getBuffer());
                }

                @Override
                public void onError(Throwable error) {
                    handler.error(error);
                    future.completeExceptionally(error);
                }
            });

            return future;
        }

        @Override
        public boolean isStreaming() {
            return true;
        }
    }


    record Blocking(ChatLanguageModel model) implements ChatModel {

        @Override
        public CompletableFuture<String> generate(List<ChatMessage> messages, ResponseHandler handler) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Response<AiMessage> response = model.generate(messages);
                    String text = response.content().text();
                    handler.write(text);
                    handler.complete();
                    return text;
                } catch (Throwable error) {
                    handler.error(error);
                    throw new RuntimeException(error);
                }
            });
        }

        @Override
        public boolean isStreaming() {
            return false;
        }
    }
}
