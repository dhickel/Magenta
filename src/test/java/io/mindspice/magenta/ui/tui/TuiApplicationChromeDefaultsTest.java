package io.mindspice.magenta.ui.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuiApplicationChromeDefaultsTest {

    @Test
    void configureFrameworkChromeDefaultsDisablesWindowShadowing() {
        String previous = System.getProperty("casciian.shadowOpacity");
        try {
            System.clearProperty("casciian.shadowOpacity");

            TuiApplication.configureFrameworkChromeDefaults();

            assertThat(System.getProperty("casciian.shadowOpacity")).isEqualTo("0");
        } finally {
            if (previous == null) {
                System.clearProperty("casciian.shadowOpacity");
            } else {
                System.setProperty("casciian.shadowOpacity", previous);
            }
        }
    }
}
