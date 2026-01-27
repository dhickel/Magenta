package com.magenta.config;



public enum Arg {


    CONFIG;

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

