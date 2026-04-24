package io.mindspice.magenta2.core.util;

import java.util.function.Consumer;
import java.util.function.Function;

public interface Option<T> {
    record Some<T>(T value) implements Option<T> { }

    record None<T>() implements Option<T> { }

    default void ifPresent(Consumer<T> consumer) {
        if (this instanceof Some<T>(T val)) {
            consumer.accept(val);
        }
    }


    default <U> U mapOr(Function<T, U> mapFunc, U def) {
        return (this instanceof Some<T>(T val)) ? mapFunc.apply(val) : def;
    }

    default boolean isSome() { return this instanceof Some<T>; }

    default boolean isNone() { return this instanceof None<T>; }

    default T get() {
        return (this instanceof Some<T>(T val)) ? val : null;
    }
}
