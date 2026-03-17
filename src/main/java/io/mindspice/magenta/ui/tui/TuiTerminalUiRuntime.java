package io.mindspice.magenta.ui.tui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TuiTerminalUiRuntime {
    private final RuntimeConfig runtimeConfig;
    private final Magenta magenta;
    private final TerminalUiConfig config;
    private final WorkspaceHost workspaceHost;
    private final TuiThemeRegistry themeRegistry;
    private final TuiApplication app;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final PromptService promptService = request -> switch (request) {
        case UiPromptRequest.ConfirmPrompt confirm -> new UiPromptResponse.ConfirmResponse(confirm.defaultYes());
        case UiPromptRequest.SelectPrompt select -> new UiPromptResponse.SelectResponse(select.defaultIndex(),
                select.options().isEmpty() ? "" : select.options().get(Math.min(select.defaultIndex(), select.options().size() - 1)));
        case UiPromptRequest.TextPrompt text -> new UiPromptResponse.TextResponse(text.defaultValue());
    };

    public TuiTerminalUiRuntime(RuntimeConfig runtimeConfig, Magenta magenta, TerminalUiConfig config) throws Exception {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.config = Objects.requireNonNull(config, "config");
        this.workspaceHost = new WorkspaceHost();
        this.themeRegistry = new TuiThemeRegistry();
        this.app = new TuiApplication(themeRegistry, workspaceHost);
    }

    public void runLoop() {
        try {
            app.run();
        } finally {
            close();
        }
    }

    public PromptService promptService() {
        return promptService;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            app.invokeLater(app::exit);
        } catch (Exception ignored) {
        }
    }
}
