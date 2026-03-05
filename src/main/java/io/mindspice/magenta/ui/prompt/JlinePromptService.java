package io.mindspice.magenta.ui.prompt;

import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.render.UiRenderer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.Objects;

public final class JlinePromptService implements PromptService {

    private final LineReader lineReader;
    private final UiRenderer renderer;
    private final TerminalUiConfig.Prompts promptsConfig;

    public JlinePromptService(LineReader lineReader, UiRenderer renderer, TerminalUiConfig.Prompts promptsConfig) {
        this.lineReader = Objects.requireNonNull(lineReader, "lineReader");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.promptsConfig = Objects.requireNonNull(promptsConfig, "promptsConfig");
    }

    @Override
    public synchronized UiPromptResponse prompt(UiPromptRequest request) {
        Objects.requireNonNull(request, "request");
        renderer.printInfo("[prompt] " + request.title() + ": " + request.message());

        return switch (request) {
            case UiPromptRequest.ConfirmPrompt confirm -> promptConfirm(confirm);
            case UiPromptRequest.SelectPrompt select -> promptSelect(select);
            case UiPromptRequest.TextPrompt text -> promptText(text);
        };
    }

    private UiPromptResponse promptConfirm(UiPromptRequest.ConfirmPrompt request) {
        String suffix = request.defaultYes() ? " [Y/n] " : " [y/N] ";
        try {
            String raw = safeRead("confirm" + suffix);
            if (raw == null) {
                return new UiPromptResponse.Cancelled("interrupted");
            }
            String normalized = raw.trim().toLowerCase();
            if (normalized.isEmpty()) {
                return new UiPromptResponse.ConfirmResponse(request.defaultYes());
            }
            boolean approved = "y".equals(normalized) || "yes".equals(normalized);
            return new UiPromptResponse.ConfirmResponse(approved);
        } catch (Exception ignored) {
            return new UiPromptResponse.Cancelled("prompt_failed");
        }
    }

    private UiPromptResponse promptSelect(UiPromptRequest.SelectPrompt request) {
        if (request.options().isEmpty()) {
            return new UiPromptResponse.Cancelled("no_options");
        }

        for (int i = 0; i < request.options().size(); i++) {
            renderer.printSystem("  " + (i + 1) + ") " + request.options().get(i));
        }

        int defaultIndex = Math.min(Math.max(0, request.defaultIndex()), request.options().size() - 1);
        try {
            String raw = safeRead("select [default=" + (defaultIndex + 1) + "] ");
            if (raw == null) {
                return new UiPromptResponse.Cancelled("interrupted");
            }
            String normalized = raw.trim();
            if (normalized.isEmpty()) {
                return new UiPromptResponse.SelectResponse(defaultIndex, request.options().get(defaultIndex));
            }
            int selected = Integer.parseInt(normalized) - 1;
            if (selected < 0 || selected >= request.options().size()) {
                return new UiPromptResponse.Cancelled("invalid_selection");
            }
            return new UiPromptResponse.SelectResponse(selected, request.options().get(selected));
        } catch (Exception ignored) {
            return new UiPromptResponse.Cancelled("prompt_failed");
        }
    }

    private UiPromptResponse promptText(UiPromptRequest.TextPrompt request) {
        try {
            String raw = safeRead("input" + (request.defaultValue().isBlank() ? "" : " [default set]") + " ");
            if (raw == null) {
                return new UiPromptResponse.Cancelled("interrupted");
            }
            if (raw.isBlank()) {
                if (!request.allowEmpty() && request.defaultValue().isBlank()) {
                    return new UiPromptResponse.Cancelled("empty_input");
                }
                return new UiPromptResponse.TextResponse(request.defaultValue());
            }
            return new UiPromptResponse.TextResponse(raw);
        } catch (Exception ignored) {
            return new UiPromptResponse.Cancelled("prompt_failed");
        }
    }

    public String truncateForToolPrompt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int max = Math.max(32, promptsConfig.maxToolArgsPreviewChars());
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
    }

    private String safeRead(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }
}
