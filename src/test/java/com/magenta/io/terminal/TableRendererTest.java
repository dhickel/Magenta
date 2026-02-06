package com.magenta.io.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableRendererTest {

    record Person(String name, int age, String city) {}

    @Test
    void testRenderTable() {
        List<Person> people = List.of(
            new Person("Alice", 30, "NYC"),
            new Person("Bob", 25, "LA")
        );

        List<TableRenderer.ColumnDef<Person>> columns = List.of(
            new TableRenderer.ColumnDef<>("Name", Person::name, TableRenderer.ColumnDef.Align.LEFT),
            new TableRenderer.ColumnDef<>("Age", p -> String.valueOf(p.age()), TableRenderer.ColumnDef.Align.RIGHT),
            new TableRenderer.ColumnDef<>("City", Person::city, TableRenderer.ColumnDef.Align.LEFT)
        );

        String table = TableRenderer.renderTable(people, columns);

        assertNotNull(table);
        assertTrue(table.contains("Alice"));
        assertTrue(table.contains("Bob"));
        assertTrue(table.contains("Name"));
        assertTrue(table.contains("Age"));
        assertTrue(table.contains("City"));
    }

    @Test
    void testRenderTableWithBorder() {
        List<Person> people = List.of(new Person("Alice", 30, "NYC"));

        List<TableRenderer.ColumnDef<Person>> columns = List.of(
            new TableRenderer.ColumnDef<>("Name", Person::name, TableRenderer.ColumnDef.Align.LEFT)
        );

        String table = TableRenderer.renderTableWithBorder("People", people, columns);

        assertTrue(table.contains("People"));
        assertTrue(table.contains("┌"));
        assertTrue(table.contains("├"));
        assertTrue(table.contains("┐"));
        assertTrue(table.contains("┤"));
    }

    @Test
    void testRenderIndexedTable() {
        List<String> items = List.of("Apple", "Banana", "Cherry");

        String table = TableRenderer.renderIndexedTable(
            "Fruits",
            "Fruit",
            items,
            fruit -> fruit
        );

        assertTrue(table.contains("Fruits"));
        assertTrue(table.contains("Index"));
        assertTrue(table.contains("1"));
        assertTrue(table.contains("Apple"));
        assertTrue(table.contains("Banana"));
    }

    @Test
    void testRenderTableEmpty() {
        List<Person> people = List.of();

        List<TableRenderer.ColumnDef<Person>> columns = List.of(
            new TableRenderer.ColumnDef<>("Name", Person::name, TableRenderer.ColumnDef.Align.LEFT)
        );

        String table = TableRenderer.renderTable(people, columns);
        assertNotNull(table);
        assertTrue(table.contains("Name"));
    }

    @Test
    void testRenderTableAllAlignments() {
        List<Person> people = List.of(new Person("Alice", 30, "NYC"));

        List<TableRenderer.ColumnDef<Person>> columns = List.of(
            new TableRenderer.ColumnDef<>("Left", Person::name, TableRenderer.ColumnDef.Align.LEFT),
            new TableRenderer.ColumnDef<>("Right", p -> String.valueOf(p.age()), TableRenderer.ColumnDef.Align.RIGHT),
            new TableRenderer.ColumnDef<>("Center", Person::city, TableRenderer.ColumnDef.Align.CENTER)
        );

        String table = TableRenderer.renderTable(people, columns);

        assertNotNull(table);
        assertTrue(table.contains("Alice"));
        assertTrue(table.contains("30"));
        assertTrue(table.contains("NYC"));
    }
}
