package com.magenta.io;

import com.magenta.config.Config.ColorsConfig;
import com.magenta.security.SecurityFilter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Terminal-based IOManager for human-agent interaction.
 * Uses JLine for rich terminal features including colors and command history.
 */
public class TerminalIOManager implements IOManager {

    private final org.jline.terminal.Terminal terminal;
    private final LineReader reader;
    private final PrintWriter writer;
    private final boolean colorEnabled;
    private ColorsConfig colorsConfig;
    private SecurityFilter securityFilter;

    public TerminalIOManager() throws IOException {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.reader = LineReaderBuilder.builder().terminal(terminal).build();
        this.writer = terminal.writer();
        this.colorEnabled = !org.jline.terminal.Terminal.TYPE_DUMB.equals(terminal.getType());
        this.securityFilter = SecurityFilter.identity(); // Default: no filtering
    }

    public void setColorsConfig(ColorsConfig colorsConfig) {
        this.colorsConfig = colorsConfig;
    }

    @Override
    public SecurityFilter securityFilter() {
        return securityFilter;
    }

    @Override
    public void setSecurityFilter(SecurityFilter filter) {
        this.securityFilter = filter;
    }

    // === Input (InputPipe) ===

    @Override
    public Command read(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (line == null) {
                return new Command.Exit(); // EOF or Ctrl-D
            }

            Command cmd = Command.parse(line);

            // Handle terminal-specific commands locally
            if (cmd instanceof Command.Clear) {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue; // Ask for input again
            }

            // Apply security filter
            cmd = securityFilter.inputFilter().apply(cmd, this);

            return cmd;
        }
    }

    public String readLine() {
        return reader.readLine();
    }

    public String readLine(String prompt) {
        return reader.readLine(styled(prompt, OutputStyle.PROMPT));
    }

    // === Output (OutputPipe) ===

    @Override
    public void print(String text) {
        String filtered = securityFilter.outputFilter().apply(text);
        writer.print(filtered);
        writer.flush();
    }

    @Override
    public void print(String text, int colorCode) {
        String filtered = securityFilter.outputFilter().apply(text);
        writer.print(styled(filtered, colorCode));
        writer.flush();
    }

    @Override
    public void println(String text) {
        String filtered = securityFilter.outputFilter().apply(text);
        writer.println(filtered);
        flush();
    }

    @Override
    public void println(String text, int colorCode) {
        String filtered = securityFilter.outputFilter().apply(text);
        writer.println(styled(filtered, colorCode));
        flush();
    }

    // === Convenience Output Methods ===

    public void println(String message, OutputStyle style) {
        writer.println(styled(message, style));
        flush();
    }

    public void error(String message) {
        println(message, OutputStyle.ERROR);
    }

    public void warn(String message) {
        println(message, OutputStyle.WARNING);
    }

    public void info(String message) {
        println(message, OutputStyle.INFO);
    }

    public void success(String message) {
        println(message, OutputStyle.SUCCESS);
    }

    public void agentResponse(String response) {
        println(response, OutputStyle.AGENT);
    }

    public void agentResponse(String response, Integer agentColor) {
        if (agentColor != null) {
            println(response, agentColor);
        } else {
            println(response, OutputStyle.AGENT);
        }
    }

    public void securityAlert(String message) {
        println(message, OutputStyle.SECURITY);
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
                return new AttributedString(text, AttributedStyle.DEFAULT.foreground(code)).toAnsi(terminal);
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

    public LineReader reader() {
        return reader;
    }

    public PrintWriter writer() {
        return writer;
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
