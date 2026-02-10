package com.magenta.io.terminal;

import com.magenta.context.Context;
import com.magenta.session.AgentSession;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Functional interface for rendering a status bar line.
 * Used by TerminalView.Composed for positioned status display.
 *
 * <p>Static methods match the functional interface signature for use as method references:
 * <pre>
 *   .statusBar(StatusBar::full, StatusPosition.BOTTOM_RIGHT)
 *   .statusBar(StatusBar::aligned, StatusPosition.BOTTOM_RIGHT)
 *   .statusBar(StatusBar::compact, StatusPosition.BOTTOM_LEFT)
 * </pre>
 */
@FunctionalInterface
public interface StatusBar {

    /**
     * Render the status bar content.
     *
     * @param session Current agent session (provides state)
     * @param width Available terminal width
     * @return Rendered status line
     */
    AttributedString render(AgentSession session, int width);

    // === Static Renderers (method references matching this functional interface) ===

    /**
     * Full status: "Agent: name | Context: 100/1000 (10%)"
     * Color-coded by context usage (green &lt; 50%, yellow 50-80%, red &gt; 80%).
     */
    static AttributedString full(AgentSession session, int width) {
        var cm = session.magenta().contextManager();
        Context ctx = cm.loadContext(session.sessionId());
        int currentTokens = ctx.totalEstimatedTokens();
        int maxTokens = session.contextLimits().maxContext();
        double usage = maxTokens > 0 ? (double) currentTokens / maxTokens * 100 : 0;

        String text = String.format("Agent: %s | Context: %d/%d (%.0f%%)",
            session.alias().value(), currentTokens, maxTokens, usage);
        return new AttributedString(text, getUsageStyle(usage));
    }

    /**
     * Right-aligned full status bar.
     */
    static AttributedString aligned(AgentSession session, int width) {
        return alignRight(full(session, width), width);
    }

    /**
     * Compact status: just the usage percentage (e.g., "42%").
     */
    static AttributedString compact(AgentSession session, int width) {
        var cm = session.magenta().contextManager();
        Context ctx = cm.loadContext(session.sessionId());
        int currentTokens = ctx.totalEstimatedTokens();
        int maxTokens = session.contextLimits().maxContext();
        double usage = maxTokens > 0 ? (double) currentTokens / maxTokens * 100 : 0;

        return new AttributedString(String.format("%.0f%%", usage), getUsageStyle(usage));
    }

    // === Alignment Helpers ===

    /**
     * Right-align an AttributedString within terminal width.
     * Preserves original styling.
     */
    static AttributedString alignRight(AttributedString status, int width) {
        int padding = Math.max(0, width - status.length() - 1);
        var builder = new AttributedStringBuilder();
        builder.append(" ".repeat(padding));
        builder.append(status);
        return builder.toAttributedString();
    }

    // === Styling ===

    /**
     * Get style for context usage percentage.
     * Green: &lt; 50%, Yellow: 50-80%, Red: &gt; 80%.
     */
    private static AttributedStyle getUsageStyle(double usage) {
        if (usage > 80) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
        } else if (usage > 50) {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
        } else {
            return AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
        }
    }
}
