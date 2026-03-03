package io.mindspice.magenta.runtime.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConfigTest {

    @Test
    void defaultsEnableStreamingAndTools() {
        SessionConfig config = SessionConfig.defaults();

        assertThat(config.blockingOnly()).isFalse();
        assertThat(config.toolsEnabled()).isTrue();
        assertThat(config.bypassSecurity()).isFalse();
        assertThat(config.streamingEnabled()).isTrue();
    }

    @Test
    void toViewReturnsSafeSnapshot() {
        SessionConfig config = SessionConfig.builder()
                .blockingOnly(true)
                .toolsEnabled(false)
                .bypassSecurity(true)
                .streamingEnabled(false)
                .build();

        SessionConfigView view = config.toView();
        assertThat(view.blockingOnly()).isTrue();
        assertThat(view.toolsEnabled()).isFalse();
        assertThat(view.bypassSecurity()).isTrue();
        assertThat(view.streamingEnabled()).isFalse();
    }
}
