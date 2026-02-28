package io.mindspice.magenta.systems.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConfigTest {

    @Test
    void defaultsEmitStreamingCompletionToFullResponse() {
        SessionConfig config = SessionConfig.defaults();

        assertThat(config.emitStreamingCompletionToFullResponse()).isTrue();
    }

    @Test
    void emitsStreamingAndFullResponseWhenEnabled() {
        List<String> streaming = new ArrayList<>();
        List<String> full = new ArrayList<>();

        SessionConfig config = SessionConfig.builder()
                .onStreamingResponseConsumer(streaming::add)
                .onFullResponseConsumer(full::add)
                .emitStreamingCompletionToFullResponse(true)
                .build();

        config.emitStreamingResponse("tok-1");
        config.emitFullResponse("final-1", true);

        assertThat(streaming).containsExactly("tok-1");
        assertThat(full).containsExactly("final-1");
    }

    @Test
    void suppressesStreamingCompletionToFullResponseWhenDisabled() {
        List<String> full = new ArrayList<>();

        SessionConfig config = SessionConfig.builder()
                .onFullResponseConsumer(full::add)
                .emitStreamingCompletionToFullResponse(false)
                .build();

        config.emitFullResponse("stream-final", true);
        config.emitFullResponse("blocking-final", false);

        assertThat(full).containsExactly("blocking-final");
    }
}
