package com.magenta.tools;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CodeExecutionToolsTest {

    @Test
    void testCommandSplitting() throws Exception {
        CodeExecutionTools tools = new CodeExecutionTools();

        // Use reflection to test private splitCommand method
        Method splitCommand = CodeExecutionTools.class.getDeclaredMethod("splitCommand", String.class);
        splitCommand.setAccessible(true);

        // Test 1: Simple command
        @SuppressWarnings("unchecked")
        List<String> parts1 = (List<String>) splitCommand.invoke(tools, "mvn clean install");
        assertEquals(List.of("mvn", "clean", "install"), parts1);

        // Test 2: Quoted argument with spaces
        // Expect: mvn, -Dmessage=Hello World, test
        // The implementation should handle quotes appropriately.
        @SuppressWarnings("unchecked")
        List<String> parts2 = (List<String>) splitCommand.invoke(tools, "mvn \"-Dmessage=Hello World\" test");
        assertEquals(List.of("mvn", "-Dmessage=Hello World", "test"), parts2);

        // Test 3: Mixed quotes
        @SuppressWarnings("unchecked")
        List<String> parts3 = (List<String>) splitCommand.invoke(tools, "echo 'single quote' \"double quote\"");
        assertEquals(List.of("echo", "single quote", "double quote"), parts3);
    }
}
