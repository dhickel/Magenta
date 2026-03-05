package io.mindspice.magenta.ui.prompt;

import java.util.List;

public sealed interface UiPromptRequest permits UiPromptRequest.ConfirmPrompt, UiPromptRequest.SelectPrompt, UiPromptRequest.TextPrompt {

    String title();

    String message();

    record ConfirmPrompt(
            String title,
            String message,
            boolean defaultYes
    ) implements UiPromptRequest {
        public ConfirmPrompt {
            title = title == null ? "Confirm" : title;
            message = message == null ? "" : message;
        }
    }

    record SelectPrompt(
            String title,
            String message,
            List<String> options,
            int defaultIndex
    ) implements UiPromptRequest {
        public SelectPrompt {
            title = title == null ? "Select" : title;
            message = message == null ? "" : message;
            options = options == null ? List.of() : List.copyOf(options);
            defaultIndex = defaultIndex < 0 ? 0 : defaultIndex;
        }
    }

    record TextPrompt(
            String title,
            String message,
            boolean allowEmpty,
            String defaultValue
    ) implements UiPromptRequest {
        public TextPrompt {
            title = title == null ? "Input" : title;
            message = message == null ? "" : message;
            defaultValue = defaultValue == null ? "" : defaultValue;
        }
    }
}
