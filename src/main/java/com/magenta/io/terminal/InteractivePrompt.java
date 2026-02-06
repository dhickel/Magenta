package com.magenta.io.terminal;

import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.*;
import org.jline.consoleui.prompt.builder.*;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Type-safe wrapper around JLine ConsoleUI prompts.
 * Provides fluent builders for interactive user input with arrow key navigation.
 *
 * <p>Uses jline-console-ui module (not jline-prompt). While docs suggest
 * console-ui is deprecated, it's battle-tested and well-documented.
 * See ADR-1 in overview.
 */
public class InteractivePrompt {
    private final Terminal terminal;

    public InteractivePrompt(Terminal terminal) {
        this.terminal = terminal;
    }

    // === Builders ===

    /**
     * Create a checkbox prompt (multi-select with arrow keys).
     *
     * @param message Prompt message
     * @param items   Items to select from
     * @param labelFn Function to extract display label from item
     */
    public <T> CheckboxBuilder<T> checkbox(String message, List<T> items,
                                            Function<T, String> labelFn) {
        return new CheckboxBuilder<>(terminal, message, items, labelFn);
    }

    /**
     * Create a list prompt (single-select with arrow keys).
     *
     * @param message Prompt message
     * @param items   Items to select from
     * @param labelFn Function to extract display label from item
     */
    public <T> ListBuilder<T> list(String message, List<T> items,
                                    Function<T, String> labelFn) {
        return new ListBuilder<>(terminal, message, items, labelFn);
    }

    /**
     * Create a confirmation prompt (yes/no).
     *
     * @param message Prompt message
     */
    public ConfirmBuilder confirm(String message) {
        return new ConfirmBuilder(terminal, message);
    }

    /**
     * Create an input prompt (text entry).
     *
     * @param message Prompt message
     */
    public InputBuilder input(String message) {
        return new InputBuilder(terminal, message);
    }

    // === Checkbox Prompt ===

    /**
     * Multi-select prompt with arrow key navigation and spacebar toggle.
     *
     * <p>Navigation:
     * <ul>
     *   <li>Up/Down arrows: Move selection</li>
     *   <li>Space: Toggle checkbox</li>
     *   <li>Enter: Confirm selection</li>
     * </ul>
     */
    public static class CheckboxBuilder<T> {
        private final Terminal terminal;
        private final String message;
        private final List<T> items;
        private final Function<T, String> labelFn;
        private final Set<Integer> defaultSelectedIndices = new HashSet<>();

        CheckboxBuilder(Terminal terminal, String message, List<T> items,
                        Function<T, String> labelFn) {
            this.terminal = terminal;
            this.message = message;
            this.items = items;
            this.labelFn = labelFn;
        }

        /**
         * Set default selected items by indices.
         *
         * @param indices Zero-based indices of items to pre-select
         */
        public CheckboxBuilder<T> defaultSelected(int... indices) {
            for (int idx : indices) {
                defaultSelectedIndices.add(idx);
            }
            return this;
        }

        /**
         * Show the prompt and return selected items.
         *
         * @return List of selected items (in original order)
         * @throws IOException If prompt fails
         */
        public List<T> show() throws IOException {
            var prompt = new ConsolePrompt(terminal);
            var promptBuilder = prompt.getPromptBuilder();

            var checkboxBuilder = promptBuilder.createCheckboxPrompt()
                .name("checkbox")
                .message(message);

            for (int i = 0; i < items.size(); i++) {
                var label = labelFn.apply(items.get(i));
                var itemBuilder = checkboxBuilder.newItem(String.valueOf(i)).text(label);

                if (defaultSelectedIndices.contains(i)) {
                    itemBuilder.check();
                }

                itemBuilder.add();
            }

            checkboxBuilder.addPrompt();

            var result = prompt.prompt(promptBuilder.build());
            var checkboxResult = (CheckboxResult) result.get("checkbox");

            var selected = new ArrayList<T>();
            var selectedIds = checkboxResult.getSelectedIds();

            for (int i = 0; i < items.size(); i++) {
                if (selectedIds.contains(String.valueOf(i))) {
                    selected.add(items.get(i));
                }
            }

            return selected;
        }
    }

    // === List Prompt ===

    /**
     * Single-select prompt with arrow key navigation.
     *
     * <p>Navigation:
     * <ul>
     *   <li>Up/Down arrows: Move selection</li>
     *   <li>Enter: Confirm selection</li>
     * </ul>
     */
    public static class ListBuilder<T> {
        private final Terminal terminal;
        private final String message;
        private final List<T> items;
        private final Function<T, String> labelFn;

        ListBuilder(Terminal terminal, String message, List<T> items,
                    Function<T, String> labelFn) {
            this.terminal = terminal;
            this.message = message;
            this.items = items;
            this.labelFn = labelFn;
        }

        /**
         * Show the prompt and return selected item.
         *
         * @return Selected item, or empty if cancelled
         * @throws IOException If prompt fails
         */
        public Optional<T> show() throws IOException {
            var prompt = new ConsolePrompt(terminal);
            var promptBuilder = prompt.getPromptBuilder();

            var listBuilder = promptBuilder.createListPrompt()
                .name("list")
                .message(message);

            for (int i = 0; i < items.size(); i++) {
                var label = labelFn.apply(items.get(i));
                listBuilder.newItem(String.valueOf(i)).text(label).add();
            }

            listBuilder.addPrompt();

            var result = prompt.prompt(promptBuilder.build());
            var listResult = (ListResult) result.get("list");

            var selectedId = listResult.getSelectedId();
            if (selectedId != null) {
                int idx = Integer.parseInt(selectedId);
                return Optional.of(items.get(idx));
            }

            return Optional.empty();
        }
    }

    // === Confirm Prompt ===

    /**
     * Yes/no confirmation prompt.
     *
     * <p>Navigation:
     * <ul>
     *   <li>y/n keys: Quick answer</li>
     *   <li>Enter: Confirm default</li>
     * </ul>
     */
    public static class ConfirmBuilder {
        private final Terminal terminal;
        private final String message;
        private ConfirmChoice.ConfirmationValue defaultValue =
            ConfirmChoice.ConfirmationValue.NO;

        ConfirmBuilder(Terminal terminal, String message) {
            this.terminal = terminal;
            this.message = message;
        }

        /**
         * Set default answer to YES.
         */
        public ConfirmBuilder defaultYes() {
            this.defaultValue = ConfirmChoice.ConfirmationValue.YES;
            return this;
        }

        /**
         * Set default answer to NO.
         */
        public ConfirmBuilder defaultNo() {
            this.defaultValue = ConfirmChoice.ConfirmationValue.NO;
            return this;
        }

        /**
         * Show the prompt and return answer.
         *
         * @return true if YES, false if NO
         * @throws IOException If prompt fails
         */
        public boolean show() throws IOException {
            var prompt = new ConsolePrompt(terminal);
            var promptBuilder = prompt.getPromptBuilder();

            promptBuilder.createConfirmPromp()
                .name("confirm")
                .message(message)
                .defaultValue(defaultValue)
                .addPrompt();

            var result = prompt.prompt(promptBuilder.build());
            var confirmResult = (ConfirmResult) result.get("confirm");

            return confirmResult.getConfirmed() == ConfirmChoice.ConfirmationValue.YES;
        }
    }

    // === Input Prompt ===

    /**
     * Text input prompt with optional masking.
     *
     * <p>Features:
     * <ul>
     *   <li>Optional default value</li>
     *   <li>Optional password masking</li>
     * </ul>
     */
    public static class InputBuilder {
        private final Terminal terminal;
        private final String message;
        private String defaultValue;
        private boolean mask = false;

        InputBuilder(Terminal terminal, String message) {
            this.terminal = terminal;
            this.message = message;
        }

        /**
         * Set default input value.
         */
        public InputBuilder defaultValue(String value) {
            this.defaultValue = value;
            return this;
        }

        /**
         * Enable password masking (displays * instead of characters).
         */
        public InputBuilder masked() {
            this.mask = true;
            return this;
        }

        /**
         * Show the prompt and return input.
         *
         * @return User input string
         * @throws IOException If prompt fails
         */
        public String show() throws IOException {
            var prompt = new ConsolePrompt(terminal);
            var promptBuilder = prompt.getPromptBuilder();

            var inputBuilder = promptBuilder.createInputPrompt()
                .name("input")
                .message(message);

            if (defaultValue != null) {
                inputBuilder.defaultValue(defaultValue);
            }

            if (mask) {
                inputBuilder.mask('*');
            }

            inputBuilder.addPrompt();

            var result = prompt.prompt(promptBuilder.build());
            var inputResult = (InputResult) result.get("input");
            return inputResult.getResult();
        }
    }
}
