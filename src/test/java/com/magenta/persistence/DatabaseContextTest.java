package com.magenta.persistence;

import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.manager.ContextManager;
import com.magenta.session.SessionId;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test append-only context storage.
 * Verifies that only new elements are saved on subsequent flushes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseContextTest {
    private static Path tempDbPath;
    private static Database database;
    private static ContextManager contextManager;

    @BeforeAll
    static void setUp() throws Exception {
        // Create temp database
        tempDbPath = Files.createTempFile("magenta-test-", ".db");

        // Initialize Database directly (no singleton)
        database = new Database(tempDbPath.toString());

        // Initialize ContextManager with database
        contextManager = new ContextManager(database);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (database != null) {
            database.close();
        }
        if (tempDbPath != null) {
            Files.deleteIfExists(tempDbPath);
        }
    }

    @Test
    @Order(1)
    void testAppendElements() {
        SessionId sessionId = SessionId.random();

        // Create context with 3 elements
        Context context = new Context(sessionId, List.of(
            new ContextElement.System("System prompt"),
            new ContextElement.User("Hello"),
            new ContextElement.Agent("Hi there!")
        ));

        // Append all elements (simulates first save)
        database.appendElements(sessionId, context.getElements(), 0);

        // Load back
        var loaded = database.load(sessionId);
        assertTrue(loaded.isPresent());
        assertEquals(3, loaded.get().getElements().size());
        assertEquals("System prompt", loaded.get().getElements().get(0).content());
        assertEquals("Hello", loaded.get().getElements().get(1).content());
        assertEquals("Hi there!", loaded.get().getElements().get(2).content());
    }

    @Test
    @Order(2)
    void testIncrementalAppend() {
        SessionId sessionId = SessionId.random();

        // Initial save: 2 elements
        List<ContextElement> initial = List.of(
            new ContextElement.User("First message"),
            new ContextElement.Agent("First response")
        );
        database.appendElements(sessionId, initial, 0);

        // Load and verify
        var loaded1 = database.load(sessionId);
        assertTrue(loaded1.isPresent());
        assertEquals(2, loaded1.get().getElements().size());

        // Incremental save: 2 more elements (starting at sequence 2)
        List<ContextElement> additional = List.of(
            new ContextElement.User("Second message"),
            new ContextElement.Agent("Second response")
        );
        database.appendElements(sessionId, additional, 2);

        // Load and verify all 4 elements
        var loaded2 = database.load(sessionId);
        assertTrue(loaded2.isPresent());
        assertEquals(4, loaded2.get().getElements().size());
        assertEquals("First message", loaded2.get().getElements().get(0).content());
        assertEquals("First response", loaded2.get().getElements().get(1).content());
        assertEquals("Second message", loaded2.get().getElements().get(2).content());
        assertEquals("Second response", loaded2.get().getElements().get(3).content());
    }

    @Test
    @Order(3)
    void testContextManagerFlush() {
        SessionId sessionId = SessionId.random();
        ContextLimits limits = new ContextLimits(100000, 80000);

        // Add initial elements via ContextManager
        contextManager.append(sessionId, new ContextElement.System("Test system"), limits);
        contextManager.append(sessionId, new ContextElement.User("Test user"), limits);

        // Flush dirty contexts (should save 2 elements)
        contextManager.flushDirtyContexts();

        // Add more elements
        contextManager.append(sessionId, new ContextElement.Agent("Test agent"), limits);
        contextManager.append(sessionId, new ContextElement.User("Another message"), limits);

        // Flush again (should only save the 2 new elements)
        contextManager.flushDirtyContexts();

        // Load directly from database
        var loaded = database.load(sessionId);
        assertTrue(loaded.isPresent());
        assertEquals(4, loaded.get().getElements().size());

        // Verify order is preserved
        assertEquals("Test system", loaded.get().getElements().get(0).content());
        assertEquals("Test user", loaded.get().getElements().get(1).content());
        assertEquals("Test agent", loaded.get().getElements().get(2).content());
        assertEquals("Another message", loaded.get().getElements().get(3).content());
    }

    @Test
    @Order(4)
    void testLoadSetsLastSavedSequence() {
        SessionId sessionId = SessionId.random();
        ContextLimits limits = new ContextLimits(100000, 80000);

        // Save some elements directly
        List<ContextElement> elements = List.of(
            new ContextElement.User("Saved message 1"),
            new ContextElement.Agent("Saved response 1"),
            new ContextElement.User("Saved message 2")
        );
        database.appendElements(sessionId, elements, 0);

        // Load via ContextManager (should track that 3 elements are already saved)
        Context loaded = contextManager.loadContext(sessionId);
        assertEquals(3, loaded.getElements().size());

        // Add one more element
        contextManager.append(sessionId, new ContextElement.Agent("New response"), limits);

        // Flush (should only save 1 new element, not all 4)
        contextManager.flushDirtyContexts();

        // Reload from database
        var reloaded = database.load(sessionId);
        assertTrue(reloaded.isPresent());
        assertEquals(4, reloaded.get().getElements().size());
        assertEquals("New response", reloaded.get().getElements().get(3).content());
    }

    @Test
    @Order(5)
    void testEmptyAppendDoesNothing() {
        SessionId sessionId = SessionId.random();

        // Append empty list (should do nothing)
        database.appendElements(sessionId, List.of(), 0);

        // Should return empty
        var loaded = database.load(sessionId);
        assertFalse(loaded.isPresent());
    }
}
