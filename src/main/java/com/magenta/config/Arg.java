package com.magenta.config;


import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public enum Arg {
    CONFIG;


    public static Map<Arg, Value> parseAll(String[] args) {
        if (args.length == 0) { return new HashMap<>(); }

        Consumer<Integer> checkLength = idx -> {
            if (idx > args.length) { throw new IllegalStateException("Argument must have value, index: " + idx); }
        };


        Function<Integer, Arg.Value> parseFlag = idx -> {
             switch(args[idx]) {
                 default -> throw new IllegalStateException("Unexpected value: " + args[idx]);
             }
        };

        Function<Integer, Arg.Value> parseFloat = idx -> {
            checkLength.accept(idx + 1);
            float val = java.lang.Float.parseFloat(args[idx + 1]);
            return Arg.Value.Float.of(val);
        };

        Function<Integer, Arg.Value> parseInt = idx -> {
            checkLength.accept(idx + 1);
            int val = java.lang.Integer.parseInt(args[idx + 1]);
            return Arg.Value.Int.of(val);
        };

        Function<Integer, Arg.Value> parseString = idx -> {
            checkLength.accept(idx + 1);
            String val = args[idx + 1];
            return Arg.Value.String.of(val);
        };




        Map<Arg, Value> argMap = new HashMap<>((args.length / 2) + 2);
        for (int i = 0; i < args.length; ++i) {

            switch (args[i]) {
                case "--config" -> {
                    argMap.put(Arg.CONFIG, parseString.apply(i));
                    i++;
                }
                default -> throw new IllegalStateException("Invalid argument: " + args[i]);
            }
        }

        // Ensure we always have our base config if no other config is passed
        argMap.computeIfAbsent(Arg.CONFIG, (_) -> Arg.Value.String.of("config.json"));

        return argMap;
    }


    public enum Flag {
    }

    public sealed interface Value {
        record String(java.lang.String value) implements Value {
            public static String of(java.lang.String value) { return new String(value); }
        }

        record Int(int value) implements Value {
            public static Int of(int value) { return new Int(value); }
        }

        record Float(float value) implements Value {
            public static Float of(float value) { return new Float(value); }
        }

        record Flag(Flag value) implements Value {
            public static Flag of(Flag value) { return new Flag(value); }

            public static Flag fromString(java.lang.String str) {
                switch (str) {
                    default -> throw new IllegalStateException("Invalid argument: " + str);
                }
            }
        }

        default java.lang.String getString() {
            return switch (this) {
                case String arg -> arg.value;
                default -> throw new IllegalStateException("Wrong value type");
            };
        }

        default float getFloat() {
            return switch (this) {
                case Float arg -> arg.value;
                default -> throw new IllegalStateException("Wrong value type");
            };
        }

        default int getInt() {
            return switch (this) {
                case Int arg -> arg.value;
                default -> throw new IllegalStateException("Wrong value type");
            };
        }

        default Flag getFlag() {
            return switch (this) {
                case Flag arg -> arg.value;
                default -> throw new IllegalStateException("Wrong value type");
            };
        }
    }

}

