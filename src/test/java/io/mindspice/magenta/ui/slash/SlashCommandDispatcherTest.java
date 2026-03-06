package io.mindspice.magenta.ui.slash;

import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.render.UiRenderer;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandDispatcherTest {

    @Test
    void dispatchesHandlersByArity() throws Exception {
        AtomicInteger zeroCalls = new AtomicInteger();
        AtomicInteger oneCalls = new AtomicInteger();
        AtomicInteger twoCalls = new AtomicInteger();
        AtomicInteger threeCalls = new AtomicInteger();

        SlashCommandRegistry registry = new SlashCommandRegistry(List.of(
                SlashCommandSpec.zero("zero", List.of(), "", "/zero", zeroCalls::incrementAndGet),
                SlashCommandSpec.one("one", List.of(), "", "/one <a>", List.of("a"), a -> oneCalls.incrementAndGet()),
                SlashCommandSpec.two("two", List.of(), "", "/two <a> <b>", List.of("a", "b"), (a, b) -> twoCalls.incrementAndGet()),
                SlashCommandSpec.three("three", List.of(), "", "/three <a> <b> <c>", List.of("a", "b", "c"), (a, b, c) -> threeCalls.incrementAndGet())
        ));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), outputStream)
                .build()) {

            UiRenderer renderer = new UiRenderer(
                    terminal,
                    new TerminalUiConfig.Rendering(false, false, false, TerminalUiConfig.ColorPalette.defaults())
            );
            SlashCommandDispatcher dispatcher = new SlashCommandDispatcher(registry, renderer);

            dispatcher.dispatchIfCommand("/zero");
            dispatcher.dispatchIfCommand("/one x");
            dispatcher.dispatchIfCommand("/two x y");
            dispatcher.dispatchIfCommand("/three x y z");
            dispatcher.dispatchIfCommand("/three only-two args");

            assertThat(zeroCalls.get()).isEqualTo(1);
            assertThat(oneCalls.get()).isEqualTo(1);
            assertThat(twoCalls.get()).isEqualTo(1);
            assertThat(threeCalls.get()).isEqualTo(1);
        }
    }
}
