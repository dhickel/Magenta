package com.magenta.io;

public interface OutputPipe {
    void print(String text);
    void println(String text);

    default void print(String text, int color) {
        print(text);
    }

    default void println(String text, int color) {
        println(text);
    }
}
