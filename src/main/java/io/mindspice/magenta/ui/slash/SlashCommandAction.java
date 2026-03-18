package io.mindspice.magenta.ui.slash;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public sealed interface SlashCommandAction permits SlashCommandAction.ZeroArg,
        SlashCommandAction.OneArg,
        SlashCommandAction.OptionalOneArg,
        SlashCommandAction.TwoArg,
        SlashCommandAction.ThreeArg,
        SlashCommandAction.VarArg {

    int minArity();

    int maxArity();

    record ZeroArg(Runnable handler) implements SlashCommandAction {
        public ZeroArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int minArity() {
            return 0;
        }

        @Override
        public int maxArity() {
            return 0;
        }
    }

    record OneArg(Consumer<String> handler) implements SlashCommandAction {
        public OneArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int minArity() {
            return 1;
        }

        @Override
        public int maxArity() {
            return 1;
        }
    }

    record OptionalOneArg(Consumer<String> handler) implements SlashCommandAction {
        public OptionalOneArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int minArity() {
            return 0;
        }

        @Override
        public int maxArity() {
            return 1;
        }
    }

    record TwoArg(BiConsumer<String, String> handler) implements SlashCommandAction {
        public TwoArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int minArity() {
            return 2;
        }

        @Override
        public int maxArity() {
            return 2;
        }
    }

    record ThreeArg(TriConsumer<String, String, String> handler) implements SlashCommandAction {
        public ThreeArg {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public int minArity() {
            return 3;
        }

        @Override
        public int maxArity() {
            return 3;
        }
    }

    record VarArg(int minArity, int maxArity, Consumer<java.util.List<String>> handler) implements SlashCommandAction {
        public VarArg {
            if (minArity < 0) {
                throw new IllegalArgumentException("minArity must be >= 0");
            }
            if (maxArity < minArity) {
                throw new IllegalArgumentException("maxArity must be >= minArity");
            }
            Objects.requireNonNull(handler, "handler");
        }
    }
}
