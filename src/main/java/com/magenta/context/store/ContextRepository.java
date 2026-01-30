package com.magenta.context.store;

import com.magenta.context.model.Context;

import java.util.Optional;

public interface ContextRepository {
    void save(String key, Context context);
    Optional<Context> load(String key);
    void delete(String key);
}
