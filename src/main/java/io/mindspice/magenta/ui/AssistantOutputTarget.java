package io.mindspice.magenta.ui;

public interface AssistantOutputTarget {

    void printAssistantToken(String token);

    void finishAssistantStreamLine();

    void printAssistantFinal(String text);

    void printToolCall(String toolName, String argumentsJson);

    void printToolResult(String toolName, String content, boolean failed);

    void printStreamFallbackNotice(String reason);
}
