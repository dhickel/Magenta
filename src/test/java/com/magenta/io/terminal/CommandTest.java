package com.magenta.io.terminal;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    // === Exit ===

    @Test
    void testParseExit() {
        assertCommand("/exit", Command.Exit.class);
        assertCommand("/quit", Command.Exit.class);
        assertCommand("/q", Command.Exit.class);
    }

    // === Help ===

    @Test
    void testParseHelp() {
        assertCommand("/help", Command.Help.class);
        assertCommand("/?", Command.Help.class);
    }

    // === Clear ===

    @Test
    void testParseClear() {
        assertCommand("/clear", Command.Clear.class);
        assertCommand("/cls", Command.Clear.class);
    }

    // === History ===

    @Test
    void testParseHistory() {
        assertCommand("/history", Command.History.class);
    }

    @Test
    void testParseHistoryShow() {
        Optional<Command> result = Command.tryParse("/history show 10");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.HistoryShow.class, result.get());
        assertEquals(10, ((Command.HistoryShow) result.get()).limit());
    }

    @Test
    void testParseHistoryShowInvalidNumber() {
        assertCommand("/history show abc", Command.Unknown.class);
    }

    @Test
    void testParseHistorySearch() {
        Optional<Command> result = Command.tryParse("/history search hello world");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.HistorySearch.class, result.get());
        assertEquals("hello world", ((Command.HistorySearch) result.get()).query());
    }

    // === Agent ===

    @Test
    void testParseAgent() {
        Optional<Command> result = Command.tryParse("/agent helpful");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Agent.class, result.get());
        assertEquals("helpful", ((Command.Agent) result.get()).agentName());
    }

    @Test
    void testParseAgentMissingName() {
        assertCommand("/agent", Command.Unknown.class);
        assertCommand("/agent   ", Command.Unknown.class);
    }

    // === Sessions / Agents ===

    @Test
    void testParseSessions() {
        assertCommand("/sessions", Command.Sessions.class);
    }

    @Test
    void testParseAgents() {
        assertCommand("/agents", Command.Agents.class);
    }

    // === Context ===

    @Test
    void testParseContext() {
        Optional<Command> result = Command.tryParse("/context status");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Context.class, result.get());
        assertEquals("status", ((Command.Context) result.get()).subCommand());
    }

    @Test
    void testParseContextDefaultsToStatus() {
        Optional<Command> result = Command.tryParse("/context");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Context.class, result.get());
        assertEquals("status", ((Command.Context) result.get()).subCommand());
    }

    @Test
    void testParseContextArchiveWithKey() {
        Optional<Command> result = Command.tryParse("/context archive mykey");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Context.class, result.get());
        var ctx = (Command.Context) result.get();
        assertEquals("archive", ctx.subCommand());
        assertEquals("mykey", ctx.arg());
    }

    // === Workflow Task ===

    @Test
    void testParseWorkflowTask() {
        Optional<Command> result = Command.tryParse("/task list");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.WorkflowTask.class, result.get());
        assertEquals("list", ((Command.WorkflowTask) result.get()).subCommand());
    }

    @Test
    void testParseWorkflowTaskDefaultsToList() {
        Optional<Command> result = Command.tryParse("/task");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.WorkflowTask.class, result.get());
        assertEquals("list", ((Command.WorkflowTask) result.get()).subCommand());
    }

    // === Bash ===

    @Test
    void testParseBash() {
        Optional<Command> result = Command.tryParse("!ls -la");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Bash.class, result.get());
        assertEquals("ls -la", ((Command.Bash) result.get()).command());
    }

    @Test
    void testParseBashEmpty() {
        assertCommand("!", Command.Unknown.class);
    }

    // === Message ===

    @Test
    void testParseMessage() {
        Optional<Command> result = Command.tryParse("/message agent1 hello there");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Message.class, result.get());
        var msg = (Command.Message) result.get();
        assertEquals("agent1", msg.targetAgent());
        assertEquals("hello there", msg.message());
    }

    @Test
    void testParseMessageMissingArgs() {
        assertCommand("/message", Command.Unknown.class);
        assertCommand("/message agent1", Command.Unknown.class);
    }

    // === Messages ===

    @Test
    void testParseMessages() {
        assertCommand("/messages", Command.Messages.class);
        assertCommand("/inbox", Command.Messages.class);
    }

    // === Delegate ===

    @Test
    void testParseDelegate() {
        Optional<Command> result = Command.tryParse("/delegate agent1 template1");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.Delegate.class, result.get());
        var del = (Command.Delegate) result.get();
        assertEquals("agent1", del.targetAgent());
        assertEquals("template1", del.taskTemplateKey());
    }

    @Test
    void testParseDelegateMissingArgs() {
        assertCommand("/delegate", Command.Unknown.class);
        assertCommand("/delegate agent1", Command.Unknown.class);
    }

    // === Network ===

    @Test
    void testParseNetwork() {
        assertCommand("/network", Command.Network.class);
    }

    // === Config ===

    @Test
    void testParseConfig() {
        assertCommand("/config", Command.ConfigShow.class);
        assertCommand("/cfg", Command.ConfigShow.class);
    }

    @Test
    void testParseConfigShowSection() {
        Optional<Command> result = Command.tryParse("/config show agents");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.ConfigShowSection.class, result.get());
        assertEquals("agents", ((Command.ConfigShowSection) result.get()).section());
    }

    @Test
    void testParseConfigReload() {
        assertCommand("/config reload", Command.ConfigReload.class);
    }

    // === View ===

    @Test
    void testParseView() {
        Optional<Command> result = Command.tryParse("/view dashboard");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.View.class, result.get());
        assertEquals("dashboard", ((Command.View) result.get()).viewName());
    }

    @Test
    void testParseViewMissingName() {
        assertCommand("/view", Command.Unknown.class);
    }

    @Test
    void testParseViewCaseInsensitive() {
        Optional<Command> result = Command.tryParse("/view DASHBOARD");
        assertTrue(result.isPresent());
        assertInstanceOf(Command.View.class, result.get());
        assertEquals("dashboard", ((Command.View) result.get()).viewName());
    }

    // === Dashboard shorthand ===

    @Test
    void testParseDashboard() {
        assertCommand("/dashboard", Command.Dashboard.class);
    }

    // === Unknown ===

    @Test
    void testParseUnknown() {
        assertCommand("/foo", Command.Unknown.class);
        assertCommand("/notacommand", Command.Unknown.class);
    }

    // === Non-commands ===

    @Test
    void testParseNonCommand() {
        assertTrue(Command.tryParse("hello").isEmpty());
        assertTrue(Command.tryParse("").isEmpty());
        assertTrue(Command.tryParse("   ").isEmpty());
        assertTrue(Command.tryParse(null).isEmpty());
    }

    // === CompletionProvider ===

    @Test
    void testExitHasNoCompletion() {
        var exit = new Command.Exit();
        assertSame(CompletionProvider.NONE, exit.completionProvider());
    }

    @Test
    void testViewHasCompletion() {
        var view = new Command.View("");
        var provider = view.completionProvider();
        assertNotSame(CompletionProvider.NONE, provider);
    }

    @Test
    void testContextHasCompletion() {
        var ctx = new Command.Context("", "");
        var provider = ctx.completionProvider();
        assertNotSame(CompletionProvider.NONE, provider);
    }

    @Test
    void testHistoryHasCompletion() {
        var history = new Command.History();
        var provider = history.completionProvider();
        assertNotSame(CompletionProvider.NONE, provider);
    }

    // === Helper ===

    private void assertCommand(String input, Class<? extends Command> expectedType) {
        Optional<Command> result = Command.tryParse(input);
        assertTrue(result.isPresent(), "Expected command for: " + input);
        assertInstanceOf(expectedType, result.get(),
            "Expected " + expectedType.getSimpleName() + " for: " + input);
    }
}
