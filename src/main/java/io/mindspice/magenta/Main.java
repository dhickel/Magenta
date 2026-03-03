package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;

public class Main {

    public static void main(String[] args) {
        RuntimeConfig config = RuntimeConfig.loadDefault();
        Magenta magenta = new Magenta(config);
        var handle = magenta.startBaseSession(
                "main",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        System.out.println("Magenta ready. sessionId=" + handle.sessionId() + " active=" + handle.isActive());
    }
}
