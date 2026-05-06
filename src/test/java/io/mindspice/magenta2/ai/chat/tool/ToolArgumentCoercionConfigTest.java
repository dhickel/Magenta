package io.mindspice.magenta2.ai.chat.tool;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.JsonParser;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgumentCoercionConfigTest {

    @Test
    void scalarIsCoercedToSingleElementList() {
        new ToolArgumentCoercionConfig().enableSingleValueAsArray();

        List<String> result = JsonParser.fromJson("\"None.\"",
            new TypeReference<List<String>>() {}.getType());

        assertThat(result).containsExactly("None.");
    }

    @Test
    void actualArraysPassThrough() {
        new ToolArgumentCoercionConfig().enableSingleValueAsArray();

        List<String> result = JsonParser.fromJson("[\"a\", \"b\"]",
            new TypeReference<List<String>>() {}.getType());

        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void integerScalarCoercedToSingleElementList() {
        new ToolArgumentCoercionConfig().enableSingleValueAsArray();

        List<Integer> result = JsonParser.fromJson("42",
            new TypeReference<List<Integer>>() {}.getType());

        assertThat(result).containsExactly(42);
    }
}
