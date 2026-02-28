package io.mindspice.magenta;

import io.mindspice.magenta.systems.Magenta;
import io.mindspice.magenta.systems.config.RuntimeConfig;
import io.mindspice.magenta.systems.session.SessionConfig;

public class Main {

    public static void main(String[] args) {
        RuntimeConfig config = RuntimeConfig.loadDefault();
        Magenta magenta = new Magenta(config);
        var session = magenta.startBaseSession("main", SessionConfig.defaults());
        System.out.println("Magenta ready. sessionId=" + session.sessionId() + " alias=" + session.alias());
    }
}
