package com.magenta.session;

import com.magenta.context.Context;
import com.magenta.context.ContextLimits;
import com.magenta.context.ContextManager;
import com.magenta.io.terminal.StatusBar;
import com.magenta.io.terminal.TableRenderer;
import com.magenta.io.terminal.TerminalDisplay;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Sealed interface for terminal view modes.
 * Each view defines how to render session state and handle input.
 */
public sealed interface TerminalView
    permits TerminalView.Chat, TerminalView.Dashboard, TerminalView.Table, TerminalView.Composed {

    /**
     * Render this view to AttributedString lines.
     *
     * @param session Current agent session (provides state)
     * @param display Terminal display utilities
     * @return Lines to display
     */
    List<AttributedString> render(AgentSession session, TerminalDisplay display);

    /**
     * Handle input in this view.
     *
     * @param session Current agent session
     * @param input User input
     * @return true if view handled input, false to pass to default handler
     */
    boolean handleInput(AgentSession session, String input);

    /**
     * View name for debugging.
     */
    String name();

    // === Builder ===

    /**
     * Create a new ViewBuilder for composing views.
     */
    static ViewBuilder builder() {
        return new ViewBuilder();
    }

    // === View Implementations ===

    /**
     * Default conversational chat view.
     * No overlay rendering, pass-through input handling.
     */
    final class Chat implements TerminalView {
        @Override
        public List<AttributedString> render(AgentSession session, TerminalDisplay display) {
            return List.of(); // No overlay, just streaming chat
        }

        @Override
        public boolean handleInput(AgentSession session, String input) {
            return false; // Let default handler process
        }

        @Override
        public String name() {
            return "chat";
        }
    }

    /**
     * Dashboard view - shows context stats, agent info, active tasks.
     */
    final class Dashboard implements TerminalView {
        @Override
        public List<AttributedString> render(AgentSession session, TerminalDisplay display) {
            List<AttributedString> lines = new ArrayList<>();

            // Title
            lines.add(new AttributedString("=== Magenta Dashboard ===", AttributedStyle.BOLD));
            lines.add(new AttributedString(""));

            // Context stats
            ContextManager cm = ContextManager.getInstance();
            Context ctx = cm.loadContext(session.sessionId());
            ContextLimits limits = session.contextLimits();
            int tokens = ctx.totalEstimatedTokens();
            int maxTokens = limits.maxContext();
            double usage = maxTokens > 0 ? (double) tokens / maxTokens * 100 : 0;

            AttributedStyle contextStyle = usage > 80
                ? AttributedStyle.DEFAULT.foreground(1) // RED
                : usage > 50
                    ? AttributedStyle.DEFAULT.foreground(3) // YELLOW
                    : AttributedStyle.DEFAULT.foreground(2); // GREEN

            lines.add(new AttributedString(String.format("Context: %d/%d tokens (%.1f%%)",
                tokens, maxTokens, usage), contextStyle));

            // Agent info
            lines.add(new AttributedString("Agent: " + session.agent().config().name(),
                AttributedStyle.DEFAULT.foreground(5))); // MAGENTA

            // Active task (if any)
            if (session.currentWorkflowTask() != null) {
                lines.add(new AttributedString("Task: " + session.currentWorkflowTask().name(),
                    AttributedStyle.DEFAULT.foreground(3))); // YELLOW
            }

            lines.add(new AttributedString(""));
            lines.add(new AttributedString("Commands: /view chat | /exit-dashboard",
                AttributedStyle.DEFAULT.faint()));

            return lines;
        }

        @Override
        public boolean handleInput(AgentSession session, String input) {
            if (input.equals("/exit-dashboard") || input.equals("/view chat")) {
                session.setView(new Chat());
                return true;
            }
            return false;
        }

        @Override
        public String name() {
            return "dashboard";
        }
    }

    /**
     * Table view - displays data in formatted tables.
     *
     * @param <T> Row item type
     */
    final class Table<T> implements TerminalView {
        private final String title;
        private final List<T> items;
        private final List<TableRenderer.ColumnDef<T>> columns;

        public Table(String title, List<T> items, List<TableRenderer.ColumnDef<T>> columns) {
            this.title = title;
            this.items = items;
            this.columns = columns;
        }

        @Override
        public List<AttributedString> render(AgentSession session, TerminalDisplay display) {
            String table = TableRenderer.renderTableWithBorder(title, items, columns);
            return table.lines()
                .map(AttributedString::new)
                .toList();
        }

        @Override
        public boolean handleInput(AgentSession session, String input) {
            if (input.equals("/exit-table") || input.equals("/view chat")) {
                session.setView(new Chat());
                return true;
            }
            return false;
        }

        @Override
        public String name() {
            return "table";
        }
    }

    /**
     * Composed view built with ViewBuilder.
     * Supports headers, footers, status bar positioning.
     */
    final class Composed implements TerminalView {
        private final TerminalView content;
        private final List<ViewComponent> headers;
        private final List<ViewComponent> footers;
        private final StatusBar statusBar;
        private final StatusPosition statusPosition;

        Composed(TerminalView content, List<ViewComponent> headers, List<ViewComponent> footers,
                 StatusBar statusBar, StatusPosition statusPosition) {
            this.content = content;
            this.headers = List.copyOf(headers);
            this.footers = List.copyOf(footers);
            this.statusBar = statusBar;
            this.statusPosition = statusPosition;
        }

        @Override
        public List<AttributedString> render(AgentSession session, TerminalDisplay display) {
            List<AttributedString> lines = new ArrayList<>();
            TerminalDisplay.Size size = display.getTerminalSize();

            // Status bar at top (if applicable)
            if (statusBar != null && isTopPosition(statusPosition)) {
                AttributedString status = statusBar.render(session, size.width());
                lines.add(positionStatus(status, statusPosition, size));
            }

            // Render headers (displayed under prompt, above content)
            for (ViewComponent header : headers) {
                lines.addAll(header.render(session, display));
            }

            // Render content
            lines.addAll(content.render(session, display));

            // Render footers
            for (ViewComponent footer : footers) {
                lines.addAll(footer.render(session, display));
            }

            // Status bar at bottom (if applicable)
            if (statusBar != null && !isTopPosition(statusPosition)) {
                AttributedString status = statusBar.render(session, size.width());
                lines.add(positionStatus(status, statusPosition, size));
            }

            return lines;
        }

        @Override
        public boolean handleInput(AgentSession session, String input) {
            return content.handleInput(session, input);
        }

        @Override
        public String name() {
            return "composed[" + content.name() + "]";
        }

        private static boolean isTopPosition(StatusPosition position) {
            return position == StatusPosition.TOP_LEFT || position == StatusPosition.TOP_RIGHT;
        }

        private AttributedString positionStatus(AttributedString status,
                                                StatusPosition position, TerminalDisplay.Size size) {
            return switch (position) {
                case TOP_LEFT, BOTTOM_LEFT -> status;
                case TOP_RIGHT, BOTTOM_RIGHT -> StatusBar.alignRight(status, size.width());
            };
        }
    }

    /**
     * Status bar positioning options.
     */
    enum StatusPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /**
     * Builder for composing views with headers, footers, status bars.
     */
    final class ViewBuilder {
        private TerminalView contentView;
        private final List<ViewComponent> headers = new ArrayList<>();
        private final List<ViewComponent> footers = new ArrayList<>();
        private StatusBar statusBar;
        private StatusPosition statusPosition = StatusPosition.BOTTOM_RIGHT;

        /**
         * Set the main content view.
         */
        public ViewBuilder content(TerminalView view) {
            this.contentView = view;
            return this;
        }

        /**
         * Add a header component (rendered under prompt, above content).
         */
        public ViewBuilder header(ViewComponent component) {
            this.headers.add(component);
            return this;
        }

        /**
         * Add a static header string.
         */
        public ViewBuilder header(String text) {
            return header(ViewComponent.text(text));
        }

        /**
         * Add a footer component (rendered below content, above status).
         */
        public ViewBuilder footer(ViewComponent component) {
            this.footers.add(component);
            return this;
        }

        /**
         * Add a static footer string.
         */
        public ViewBuilder footer(String text) {
            return footer(ViewComponent.text(text));
        }

        /**
         * Set status bar and position.
         */
        public ViewBuilder statusBar(StatusBar statusBar, StatusPosition position) {
            this.statusBar = statusBar;
            this.statusPosition = position;
            return this;
        }

        /**
         * Build the composed view.
         */
        public TerminalView build() {
            if (contentView == null) {
                throw new IllegalStateException("Content view is required");
            }
            return new Composed(contentView, headers, footers, statusBar, statusPosition);
        }
    }
}
