package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Planning and reasoning tools for multi-step task execution.
 * Enables agents to decompose tasks, track progress, and maintain working memory.
 */
public class PlanningTools {

    private final String sessionId;
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();
    private final Map<String, String> scratchpad = new ConcurrentHashMap<>();

    public PlanningTools(String sessionId) {
        this.sessionId = sessionId;
    }

    @Tool("Create a plan by decomposing a goal into numbered steps. stepsJson example: '[\"Step 1\", \"Step 2\"]'")
    public String createPlan(String goal, String stepsJson) {
        try {
            List<String> steps = parseSteps(stepsJson);

            String planId = UUID.randomUUID().toString().substring(0, 8);
            Plan plan = new Plan(planId, goal, steps);
            plans.put(planId, plan);

            return String.format(
                "Plan created (ID: %s)\nGoal: %s\nSteps:\n%s",
                planId, goal, formatSteps(steps)
            );
        } catch (Exception e) {
            return "Error creating plan: " + e.getMessage();
        }
    }

    @Tool("Mark a plan step as completed")
    public String completeStep(String planId, int stepNumber) {
        Plan plan = plans.get(planId);
        if (plan == null) {
            return "Error: Plan not found: " + planId;
        }

        if (stepNumber < 1 || stepNumber > plan.steps.size()) {
            return "Error: Invalid step number. Valid range: 1-" + plan.steps.size();
        }

        plan.completed.add(stepNumber - 1);

        return String.format(
            "Step %d completed. Progress: %d/%d steps",
            stepNumber, plan.completed.size(), plan.steps.size()
        );
    }

    @Tool("Get current plan status and next steps")
    public String planStatus(String planId) {
        Plan plan = plans.get(planId);
        if (plan == null) {
            return "Error: Plan not found: " + planId;
        }

        StringBuilder status = new StringBuilder();
        status.append("Goal: ").append(plan.goal).append("\n\n");
        status.append("Progress: ").append(plan.completed.size())
              .append("/").append(plan.steps.size()).append(" steps\n\n");

        for (int i = 0; i < plan.steps.size(); i++) {
            boolean done = plan.completed.contains(i);
            String marker = done ? "[✓]" : "[ ]";
            status.append(marker).append(" ").append(i + 1).append(". ")
                  .append(plan.steps.get(i)).append("\n");
        }

        // Highlight next step
        if (plan.completed.size() < plan.steps.size()) {
            int nextStep = findNextIncompleteStep(plan);
            status.append("\nNext: ").append(plan.steps.get(nextStep));
        } else {
            status.append("\n✓ All steps completed!");
        }

        return status.toString();
    }

    @Tool("Write to scratchpad memory for temporary notes and observations")
    public String scratchpadWrite(String key, String value) {
        scratchpad.put(key, value);
        return "Saved to scratchpad: " + key;
    }

    @Tool("Read from scratchpad memory")
    public String scratchpadRead(String key) {
        String value = scratchpad.get(key);
        if (value == null) {
            return "No entry found for: " + key;
        }
        return value;
    }

    @Tool("List all scratchpad entries")
    public String scratchpadList() {
        if (scratchpad.isEmpty()) {
            return "Scratchpad is empty";
        }

        StringBuilder list = new StringBuilder("Scratchpad entries:\n");
        scratchpad.forEach((key, value) -> {
            String preview = value.length() > 100
                ? value.substring(0, 100) + "..."
                : value;
            list.append("- ").append(key).append(": ").append(preview).append("\n");
        });

        return list.toString();
    }

    private List<String> parseSteps(String stepsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(stepsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});
        } catch (Exception e) {
            // Fallback for simple comma separated string if not valid JSON array
            return Arrays.asList(stepsJson.replaceAll("[\\[\\]\"]", "").split(","));
        }
    }

    private String formatSteps(List<String> steps) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            formatted.append((i + 1)).append(". ").append(steps.get(i)).append("\n");
        }
        return formatted.toString();
    }

    private int findNextIncompleteStep(Plan plan) {
        for (int i = 0; i < plan.steps.size(); i++) {
            if (!plan.completed.contains(i)) {
                return i;
            }
        }
        return plan.steps.size() - 1;
    }

    private static class Plan {
        final String id;
        final String goal;
        final List<String> steps;
        final Set<Integer> completed = ConcurrentHashMap.newKeySet();

        Plan(String id, String goal, List<String> steps) {
            this.id = id;
            this.goal = goal;
            this.steps = new ArrayList<>(steps);
        }
    }
}
