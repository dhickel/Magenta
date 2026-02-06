package com.magenta.session;

import com.magenta.io.terminal.StatusBar;
import com.magenta.io.terminal.TableRenderer;
import com.magenta.io.terminal.TerminalDisplay;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerminalViewTest {

    private TerminalDisplay display;

    @BeforeEach
    void setUp() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
            .system(false)
            .dumb(true)
            .build();
        display = new TerminalDisplay(terminal);
    }

    // === Chat View ===

    @Test
    void testChatRenderReturnsEmpty() {
        var chat = new TerminalView.Chat();
        List<AttributedString> lines = chat.render(null, display);
        assertTrue(lines.isEmpty());
    }

    @Test
    void testChatHandleInputReturnsFalse() {
        var chat = new TerminalView.Chat();
        assertFalse(chat.handleInput(null, "anything"));
    }

    @Test
    void testChatName() {
        assertEquals("chat", new TerminalView.Chat().name());
    }

    // === Table View ===

    @Test
    void testTableRender() {
        record Item(String name, String value) {}

        List<Item> items = List.of(
            new Item("key1", "val1"),
            new Item("key2", "val2")
        );

        List<TableRenderer.ColumnDef<Item>> columns = List.of(
            new TableRenderer.ColumnDef<>("Name", Item::name, TableRenderer.ColumnDef.Align.LEFT),
            new TableRenderer.ColumnDef<>("Value", Item::value, TableRenderer.ColumnDef.Align.LEFT)
        );

        var table = new TerminalView.Table<>("Test", items, columns);
        List<AttributedString> lines = table.render(null, display);

        assertFalse(lines.isEmpty());
        String content = lines.stream()
            .map(AttributedString::toString)
            .reduce("", (a, b) -> a + b);
        assertTrue(content.contains("key1"));
        assertTrue(content.contains("val1"));
    }

    @Test
    void testTableName() {
        var table = new TerminalView.Table<>("T", List.of(), List.of());
        assertEquals("table", table.name());
    }

    @Test
    void testTableEmpty() {
        var table = new TerminalView.Table<>("Empty", List.of(), List.of(
            new TableRenderer.ColumnDef<>("Col", Object::toString, TableRenderer.ColumnDef.Align.LEFT)
        ));
        List<AttributedString> lines = table.render(null, display);
        assertNotNull(lines);
    }

    // === Composed View ===

    @Test
    void testComposedWithHeaders() {
        TerminalView composed = TerminalView.builder()
            .header(ViewComponent.title("Header"))
            .content(new TerminalView.Chat())
            .build();

        List<AttributedString> lines = composed.render(null, display);
        assertFalse(lines.isEmpty());
        assertEquals("Header", lines.getFirst().toString());
    }

    @Test
    void testComposedWithFooters() {
        TerminalView composed = TerminalView.builder()
            .content(new TerminalView.Chat())
            .footer(ViewComponent.text("Footer"))
            .build();

        List<AttributedString> lines = composed.render(null, display);
        assertFalse(lines.isEmpty());
        assertEquals("Footer", lines.getLast().toString());
    }

    @Test
    void testComposedWithMultipleHeaders() {
        TerminalView composed = TerminalView.builder()
            .header("Line 1")
            .header("Line 2")
            .content(new TerminalView.Chat())
            .build();

        List<AttributedString> lines = composed.render(null, display);
        assertEquals(2, lines.size());
        assertEquals("Line 1", lines.get(0).toString());
        assertEquals("Line 2", lines.get(1).toString());
    }

    @Test
    void testComposedDelegatesToContentHandleInput() {
        TerminalView composed = TerminalView.builder()
            .content(new TerminalView.Chat())
            .build();

        // Chat returns false for all input
        assertFalse(composed.handleInput(null, "anything"));
    }

    @Test
    void testComposedName() {
        TerminalView composed = TerminalView.builder()
            .content(new TerminalView.Chat())
            .build();

        assertEquals("composed[chat]", composed.name());
    }

    // === ViewBuilder ===

    @Test
    void testViewBuilderRequiresContent() {
        assertThrows(IllegalStateException.class, () -> TerminalView.builder().build());
    }

    @Test
    void testViewBuilderStringHeader() {
        TerminalView composed = TerminalView.builder()
            .header("Hello")
            .content(new TerminalView.Chat())
            .build();

        List<AttributedString> lines = composed.render(null, display);
        assertEquals("Hello", lines.getFirst().toString());
    }

    @Test
    void testViewBuilderStringFooter() {
        TerminalView composed = TerminalView.builder()
            .content(new TerminalView.Chat())
            .footer("Goodbye")
            .build();

        List<AttributedString> lines = composed.render(null, display);
        assertEquals("Goodbye", lines.getLast().toString());
    }

    // === ViewComponent factory methods ===

    @Test
    void testViewComponentBlank() {
        var blank = ViewComponent.blank();
        List<AttributedString> lines = blank.render(null, display);
        assertEquals(1, lines.size());
        assertEquals("", lines.getFirst().toString());
    }

    @Test
    void testViewComponentText() {
        var text = ViewComponent.text("hello");
        List<AttributedString> lines = text.render(null, display);
        assertEquals(1, lines.size());
        assertEquals("hello", lines.getFirst().toString());
    }

    @Test
    void testViewComponentTitle() {
        var title = ViewComponent.title("title");
        List<AttributedString> lines = title.render(null, display);
        assertEquals(1, lines.size());
        assertEquals("title", lines.getFirst().toString());
    }

    @Test
    void testViewComponentSeparator() {
        var sep = ViewComponent.separator();
        List<AttributedString> lines = sep.render(null, display);
        assertEquals(1, lines.size());
        String line = lines.getFirst().toString();
        assertTrue(line.chars().allMatch(c -> c == '─'));
    }

    @Test
    void testViewComponentStyled() {
        var styled = ViewComponent.styled("styled", AttributedStyle.BOLD);
        List<AttributedString> lines = styled.render(null, display);
        assertEquals(1, lines.size());
        assertEquals("styled", lines.getFirst().toString());
    }

    // === StatusPosition enum ===

    @Test
    void testStatusPositionValues() {
        assertEquals(4, TerminalView.StatusPosition.values().length);
        assertNotNull(TerminalView.StatusPosition.TOP_LEFT);
        assertNotNull(TerminalView.StatusPosition.TOP_RIGHT);
        assertNotNull(TerminalView.StatusPosition.BOTTOM_LEFT);
        assertNotNull(TerminalView.StatusPosition.BOTTOM_RIGHT);
    }
}
