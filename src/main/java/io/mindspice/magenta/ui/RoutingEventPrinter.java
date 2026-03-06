package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.ui.render.UiRenderBlock;
import io.mindspice.magenta.ui.render.UiRenderer;
import io.mindspice.magenta.ui.render.UiStyle;

import java.util.Objects;

public final class RoutingEventPrinter {

    private final UiRenderer renderer;
    private final RoutingEventFormatter formatter;

    public RoutingEventPrinter(UiRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.formatter = new RoutingEventFormatter();
    }

    public void print(RoutingEvent event) {
        UiStyle style = event instanceof RoutingEvent.OutputResult outputResult && !outputResult.failedRoutes().isEmpty()
                ? UiStyle.WARN
                : UiStyle.MUTED;
        String title = event instanceof RoutingEvent.InputResult ? "route> input" : "route> output";
        renderer.renderBlock(new UiRenderBlock(title, formatter.format(event), style));
    }
}
