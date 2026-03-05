package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.ui.prompt.JlinePromptService;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolApprovalPromptAdapter implements SecurityManager.ApprovalCallback {

    private final AtomicReference<PromptService> promptServiceRef = new AtomicReference<>();

    public void setPromptService(PromptService promptService) {
        promptServiceRef.set(Objects.requireNonNull(promptService, "promptService"));
    }

    @Override
    public SecurityManager.ApprovalResponse approve(SecurityManager.ApprovalRequest request) {
        PromptService promptService = promptServiceRef.get();
        if (promptService == null) {
            return SecurityManager.ApprovalResponse.DENY;
        }

        String argsPreview = request.argumentsJson() == null ? "" : request.argumentsJson();
        if (promptService instanceof JlinePromptService jlinePromptService) {
            argsPreview = jlinePromptService.truncateForToolPrompt(argsPreview);
        }

        UiPromptResponse response = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                "Tool Approval",
                "Allow tool '" + request.toolName() + "'? reason='" + request.reason() + "' args='" + argsPreview + "'",
                false
        ));

        return switch (response) {
            case UiPromptResponse.ConfirmResponse confirm -> confirm.approved()
                    ? SecurityManager.ApprovalResponse.APPROVE
                    : SecurityManager.ApprovalResponse.DENY;
            default -> SecurityManager.ApprovalResponse.DENY;
        };
    }
}
