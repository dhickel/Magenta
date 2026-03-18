package io.mindspice.magenta.ui.tui.workspace;

import casciian.TWindow;
import io.mindspice.magenta.ui.tui.TuiApplication;

public interface WindowKindFactory {
    String kind();

    TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, TuiApplication app);
}
