package io.mindspice.magenta2.api.web;

import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.components.navigation.SideNav;
import io.mindspice.simplypages.core.Component;

final class AppNavigation {
    static final String OPERATIONAL_CSS = "/css/orchestration.css?v=14";

    private AppNavigation() {
    }

    static Component primaryTopNav() {
        return TopNavBuilder.create()
            .withHtmxNavigation(false)
            .addPrimaryLink("Home", "/")
            .addPrimaryLink("Chat", "/chat")
            .addPrimaryLink("Agents", "/agents")
            .addPrimaryLink("Manage", "/manage")
            .build();
    }

    static SideNav operationalSideNav(String activePath) {
        SideNav nav = SideNav.create();
        nav.addSection("System");
        nav.addItem("System", "/manage", isActivePath(activePath, "/manage"));
        nav.addItem("Settings", "/settings", isActivePath(activePath, "/settings"));
        nav.addSection("Orchestration");
        nav.addItem("Plans", "/plans", isActivePath(activePath, "/plans"));
        nav.addItem("Workflows", "/workflows", isActivePath(activePath, "/workflows"));
        nav.addItem("Jobs", "/jobs", isActivePath(activePath, "/jobs"));
        nav.addItem("Projects", "/projects", isActivePath(activePath, "/projects"));
        nav.addItem("Inbox", "/inbox", isActivePath(activePath, "/inbox"));
        nav.addSection("Tools");
        nav.addItem("Skills", "/skills", isActivePath(activePath, "/skills"));
        nav.addItem("Outputs", "/outputs", isActivePath(activePath, "/outputs"));
        return nav;
    }

    private static boolean isActivePath(String activePath, String navPath) {
        return activePath != null && (activePath.equals(navPath) || activePath.startsWith(navPath + "/"));
    }
}
