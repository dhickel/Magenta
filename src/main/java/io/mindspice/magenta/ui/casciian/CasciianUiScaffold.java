package io.mindspice.magenta.ui.casciian;

import casciian.TApplication;
import casciian.TEditor;
import casciian.TField;
import casciian.TKeypress;
import casciian.TPanel;
import casciian.TSplitPane;
import casciian.TText;
import casciian.TWindow;
import casciian.event.TKeypressEvent;
import casciian.event.TResizeEvent;

import java.io.UnsupportedEncodingException;

/**
 * Runnable Casciian scaffold that mirrors Magenta's current pane structure.
 * This is an implementation seed for the full runtime port.
 */
public final class CasciianUiScaffold {

    private CasciianUiScaffold() {
    }

    public static void main(String[] args) throws Exception {
        ScaffoldApp app = new ScaffoldApp();
        app.run();
    }

    static final class ScaffoldApp extends TApplication {
        private final CasciianLayoutSpec layoutSpec = CasciianLayoutSpec.defaults();
        private ScaffoldWindow rootWindow;

        ScaffoldApp() throws UnsupportedEncodingException {
            super(BackendType.XTERM);
            CasciianTheme.applyDarkMinimal(getTheme());
            this.rootWindow = new ScaffoldWindow(this, layoutSpec);
            this.rootWindow.activate();
        }
    }

    static final class ScaffoldWindow extends TWindow {
        private final CasciianLayoutSpec spec;
        private final TSplitPane horizontalSplit;
        private final TSplitPane verticalSplit;
        private final TPanel topPanel;
        private final TPanel bottomPanel;
        private final TText transcriptText;
        private final TText contextText;
        private final TEditor inputEditor;

        ScaffoldWindow(TApplication app, CasciianLayoutSpec spec) {
            super(
                    app,
                    "magenta preview",
                    0,
                    0,
                    Math.max(40, app.getScreen().getWidth()),
                    Math.max(16, app.getScreen().getHeight()),
                    TWindow.ABSOLUTEXY | TWindow.NOCLOSEBOX | TWindow.NOZOOMBOX | TWindow.OVERRIDEMENU
            );
            this.spec = spec;
            this.horizontalSplit = addSplitPane(0, 0, Math.max(1, getWidth() - 2), Math.max(1, getHeight() - 2), true);
            TPanel leftPanel = new TPanel(horizontalSplit, 0, 0, 10, 10);
            leftPanel.setTitle("conversation");
            TPanel rightPanel = new TPanel(horizontalSplit, 0, 0, 10, 10);
            rightPanel.setTitle("views");
            this.horizontalSplit.setLeft(leftPanel);
            this.horizontalSplit.setRight(rightPanel);

            this.verticalSplit = leftPanel.addSplitPane(0, 0, Math.max(1, leftPanel.getWidth()), Math.max(1, leftPanel.getHeight()), false);
            this.topPanel = new TPanel(verticalSplit, 0, 0, 10, 10);
            this.topPanel.setTitle("session");
            this.bottomPanel = new TPanel(verticalSplit, 0, 0, 10, 10);
            this.bottomPanel.setTitle("input");
            this.verticalSplit.setTop(topPanel);
            this.verticalSplit.setBottom(bottomPanel);

            this.transcriptText = topPanel.addText("", 1, 1, Math.max(8, topPanel.getWidth() - 2), Math.max(4, topPanel.getHeight() - 4));
            this.contextText = topPanel.addText("", 1, Math.max(2, topPanel.getHeight() - 2), Math.max(8, topPanel.getWidth() - 2), 1);
            this.inputEditor = new ComposerEditor(bottomPanel, 1, 1, Math.max(8, bottomPanel.getWidth() - 2), Math.max(3, bottomPanel.getHeight() - 2));
            rightPanel.addText("context/task views coming soon", 1, 1, Math.max(8, rightPanel.getWidth() - 2), 2);
            redrawTranscript();
            redrawContext();
            applySplitLayout();
        }

        @Override
        public void onResize(TResizeEvent event) {
            super.onResize(event);
            if (event.getType() == TResizeEvent.Type.WIDGET || event.getType() == TResizeEvent.Type.SCREEN) {
                setDimensions(0, 0, Math.max(40, event.getWidth()), Math.max(16, event.getHeight()));
                applySplitLayout();
            }
        }

        private void redrawTranscript() {
            int width = Math.max(18, transcriptText.getWidth());
            String content = String.join(
                    "\n",
                    CasciianMessageFormatter.block("tool", "[Tool] SQL Query OK | Database: strain_research.db | Rows: 5", width),
                    CasciianMessageFormatter.block("magenta", "assistant> You're right to call that out. I was repeating calls; I will track processed ids and continue cleanly.", width),
                    CasciianMessageFormatter.block("user", "yes why are you looping are you selecting different offsets?", width)
            );
            transcriptText.setText(content);
        }

        private void redrawContext() {
            contextText.setText("ctx 14161/40000 (35.4%) | messages 51");
        }

        private void applySplitLayout() {
            int innerWidth = Math.max(1, getWidth() - 2);
            int innerHeight = Math.max(1, getHeight() - 2);
            horizontalSplit.setDimensions(0, 0, innerWidth, innerHeight);
            CasciianLayoutSpec.Allocation cols = spec.allocateColumns(innerWidth);
            horizontalSplit.setSplit(Math.max(1, cols.primary()));

            TPanel leftPanel = (TPanel) horizontalSplit.getLeft();
            int leftWidth = Math.max(1, leftPanel.getWidth());
            int leftHeight = Math.max(1, leftPanel.getHeight());
            verticalSplit.setDimensions(0, 0, leftWidth, leftHeight);
            CasciianLayoutSpec.Allocation rows = spec.allocateRows(leftHeight);
            verticalSplit.setSplit(Math.max(1, rows.primary()));

            topPanel.setDimensions(0, 0, Math.max(1, topPanel.getWidth()), Math.max(1, topPanel.getHeight()));
            bottomPanel.setDimensions(0, 0, Math.max(1, bottomPanel.getWidth()), Math.max(1, bottomPanel.getHeight()));

            transcriptText.setDimensions(1, 1, Math.max(8, topPanel.getWidth() - 2), Math.max(4, topPanel.getHeight() - 4));
            contextText.setDimensions(1, Math.max(1, topPanel.getHeight() - 2), Math.max(8, topPanel.getWidth() - 2), 1);
            inputEditor.setDimensions(1, 1, Math.max(8, bottomPanel.getWidth() - 2), Math.max(3, bottomPanel.getHeight() - 2));
            redrawTranscript();
            redrawContext();
        }
    }

    static final class ComposerEditor extends TEditor {
        ComposerEditor(TPanel parent, int x, int y, int width, int height) {
            super(parent, "", x, y, width, height);
            setAutoWrap(true);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event.matchesKey(TKeypress.kbEsc)) {
                getText();
                return;
            }
            if (event.matchesKey(TKeypress.kbEnter)) {
                setText("");
                return;
            }
            if (event.matchesKey(TKeypress.kbShiftEnter)) {
                super.onKeypress(new TKeypressEvent(getApplication().getBackend(), TKeypress.kbCtrlM, false, false, false));
                return;
            }
            super.onKeypress(event);
        }
    }
}
