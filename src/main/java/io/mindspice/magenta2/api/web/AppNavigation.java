package io.mindspice.magenta2.api.web;

import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.core.Component;

final class AppNavigation {
    private AppNavigation() {
    }

    static Component primaryTopNav() {
        return TopNavBuilder.create()
            .withHtmxNavigation(false)
            .addPrimaryLink("Home", "/")
            .addPrimaryLink("Dashboard", "/dashboard")
            .addPrimaryLink("Chat", "/chat")
            .build();
    }
}
