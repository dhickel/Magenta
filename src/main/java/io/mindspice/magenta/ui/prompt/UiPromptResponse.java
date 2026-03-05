package io.mindspice.magenta.ui.prompt;

public sealed interface UiPromptResponse permits UiPromptResponse.ConfirmResponse, UiPromptResponse.SelectResponse, UiPromptResponse.TextResponse, UiPromptResponse.Cancelled {

    record ConfirmResponse(boolean approved) implements UiPromptResponse {}

    record SelectResponse(int selectedIndex, String selectedOption) implements UiPromptResponse {
        public SelectResponse {
            selectedOption = selectedOption == null ? "" : selectedOption;
        }
    }

    record TextResponse(String text) implements UiPromptResponse {
        public TextResponse {
            text = text == null ? "" : text;
        }
    }

    record Cancelled(String reason) implements UiPromptResponse {
        public Cancelled {
            reason = reason == null ? "cancelled" : reason;
        }
    }
}
