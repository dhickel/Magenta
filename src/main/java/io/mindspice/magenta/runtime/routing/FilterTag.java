package io.mindspice.magenta.runtime.routing;

@FunctionalInterface
public interface FilterTag<T> {
    boolean passes(T other);
}
