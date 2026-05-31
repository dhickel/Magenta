package io.mindspice.magenta2.api.web;

import java.util.List;

import io.mindspice.simplypages.components.Div;
import io.mindspice.simplypages.components.forms.Button;
import io.mindspice.simplypages.core.Component;
import io.mindspice.simplypages.core.HtmlTag;
import io.mindspice.simplypages.core.RenderContext;
import io.mindspice.simplypages.core.Slot;
import io.mindspice.simplypages.core.SlotKey;
import io.mindspice.simplypages.core.Template;
import io.mindspice.simplypages.core.TemplateComponent;

final class HomeDashboardTemplates {
    private static final SlotKey<Component> SELECTOR_ITEMS = SlotKey.of("home_dashboard_selector_items");
    private static final SlotKey<String> PANEL_NOTE = SlotKey.of("home_dashboard_panel_note");
    private static final SlotKey<Component> PANEL_ACTION = SlotKey.of("home_dashboard_panel_action");
    private static final SlotKey<Component> PANEL_GRID = SlotKey.of("home_dashboard_panel_grid");

    private static final Template DASHBOARD_SELECTOR = Template.of(
        new Div()
            .withId("dashboard-selector")
            .withClass("dashboard-selector")
            .withAttribute("data-dashboard-selector", "true")
            .withChild(Slot.of(SELECTOR_ITEMS))
            .withChild(Button.create("+")
                .withClass("dashboard-create-button")
                .withAttribute("type", "button")
                .withAttribute("title", "Create dashboard")
                .withAttribute("aria-label", "Create dashboard")
                .withAttribute("hx-get", "/dashboards/_create")
                .withAttribute("hx-target", "#avatar-edit-container")
                .withAttribute("hx-swap", "innerHTML"))
    );

    private static final Template DASHBOARD_PANEL_BODY = Template.of(
        new Div()
            .withChild(new Div().withClass("avatar-shell-strip")
                .withChild(new HtmlTag("span").withClass("avatar-shell-note").withInnerText(PANEL_NOTE))
                .withChild(Slot.of(PANEL_ACTION)))
            .withChild(new Div().withClass("avatar-dashboard-panel")
                .withChild(Slot.of(PANEL_GRID)))
    );

    private HomeDashboardTemplates() {
    }

    static Component dashboardSelector(Component items) {
        return TemplateComponent.of(DASHBOARD_SELECTOR, DashboardSelectorSlots.of(items).renderContext());
    }

    static Component dashboardPanel(String dashboardId, String note, Component action, Component grid) {
        return new Div()
            .withId("dashboard-panel")
            .withClass("avatar-tab-panel avatar-tab-panel-dashboard")
            .withAttribute("data-dashboard-panel", dashboardId == null ? "" : dashboardId)
            .withChild(TemplateComponent.of(
                DASHBOARD_PANEL_BODY,
                DashboardPanelSlots.of(note, action, grid).renderContext()
            ));
    }

    record DashboardSelectorSlots(Component items) {
        static DashboardSelectorSlots of(Component items) {
            return new DashboardSelectorSlots(items == null ? ComponentList.of(List.of()) : items);
        }

        RenderContext renderContext() {
            return RenderContext.builder()
                .with(SELECTOR_ITEMS, items)
                .build();
        }
    }

    record DashboardPanelSlots(String note, Component action, Component grid) {
        static DashboardPanelSlots of(String note, Component action, Component grid) {
            return new DashboardPanelSlots(
                note == null ? "" : note,
                action == null ? ComponentList.of(List.of()) : action,
                grid == null ? ComponentList.of(List.of()) : grid
            );
        }

        RenderContext renderContext() {
            return RenderContext.builder()
                .with(PANEL_NOTE, note)
                .with(PANEL_ACTION, action)
                .with(PANEL_GRID, grid)
                .build();
        }
    }

    record ComponentList(List<Component> components) implements Component {
        static ComponentList of(List<? extends Component> components) {
            return new ComponentList(List.copyOf(components == null ? List.of() : components));
        }

        @Override
        public String render(RenderContext context) {
            StringBuilder html = new StringBuilder();
            for (Component component : components) {
                if (component != null) {
                    html.append(component.render(context));
                }
            }
            return html.toString();
        }
    }
}
