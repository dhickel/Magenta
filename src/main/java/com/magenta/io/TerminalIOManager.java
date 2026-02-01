package com.magenta.io;

import com.magenta.config.Config.ColorsConfig;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;


public class TerminalIOManager extends AbstractIOManager {

    private static TerminalIOManager instance;

    private final org.jline.terminal.Terminal terminal;
    private final LineReader reader;
    private final PrintWriter writer;
    private final boolean colorEnabled;
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
        this.reader = LineReaderBuilder.builder().terminal(terminal).build();
        this.writer = terminal.writer();
        this.colorEnabled = !org.jline.terminal.Terminal.TYPE_DUMB.equals(terminal.getType());

        // Initialize pipes (raw I/O, no filtering - IOManager defaults handle that)
        this.inputPipe = this::readRaw;
        this.outputPipe = this::printRaw;
        this.colorPipe = this::applyColor;
    }


    /**
     * TerminalIOProxy that delegates all operations to the parent TerminalIOManager.
     * Allows sessions to have their own IOManager instance without closing the shared terminal.
     * Each proxy maintains its own SecurityFilter (set by the session).
     */
    public static class TerminalIOProxy extends AbstractIOManager {
        private final TerminalIOManager target;

        private TerminalIOProxy(TerminalIOManager target) {
            super(); // Initializes securityFilter to identity()
            this.target = target;

            // Delegate to target pipes
            this.inputPipe = target.inputPipe();
            this.outputPipe = target.outputPipe();
            this.colorPipe = target.colorPipe();
        }

        // === IOManager methods (delegate to target) ===

        @Override
        public void setCursor(String cursor, Integer cursorColor) {
            target.setCursor(cursor, cursorColor);
        }

        @Override
        public ResponseHandler createResponseHandler(Integer agentColor, int delayMs) {
            return target.createResponseHandler(agentColor, delayMs);
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

    public void setColorsConfig(ColorsConfig colorsConfig) {
        this.colorsConfig = colorsConfig;
    }

    @Override
    public void setCursor(String cursor, Integer cursorColor) {
        this.cursor = cursor;
        this.cursorColor = cursorColor;
    }



    /**
     * Raw input reading (no security filtering - handled by IOManager defaults).
     * Handles terminal-specific commands like /clear locally.
     */
    private Message.Input readRaw(String prompt) {
        while (true) {
            String line = readLine();
            if (line == null) {
                return Message.input("/exit");  // EOF or Ctrl-D
            }

            // Handle terminal-specific commands locally
            if (line.trim().toLowerCase().matches("^/(clear|cls).*")) {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue;  // Ask for input again
            }

            return Message.input(line);
        }
    }

    /**
     * Raw output writing (no security filtering - handled by IOManager defaults).
     * Handles different message types appropriately.
     */
    private void printRaw(Message message) {
        String text = switch (message) {
            case Message.Output(String content, Integer color) -> {
                if (color != null) {
                    yield applyColor(content, color);
                }
                yield content;
            }
            case Message.System(String content, OutputStyle style) -> {
                yield styled(content, style);
            }
            case Message.Filtered(String orig, String reason, var type, var ts) -> {
                // Filtered messages shouldn't reach here (IOManager blocks them)
                // But if they do, display the reason
                yield styled("[BLOCKED] " + reason, OutputStyle.ERROR);
            }
            case Message.Input(String content, var ts) -> {
                // Input messages shouldn't be printed, but if they are, just show content
                yield content;
            }
        };

        writer.print(text);
        writer.flush();
    }

    /**
     * Read a line from terminal with styled cursor.
     */
    public String readLine() {
        // Use configured cursor with custom color if set, otherwise use default prompt styling
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

    /**
     * Apply color formatting using ANSI codes.
     * Used by ColorPipe.
     */
    private String applyColor(String text, int colorCode) {
        return styled(text, colorCode);
    }

    // === Convenience Output Methods ===

    public void printWithStyle(String message, OutputStyle style) {
        // Get color code from config or use default
        int colorCode = OutputStyle.PROMPT.ordinal(); // Default
        if (colorsConfig != null) {
            Integer code = colorsConfig.getColor(style.name());
            if (code != null) {
                colorCode = code;
            } else {
                colorCode = style.ordinal();
            }
        } else {
            colorCode = style.ordinal();
        }
        print(message + "\n", colorCode);
    }

    public void error(String message) {
        printWithStyle(message, OutputStyle.ERROR);
    }

    public void warn(String message) {
        printWithStyle(message, OutputStyle.WARNING);
    }

    public void info(String message) {
        printWithStyle(message, OutputStyle.INFO);
    }

    public void success(String message) {
        printWithStyle(message, OutputStyle.SUCCESS);
    }

    public void agentResponse(String response) {
        printWithStyle(response, OutputStyle.AGENT);
    }

    public void agentResponse(String response, Integer agentColor) {
        if (agentColor != null) {
            print(response + "\n", agentColor);
        } else {
            printWithStyle(response, OutputStyle.AGENT);
        }
    }

    public void securityAlert(String message) {
        printWithStyle(message, OutputStyle.SECURITY);
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
