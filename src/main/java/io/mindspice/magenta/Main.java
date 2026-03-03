package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.session.config.SessionConfig;

public class Main {

    public static void main(String[] args) {
        RuntimeConfig config = RuntimeConfig.loadDefault();
        Magenta magenta = new Magenta(config);
        var handle = magenta.startBaseSession("main", SessionConfig.defaults());
        System.out.println("Magenta ready. sessionId=" + handle.sessionId() + " active=" + handle.isActive());
    }
}
