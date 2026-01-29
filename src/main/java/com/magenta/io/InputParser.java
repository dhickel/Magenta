package com.magenta.io;


@FunctionalInterface
public interface InputParser {


    Input parse(String raw);

    static InputParser defaultParser() {
        return Input::defaultParser;
    }
}
