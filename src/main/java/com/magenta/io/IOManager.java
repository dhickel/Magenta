package com.magenta.io;

import com.magenta.config.Config.ColorsConfig;
import org.jline.terminal.Terminal;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;

public class IOManager implements AutoCloseable {

    private final Terminal terminal;
    private final LineReader reader;
    private final PrintWriter writer;
    private final boolean colorEnabled;
    private ColorsConfig colorsConfig;

    public IOManager() throws IOException {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.reader = LineReaderBuilder.builder().terminal(terminal).build();
        this.writer = terminal.writer();
        this.colorEnabled = !Terminal.TYPE_DUMB.equals(terminal.getType());
    }

    public void setColorsConfig(ColorsConfig colorsConfig) {
        this.colorsConfig = colorsConfig;
    }

    // === Input ===

    public String readLine() {
        return reader.readLine();
    }

    public String readLine(String prompt) {
        return reader.readLine(styled(prompt, OutputStyle.PROMPT));
    }


    // === Output ===

    public void println(String message) {
        writer.println(message);
        flush();
    }

    public void println(String message, OutputStyle style) {
        writer.println(styled(message, style));
        flush();
    }

    public void println(String message, int colorCode) {
        writer.println(styled(message, colorCode));
        flush();
    }

    public void print(String text) {
        writer.print(text);
        writer.flush();
    }

    public void print(String text, int colorCode) {
        writer.print(styled(text, colorCode));
        writer.flush();
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

    // === Streaming ===

    public Writer startStream(Integer colorCode) {
        return new Writer(this, colorCode);
    }

    public Writer startStream(Integer colorCode, int delayMs) {
        if (delayMs > 0) {
            return new SmoothWriter(this, colorCode, delayMs);
        }
        return new Writer(this, colorCode);
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

    public void flush() {
        terminal.flush();
    }

    public Terminal terminal() {
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
    public void close() throws IOException {
        terminal.close();
    }
}
