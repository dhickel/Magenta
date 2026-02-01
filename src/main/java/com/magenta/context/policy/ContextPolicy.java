package com.magenta.context.policy;

import com.magenta.context.model.Context;

@FunctionalInterface
public interface ContextPolicy {
    Context apply(Context context, ContextLimits limits);
}
