package com.magenta.context.policy;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;

import java.util.ArrayList;
import java.util.List;

public class TokenLimitPolicy implements ContextPolicy {
    private final int maxTokens;

    public TokenLimitPolicy(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public Context apply(Context context) {
        int currentTokens = context.totalEstimatedTokens();
        if (currentTokens <= maxTokens) {
            return context;
        }

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
            if (retainedTokens + cost <= maxTokens) {
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