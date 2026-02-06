package com.magenta.io.terminal;

import com.magenta.config.Config.ColorsConfig;
import com.magenta.io.IOManager;
import com.magenta.io.OutputStyle;
import com.magenta.io.ResponseHandler;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;


public class TerminalIOManager extends IOManager {

    private static TerminalIOManager instance;

    private final org.jline.terminal.Terminal terminal;
    private final LineReader reader;
    private final PrintWriter writer;
    private final boolean colorEnabled;
    private final TerminalDisplay terminalDisplay;
    private volatile Completer activeCompleter;
    private ColorsConfig colorsConfig;
    private String cursor = "magenta> ";
    private Integer cursorColor;

    public static synchronized TerminalIOManager getInstance() throws IOException {
        if (instance == null) {
            instance = new TerminalIOManager();
        }
        return instance;
    }

    private TerminalIOManager() throws IOException {
        super();
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer((reader, line, candidates) -> {
                if (activeCompleter != null) {
                    activeCompleter.complete(reader, line, candidates);
                }
            })
            .option(LineReader.Option.AUTO_MENU, true)
            .build();
        this.writer = terminal.writer();
        this.colorEnabled = !org.jline.terminal.Terminal.TYPE_DUMB.equals(terminal.getType());
        this.terminalDisplay = new TerminalDisplay(terminal);

        // Initialize pipes with String-based I/O
        this.inputPipe = this::readRaw;
        this.outputPipe = this::printRaw;
    }


    /**
     * TerminalIOProxy that delegates all operations to the parent TerminalIOManager.
     * Allows sessions to have their own IOManager instance without closing the shared terminal.
     */
    public static class TerminalIOProxy extends IOManager {
        private final TerminalIOManager target;

        private TerminalIOProxy(TerminalIOManager target) {
            super();
            this.target = target;

            // Delegate to target pipes
            this.inputPipe = target.inputPipe();
            this.outputPipe = target.outputPipe();
        }

        // === IOManager methods (delegate to target) ===

        public TerminalDisplay display() {
            return target.display();
        }

        public void setCompleter(Completer completer) {
            target.setCompleter(completer);
        }

        public void setColorsConfig(ColorsConfig colorsConfig) {
            target.setColorsConfig(colorsConfig);
        }

        @Override
        public void setCursor(String cursor, Integer cursorColor) {
            target.setCursor(cursor, cursorColor);
        }

        @Override
        public void print(String text, int colorCode) {
            target.print(text, colorCode);
        }

        @Override
        public void printStyled(String text, OutputStyle style) {
            target.printStyled(text, style);
        }

        @Override
        public ResponseHandler createResponseHandler(Integer agentColor, int delayMs) {
            return target.createResponseHandler(agentColor, delayMs);
        }

        /**
         * Access the underlying JLine terminal (e.g., for InteractivePrompt).
         */
        public org.jline.terminal.Terminal terminal() {
            return target.terminal();
        }

        @Override
        public void close() {
            // No-op: don't close the shared terminal
        }
    }

    /**
     * Create a proxy IOManager that delegates to this terminal.
     * The proxy can be safely closed without affecting the shared terminal.
     */
    public IOManager createProxy() {
        return new TerminalIOProxy(this);
    }

    /**
     * Get terminal display utilities.
     * Used for advanced rendering (boxes, tables, multi-line updates).
     *
     * @return TerminalDisplay instance
     */
    public TerminalDisplay display() {
        return terminalDisplay;
    }

    /**
     * Set the command completer.
     * Uses a delegating pattern - no LineReader rebuild needed.
     *
     * @param completer New completer (or null to disable completion)
     */
    public void setCompleter(Completer completer) {
        this.activeCompleter = completer;
    }

    public void setColorsConfig(ColorsConfig colorsConfig) {
        this.colorsConfig = colorsConfig;
    }

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        this.cursor = cursor;
        this.cursorColor = cursorColor;
    }

    /**
     * Raw input reading - returns String.
     * Handles terminal-specific commands like /clear locally.
     */
    private String readRaw(String prompt) {
        while (true) {
            String line = readLine();
            if (line == null) {
                return "/exit";  // EOF or Ctrl-D
            }

            // Handle terminal-specific commands locally
            if (line.trim().toLowerCase().matches("^/(clear|cls).*")) {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue;  // Ask for input again
            }

            return line;
        }
    }

    /**
     * Raw output writing - takes String.
     */
    private void printRaw(String text) {
        writer.print(text);
        writer.flush();
    }

    /**
     * Read a line from terminal with styled cursor.
     */
    public String readLine() {
        String styledCursor = (cursorColor != null)
                ? styled(cursor, cursorColor)
                : styled(cursor, OutputStyle.PROMPT);
        return reader.readLine(styledCursor);
    }

    /**
     * Read a line from terminal with custom prompt.
     */
    public String readLine(String prompt) {
        return reader.readLine(styled(prompt, OutputStyle.PROMPT));
    }

    // === Output Methods with Color/Style ===

    @Override
    public void print(String text, int colorCode) {
        writer.print(styled(text, colorCode));
        writer.flush();
    }

    @Override
    public void printStyled(String text, OutputStyle style) {
        writer.print(styled(text, style));
        writer.print("\n");
        writer.flush();
    }

    public void securityAlert(String message) {
        printStyled(message, OutputStyle.SECURITY);
    }

    // === Formatting ===

    private String styled(String text, OutputStyle style) {
        if (!colorEnabled) {
            return text;
        }

        // Check config for override
        if (colorsConfig != null) {
            Integer code = colorsConfig.getColor(style.name());
            if (code != null) {
                return styled(text, code);
            }
        }

        return new AttributedString(text, style.style()).toAnsi(terminal);
    }

    private String styled(String text, int colorCode) {
        if (!colorEnabled) {
            return text;
        }
        return new AttributedString(text, AttributedStyle.DEFAULT.foreground(colorCode)).toAnsi(terminal);
    }

    // === Terminal Access ===

    public void flush() {
        terminal.flush();
    }

    public org.jline.terminal.Terminal terminal() {
        return terminal;
    }

    public int getWidth() {
        return terminal.getWidth();
    }

    @Override
    public ResponseHandler createResponseHandler(Integer agentColor, int delayMs) {
        return delayMs > 0
                ? new SmoothWriter(this, agentColor, delayMs)
                : new Writer(this, agentColor);
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }
}
