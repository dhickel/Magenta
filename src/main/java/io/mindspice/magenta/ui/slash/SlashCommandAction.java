package io.mindspice.magenta.ui.slash;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public sealed interface SlashCommandAction permits SlashCommandAction.ZeroArg, SlashCommandAction.OneArg, SlashCommandAction.TwoArg, SlashCommandAction.ThreeArg {

    int arity();

    record ZeroArg(Runnable handler) implements SlashCommandAction {
        public ZeroArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int arity() {
            return 0;
        }
    }

    record OneArg(Consumer<String> handler) implements SlashCommandAction {
        public OneArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int arity() {
            return 1;
        }
    }

    record TwoArg(BiConsumer<String, String> handler) implements SlashCommandAction {
        public TwoArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int arity() {
            return 2;
        }
    }

    record ThreeArg(TriConsumer<String, String, String> handler) implements SlashCommandAction {
        public ThreeArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int arity() {
            return 3;
        }
    }
}
