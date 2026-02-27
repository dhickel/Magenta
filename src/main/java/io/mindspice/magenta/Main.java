package io.mindspice.magenta;

import io.mindspice.magenta.config.Config;
import io.mindspice.magenta.systems.Magenta;

public class Main {

    public static void main(String[] args) {
        Config config = Config.loadDefault();
        Magenta magenta = new Magenta(config);
    }
}
