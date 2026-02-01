package com.magenta.context.policy;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;

import java.util.ArrayList;
import java.util.List;

public class TokenLimitPolicy implements ContextPolicy {

    @Override
    public Context apply(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        int targetTokens = limits.maxContext();

        // If we exceed the compaction threshold, we aim to reduce to that threshold to free up space.
        // If we strictly exceed maxContext, we must reduce to at least maxContext.
        // By setting target to compactThreshold when exceeded, we cover both cases 
        // (assuming compactThreshold <= maxContext).
        if (currentTokens > limits.compactThreshold()) {
            targetTokens = limits.compactThreshold();
        } else if (currentTokens > limits.maxContext()) {
            targetTokens = limits.maxContext();
        } else {
            return context;
        }

        // Safety check: never exceed maxContext
        targetTokens = Math.min(targetTokens, limits.maxContext());

        List<ContextElement> elements = new ArrayList<>(context.getElements());
        List<ContextElement> keptTail = new ArrayList<>();
        ContextElement systemElement = null;

        if (!elements.isEmpty() && elements.get(0) instanceof ContextElement.System) {
            systemElement = elements.get(0);
        }

        int retainedTokens = (systemElement != null) ? systemElement.estimatedTokens() : 0;
        
        for (int i = elements.size() - 1; i >= 0; i--) {
            ContextElement e = elements.get(i);
            if (e == systemElement) continue;

            int cost = e.estimatedTokens();
            if (retainedTokens + cost <= targetTokens) {
                keptTail.add(0, e);
                retainedTokens += cost;
            } else {
                break; 
            }
        }

        List<ContextElement> newElements = new ArrayList<>();
        if (systemElement != null) {
            newElements.add(systemElement);
        }
        newElements.addAll(keptTail);

        context.setElements(newElements);
        return context;
    }
}
