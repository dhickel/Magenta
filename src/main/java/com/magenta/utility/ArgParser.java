package com.magenta.utility;

import com.magenta.config.Arg;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ArgParser {


    public static Map<Arg, Arg.Value> parseArgs(String[] args) {
        if (args.length == 0) { return new HashMap<>(); }

        Map<Arg, Arg.Value> argMap = new HashMap<>((args.length / 2) + 2);
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--config" -> argMap.put(Arg.CONFIG, parseValue(i, args, Arg.Value.String.of(null)));
                default -> throw new IllegalStateException("Invalid argument: " + args[i]);
            }
        }

        return argMap;
    }

    private static Arg.Value parseValue(int index, String[] args, Arg.Value argType) {
        Consumer<Integer> checkLength = (i -> {
            if (index > args.length) {
                throw new IllegalStateException("Argument must have value, index: " + index);
            }
        });

        return switch (argType) {
            case Arg.Value.Flag flagArg -> Arg.Value.Flag.fromString(args[index]);

            case Arg.Value.Float floatArg -> {
                checkLength.accept(index + 1);
                float val = java.lang.Float.parseFloat(args[index + 1]);
                yield Arg.Value.Float.of(val);
            }
            case Arg.Value.Int intArg -> {
                checkLength.accept(index + 1);
                int val = Integer.parseInt(args[index + 1]);
                yield Arg.Value.Int.of(val);
            }
            case Arg.Value.String stringArg -> {
                checkLength.accept(index + 1);
                yield Arg.Value.String.of(args[index + 1]);
            }
        };
    }

}
