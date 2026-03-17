package io.mindspice.magenta.ui.tui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.ToolApprovalPromptAdapter;

public final class TuiTerminalUiBootstrap {

    private TuiTerminalUiBootstrap() {
    }

    public static TuiTerminalUiRuntime bootstrap(
            Magenta magenta,
            TerminalUiConfig config,
            ToolApprovalPromptAdapter approvalAdapter
    ) throws Exception {
        TuiTerminalUiRuntime runtime = new TuiTerminalUiRuntime(
                magenta.runtimeConfig(),
                magenta,
                config
        );
        approvalAdapter.setPromptService(runtime.promptService());
        return runtime;
    }
}
