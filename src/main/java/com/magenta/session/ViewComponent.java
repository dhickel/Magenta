package com.magenta.session;

import com.magenta.io.terminal.TerminalDisplay;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.List;

/**
 * Reusable view component that can render itself given session and display context.
 * Used for headers, footers, panels, separators, etc.
 *
 * <p>Functional interface - can be implemented as lambda for simple cases.
 */
@FunctionalInterface
public interface ViewComponent {

    /**
     * Render this component.
     *
     * @param session Current agent session
     * @param display Terminal display utilities
     * @return Rendered lines
     */
    List<AttributedString> render(AgentSession session, TerminalDisplay display);

    // === Common Components ===

    /**
     * Horizontal separator line.
     */
    static ViewComponent separator() {
        return (session, display) -> List.of(
            display.drawHorizontalLine(display.getTerminalSize().width(), '─')
        );
    }

    /**
     * Title component (bold text).
     */
    static ViewComponent title(String text) {
        return (session, display) -> List.of(
            new AttributedString(text, AttributedStyle.BOLD)
        );
    }

    /**
     * Blank line.
     */
    static ViewComponent blank() {
        return (session, display) -> List.of(new AttributedString(""));
    }

    /**
     * Static text component.
     */
    static ViewComponent text(String text) {
        return (session, display) -> List.of(new AttributedString(text));
    }

    /**
     * Styled text component.
     */
    static ViewComponent styled(String text, AttributedStyle style) {
        return (session, display) -> List.of(new AttributedString(text, style));
    }
}
