package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.session.SessionOutput;

import java.util.Objects;

public final class AssistantOutputWriter {

    private final AssistantOutputTarget target;
    private final String assistantPrefix;
    private final boolean emitAssistantPrefix;
    private boolean streamInProgress = false;

    public AssistantOutputWriter(AssistantOutputTarget target) {
        this(target, false, "assistant", true);
    }

    public AssistantOutputWriter(AssistantOutputTarget target, boolean emitFallbackNotice) {
        this(target, emitFallbackNotice, "assistant", true);
    }

    public AssistantOutputWriter(AssistantOutputTarget target, boolean emitFallbackNotice, String assistantLabel) {
        this(target, emitFallbackNotice, assistantLabel, true);
    }

    public AssistantOutputWriter(
            AssistantOutputTarget target,
            boolean emitFallbackNotice,
            String assistantLabel,
            boolean emitAssistantPrefix
    ) {
        this.target = Objects.requireNonNull(target, "target");
        this.assistantPrefix = normalizeAssistantPrefix(assistantLabel);
        this.emitAssistantPrefix = emitAssistantPrefix;
    }

    public void onOutput(SessionOutput output) {
        switch (output) {
            case SessionOutput.StreamedOutput stream -> {
                if (!streamInProgress && emitAssistantPrefix) {
                    target.printAssistantToken(assistantPrefix);
                }
                streamInProgress = true;
                target.printAssistantToken(stream.text());
            }
            case SessionOutput.FinalOutput finalOutput -> {
                boolean hasFinalText = finalOutput.text() != null && !finalOutput.text().isBlank();
                if (streamInProgress) {
                    target.finishAssistantStreamLine();
                } else {
                    if (hasFinalText) {
                        String text = emitAssistantPrefix ? assistantPrefix + finalOutput.text() : finalOutput.text();
                        target.printAssistantFinal(text);
                    }
                }
                streamInProgress = false;
            }
            case SessionOutput.ToolCallOutput toolCallOutput ->
                    target.printToolCall(toolCallOutput.toolCall().name(), toolCallOutput.toolCall().argumentsJson());
            case SessionOutput.ToolMessageOutput toolMessageOutput -> target.printToolResult(
                    toolMessageOutput.message().toolName(),
                    toolMessageOutput.text(),
                    isFailurePayload(toolMessageOutput.text())
            );
        }
    }

    private boolean isFailurePayload(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains("\"status\":\"failed\"") || normalized.contains("\"status\": \"failed\"");
    }

    private static String normalizeAssistantPrefix(String assistantLabel) {
        String label = assistantLabel == null ? "" : assistantLabel.trim();
        if (label.isEmpty()) {
            label = "assistant";
        }
        return label + "> ";
    }
}
