package com.magenta.io.terminal;

import com.magenta.session.Session;
import org.jline.reader.Candidate;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Single command interface for matching, completion, help, and handling.
 * Pure interface - no collection logic. Use CommandSet for parsing, help, and completion.
 */
public interface Command {

    String name();

    String description();

    default List<Candidate> completions() {
        return List.of();
    }

    boolean matches(String raw);

    void handle(Session session, String raw);

    // === Factory methods for creating command instances ===

    static Command of(
        String name,
        String description,
        Predicate<String> matcher,
        BiConsumer<Session, String> handler
    ) {
        return of(name, description, List.of(), matcher, handler);
    }

    static Command of(
        String name,
        String description,
        List<Candidate> completions,
        Predicate<String> matcher,
        BiConsumer<Session, String> handler
    ) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public List<Candidate> completions() {
                return completions;
            }

            @Override
            public boolean matches(String raw) {
                return matcher.test(raw);
            }

            @Override
            public void handle(Session session, String raw) {
                handler.accept(session, raw);
            }
        };
    }

    static Command of(
        String name,
        String description,
        Supplier<List<Candidate>> completions,
        Predicate<String> matcher,
        BiConsumer<Session, String> handler
    ) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public List<Candidate> completions() {
                return completions.get();
            }

            @Override
            public boolean matches(String raw) {
                return matcher.test(raw);
            }

            @Override
            public void handle(Session session, String raw) {
                handler.accept(session, raw);
            }
        };
    }
}
