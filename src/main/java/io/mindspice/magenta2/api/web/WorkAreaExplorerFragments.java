package io.mindspice.magenta2.api.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileLabel;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceFileLabelTargetType;
import io.mindspice.simplypages.components.Markdown;

final class WorkAreaExplorerFragments {
    static final String SHELL_ID = "avatar-workarea-explorer-shell";
    static final String LIST_ID = "avatar-workarea-list-region";
    static final String INSPECTOR_ID = "avatar-workarea-inspector";
    static final String MODAL_ID = "avatar-workarea-modal";
    static final String INSPECTOR_PANEL_STATE_EXPANDED = "expanded";
    static final String INSPECTOR_PANEL_STATE_COLLAPSED = "collapsed";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault());
    private static final Pattern TARGET_TYPE_PATTERN = Pattern.compile("\"targetType\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");

    private WorkAreaExplorerFragments() {
    }

    static String shell(
        WorkAreaExplorerService.DirectoryListing listing,
        WorkAreaExplorerService.Entry inspected,
        String selectedPath,
        String panelState
    ) {
        String currentPath = pathOrRoot(listing.path());
        String selected = selectedPath == null || selectedPath.isBlank() ? inspected == null ? null : inspected.path() : selectedPath;
        boolean panelCollapsed = INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState);
        String nextPanel = panelCollapsed ? INSPECTOR_PANEL_STATE_EXPANDED : INSPECTOR_PANEL_STATE_COLLAPSED;
        return """
            <div id="%s" class="avatar-workarea-explorer-shell">
              <div class="avatar-edit-header">
                <div>
                  <h2>%s</h2>
                  <small>%s</small>
                </div>
                <button type="button" class="button button-secondary small" hx-get="/avatar/_work-areas/placeholder" hx-target="#avatar-workarea-surface" hx-swap="innerHTML">Close</button>
              </div>
              <div class="workspace-explorer-toolbar">
                %s
                <button type="button" class="avatar-icon-toolbar-button" title="Refresh" aria-label="Refresh"
                        hx-get="/avatar/_work-areas/%s/explorer?path=%s%s&panel=%s"
                        hx-target="#%s" hx-swap="outerHTML">%s</button>
                <button type="button" class="avatar-icon-toolbar-button" title="New Folder" aria-label="New Folder"
                        hx-get="/avatar/_work-areas/%s/modal/create-folder?path=%s&panel=%s"
                        hx-target="#%s" hx-swap="innerHTML">%s</button>
                <details class="workspace-new-file-menu">
                  <summary class="avatar-icon-toolbar-button" title="New File" aria-label="New File">%s</summary>
                  <div class="workspace-new-file-menu-items">
                    <button type="button" class="button button-secondary small"
                            hx-get="/avatar/_work-areas/%s/modal/create-text?path=%s&panel=%s"
                            hx-target="#%s" hx-swap="innerHTML">Text (.txt)</button>
                    <button type="button" class="button button-secondary small"
                            hx-get="/avatar/_work-areas/%s/modal/create-markdown?path=%s&panel=%s"
                            hx-target="#%s" hx-swap="innerHTML">Markdown (.md)</button>
                  </div>
                </details>
              </div>
              <div class="workspace-explorer-pathbar">%s</div>
              <div class="avatar-workarea-explorer-layout%s">
                %s
                %s
              </div>
              <div id="%s"></div>
            </div>
            """.formatted(
            SHELL_ID,
            escape(listing.workArea().displayName()),
            escape(currentPath),
            upButton(listing),
            urlPath(listing.workArea().id()),
            url(currentPath),
            selected == null ? "" : "&selected=" + url(selected),
            panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED,
            SHELL_ID,
            iconSvg("refresh"),
            urlPath(listing.workArea().id()),
            url(currentPath),
            panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED,
            MODAL_ID,
            iconSvg("folder-plus"),
            iconSvg("file-plus"),
            urlPath(listing.workArea().id()),
            url(currentPath),
            panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED,
            MODAL_ID,
            urlPath(listing.workArea().id()),
            url(currentPath),
            panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED,
            MODAL_ID,
            breadcrumbs(listing),
            panelCollapsed ? " panel-collapsed" : "",
            list(listing, selected, panelCollapsed, false),
            inspector(
                listing.workArea().id(),
                listing.path(),
                inspected,
                null,
                panelCollapsed,
                nextPanel,
                false
            ),
            MODAL_ID
        );
    }

    static String list(
        WorkAreaExplorerService.DirectoryListing listing,
        String selectedPath,
        boolean panelCollapsed,
        boolean oob
    ) {
        StringBuilder rows = new StringBuilder();
        String currentPath = pathOrRoot(listing.path());
        if (!".".equals(currentPath)) {
            rows.append("""
                <tr data-workarea-path="%s">
                  <td class="workspace-explorer-name">
                    <button type="button" class="button button-link small workspace-name-button"
                            title=".."
                            hx-get="/avatar/_work-areas/%s/explorer?path=%s&panel=%s"
                            hx-target="#%s" hx-swap="outerHTML">..</button>
                  </td>
                  <td>Folder</td><td>-</td><td>Unknown</td><td>Unknown</td><td></td><td></td>
                </tr>
                """.formatted(
                escape(parentPath(currentPath)),
                urlPath(listing.workArea().id()),
                url(parentPath(currentPath)),
                panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED,
                SHELL_ID
            ));
        }
        for (WorkAreaExplorerService.Entry entry : listing.entries()) {
            rows.append(row(listing.workArea().id(), currentPath, entry, selectedPath, panelCollapsed));
        }
        if (listing.entries().isEmpty()) {
            rows.append("<tr><td colspan=\"7\">No entries available.</td></tr>");
        }
        return """
            <div id="%s" class="workspace-explorer-table-region"%s>
              <table class="workspace-explorer-table">
                <thead><tr><th>Name</th><th>File Type</th><th>Size</th><th>Created</th><th>Last Modified</th><th>Tags</th><th>Actions</th></tr></thead>
                <tbody>%s</tbody>
              </table>
            </div>
            """.formatted(LIST_ID, oob ? " hx-swap-oob=\"true\"" : "", rows);
    }

    static String inspector(
        String workAreaId,
        String listPath,
        WorkAreaExplorerService.Entry entry,
        String message,
        boolean panelCollapsed,
        String nextPanelState,
        boolean oob
    ) {
        if (entry == null) {
            return """
                <aside id="%s" class="file-explorer-inspector-pane"%s>
                  <div class="file-explorer-inspector-header">
                    <h4>Inspect</h4>
                    <button type="button" class="avatar-icon-toolbar-button" title="Toggle details panel" aria-label="Toggle details panel"
                            hx-get="/avatar/_work-areas/%s/explorer?path=%s&panel=%s"
                            hx-target="#%s" hx-swap="outerHTML">%s</button>
                  </div>
                  <p>Select a file or directory.</p>
                  %s
                </aside>
                """.formatted(
                INSPECTOR_ID,
                oob ? " hx-swap-oob=\"true\"" : "",
                urlPath(workAreaId),
                url(pathOrRoot(listPath)),
                nextPanelState,
                SHELL_ID,
                iconSvg(panelCollapsed ? "panel-open" : "panel-close"),
                status(message, false)
            );
        }
        String viewAction = entry.canView()
            ? button("View", "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId) + "/viewer?path=" + url(entry.path()), "#" + MODAL_ID, "innerHTML")
            : "<span class=\"avatar-muted\">Viewer unavailable</span>";
        return """
            <aside id="%s" class="file-explorer-inspector-pane"%s>
              <div class="file-explorer-inspector-header">
                <h4 title="%s">%s</h4>
                <button type="button" class="avatar-icon-toolbar-button" title="Toggle details panel" aria-label="Toggle details panel"
                        hx-get="/avatar/_work-areas/%s/explorer?path=%s&selected=%s&panel=%s"
                        hx-target="#%s" hx-swap="outerHTML">%s</button>
              </div>
              <p class="file-entry-path" title="%s">%s</p>
              <div class="file-entry-tag-editor">
                <h5>Tags</h5>
                <div class="file-entry-tags">%s</div>
                %s
              </div>
              <div class="file-entry-preview-meta">
                <h5>Preview &amp; Details</h5>
                %s
              </div>
              <dl class="file-entry-details-grid">
                <dt>Type</dt><dd>%s</dd>
                <dt>Size</dt><dd>%s</dd>
                <dt>Created</dt><dd>%s</dd>
                <dt>Last Modified</dt><dd>%s</dd>
              </dl>
              <div class="avatar-row-actions">
                %s
                %s
                %s
                %s
                %s
              </div>
              %s
            </aside>
            """.formatted(
            INSPECTOR_ID,
            oob ? " hx-swap-oob=\"true\"" : "",
            escapeAttribute(entry.name()),
            escape(entry.name()),
            urlPath(workAreaId),
            url(pathOrRoot(listPath)),
            url(entry.path()),
            nextPanelState,
            SHELL_ID,
            iconSvg(panelCollapsed ? "panel-open" : "panel-close"),
            escapeAttribute(entry.path()),
            escape(entry.path()),
            tags(null, entry.path(), entry.tags(), true),
            button(
                "Tag Editor",
                "hx-get",
                "/avatar/_work-areas/" + urlPath(workAreaId) + "/modal/tag-editor?path=" + url(entry.path())
                    + "&panel=" + url(panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED),
                "#" + MODAL_ID,
                "innerHTML"
            ),
            viewerHint(entry),
            escape(entry.fileType()),
            escape(entry.sizeLabel()),
            time(entry.createdAt()),
            time(entry.modifiedAt()),
            viewAction,
            modalButton(
                "Rename",
                workAreaId,
                "rename",
                entry.path(),
                panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED
            ),
            modalButton(
                "Delete",
                workAreaId,
                "delete",
                entry.path(),
                panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED
            ),
            pickerButton("Copy", workAreaId, "copy", entry.path()),
            pickerButton("Move", workAreaId, "move", entry.path()),
            status(message, false)
        );
    }

    static String viewer(String workAreaId, WorkAreaExplorerService.FilePreview preview) {
        String title = "View " + fileName(preview.path());
        String body;
        if ("image".equals(preview.kind())) {
            body = """
                <div class="avatar-workarea-image-frame">
                  <img class="avatar-workarea-image" src="/api/work-areas/%s/files/view?path=%s" alt="%s">
                </div>
                <dl class="avatar-workarea-viewer-meta">
                  <dt>Path</dt><dd>%s</dd>
                  <dt>Size</dt><dd>%s</dd>
                </dl>
                <a class="button button-secondary small" href="/api/work-areas/%s/files/download?path=%s">Download</a>
                """.formatted(
                urlPath(workAreaId),
                url(preview.path()),
                escapeAttribute(fileName(preview.path())),
                escape(preview.path()),
                escape(sizeLabel(preview.size())),
                urlPath(workAreaId),
                url(preview.path())
            );
        } else if ("markdown".equals(preview.kind()) && preview.text()) {
            body = """
                <div class="avatar-workarea-viewer" data-viewer-kind="markdown" data-active-tab="rendered">
                  <div class="avatar-workarea-tabs"><span class="avatar-tab-active">Rendered</span><button type="button" class="button button-secondary small" hx-get="/avatar/_work-areas/%s/viewer/text?path=%s&tab=text" hx-target="#%s" hx-swap="innerHTML">Text</button></div>
                  <div class="avatar-workarea-rendered">%s</div>
                </div>
                """.formatted(urlPath(workAreaId), url(preview.path()), MODAL_ID, safeMarkdown(preview.content()));
        } else if ("text".equals(preview.kind()) && preview.text()) {
            body = textEditor(workAreaId, preview, false);
        } else {
            body = unsupportedViewerBody(preview);
        }
        return modal(title, body, false);
    }

    static String textViewer(String workAreaId, WorkAreaExplorerService.FilePreview preview, String tab) {
        if (!preview.text()) {
            return modal("Viewer unavailable", "<p>This file cannot be opened as editable text.</p>", false);
        }
        boolean markdown = "markdown".equals(preview.kind());
        boolean rendered = markdown && !"text".equalsIgnoreCase(tab);
        String body;
        if (rendered) {
            body = """
                <div class="avatar-workarea-viewer" data-viewer-kind="markdown" data-active-tab="rendered">
                  <div class="avatar-workarea-tabs"><span class="avatar-tab-active">Rendered</span><button type="button" class="button button-secondary small" hx-get="/avatar/_work-areas/%s/viewer/text?path=%s&tab=text" hx-target="#%s" hx-swap="innerHTML">Text</button></div>
                  <div class="avatar-workarea-rendered">%s</div>
                </div>
                """.formatted(urlPath(workAreaId), url(preview.path()), MODAL_ID, safeMarkdown(preview.content()));
        } else {
            body = textEditor(workAreaId, preview, markdown);
        }
        return modal("View " + fileName(preview.path()), body, false);
    }

    static String textSaveResponse(
        String workAreaId,
        WorkAreaExplorerService.FilePreview preview,
        WorkAreaExplorerService.DirectoryListing listing,
        WorkAreaExplorerService.Entry inspected,
        String selectedPath,
        String panelState,
        String message
    ) {
        return textViewer(workAreaId, preview, "rendered")
            + list(listing, selectedPath, INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState), true)
            + inspector(
            listing.workArea().id(),
            listing.path(),
            inspected,
            message,
            INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState),
            INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState)
                ? INSPECTOR_PANEL_STATE_EXPANDED
                : INSPECTOR_PANEL_STATE_COLLAPSED,
            true
        );
    }

    static String textCreateResponse(
        String workAreaId,
        WorkAreaExplorerService.FilePreview preview,
        WorkAreaExplorerService.DirectoryListing listing,
        WorkAreaExplorerService.Entry inspected,
        String selectedPath,
        String panelState,
        String message
    ) {
        return textViewer(workAreaId, preview, "text")
            + list(listing, selectedPath, INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState), true)
            + inspector(
            listing.workArea().id(),
            listing.path(),
            inspected,
            message,
            INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState),
            INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState)
                ? INSPECTOR_PANEL_STATE_EXPANDED
                : INSPECTOR_PANEL_STATE_COLLAPSED,
            true
        );
    }

    static String actionModal(
        String workAreaId,
        String action,
        String path,
        String panelState,
        WorkAreaExplorerService.DeletePreflight preflight
    ) {
        String body = switch (action) {
            case "create-folder" -> createFolderForm(workAreaId, path, panelState);
            case "create-text", "create-markdown" -> createTextForm(workAreaId, action, path, panelState);
            case "rename" -> form(workAreaId, "/files/rename", path, panelState, "name", "New name", "Rename");
            case "copy", "move" -> copyMovePicker(workAreaId, action, path, ".", ".", panelState, 32, 64);
            case "tag" -> form(workAreaId, "/files/tags", path, panelState, "label", "Tag", "Add Tag");
            case "delete", "delete-recursive" -> deleteForm(workAreaId, action, path, preflight);
            default -> "<p>Unknown file action.</p>";
        };
        return modal(title(action), body, false);
    }

    static String mutationResponse(
        WorkAreaExplorerService.DirectoryListing listing,
        WorkAreaExplorerService.Entry inspected,
        String selectedPath,
        String panelState,
        String message
    ) {
        boolean panelCollapsed = INSPECTOR_PANEL_STATE_COLLAPSED.equalsIgnoreCase(panelState);
        return modal("", "", true)
            + list(listing, selectedPath, panelCollapsed, true)
            + inspector(
            listing.workArea().id(),
            listing.path(),
            inspected,
            message,
            panelCollapsed,
            panelCollapsed ? INSPECTOR_PANEL_STATE_EXPANDED : INSPECTOR_PANEL_STATE_COLLAPSED,
            true
        );
    }

    static String modalError(String title, String message) {
        return modal(title, "<div class=\"avatar-status-error\">" + escape(message) + "</div>", false);
    }

    static String modalMessage(String title, String message) {
        return modal(title, "<div class=\"avatar-status\">" + escape(message) + "</div>", false);
    }

    static String inspectorError(String message) {
        return inspector(null, ".", null, message, false, INSPECTOR_PANEL_STATE_COLLAPSED, false);
    }

    static String listError(String message) {
        return """
            <div id="%s" class="workspace-explorer-table-region">
              <div class="avatar-status-error">%s</div>
            </div>
            """.formatted(LIST_ID, escape(message));
    }

    static String directoryPickerOptions(
        WorkAreaExplorerService.DirectoryListing listing,
        String action,
        String sourcePath,
        String destinationPath,
        String panelState,
        int x,
        int y
    ) {
        String currentPath = pathOrRoot(listing.path());
        StringBuilder rows = new StringBuilder();
        if (!".".equals(currentPath)) {
            rows.append("""
                <button type="button" class="workspace-directory-picker-row"
                        hx-get="/avatar/_work-areas/%s/files/action/%s/picker?path=%s&browse=%s&destination=%s&panel=%s&x=%d&y=%d"
                        hx-target="#%s" hx-swap="innerHTML">..</button>
                """.formatted(
                urlPath(listing.workArea().id()),
                urlPath(action),
                url(sourcePath),
                url(parentPath(currentPath)),
                url(destinationPath),
                escapeAttribute(panelState),
                x,
                y,
                MODAL_ID
            ));
        }
        for (WorkAreaExplorerService.Entry entry : listing.entries()) {
            if (!entry.directory()) {
                continue;
            }
            rows.append("""
                <button type="button" class="workspace-directory-picker-row"
                        title="%s"
                        hx-get="/avatar/_work-areas/%s/files/action/%s/picker?path=%s&browse=%s&destination=%s&panel=%s&x=%d&y=%d"
                        hx-target="#%s" hx-swap="innerHTML">%s</button>
                """.formatted(
                escapeAttribute(entry.path()),
                urlPath(listing.workArea().id()),
                urlPath(action),
                url(sourcePath),
                url(entry.path()),
                url(entry.path()),
                escapeAttribute(panelState),
                x,
                y,
                MODAL_ID,
                escape(entry.name())
            ));
        }
        if (rows.isEmpty()) {
            rows.append("<div class=\"avatar-muted\">No subdirectories available.</div>");
        }
        return rows.toString();
    }

    static String tagOptions(String workAreaId, String path, String typedValue, List<WorkspaceFileLabel> labels) {
        String query = typedValue == null ? "" : typedValue.trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        boolean exactMatch = false;
        for (WorkspaceFileLabel label : labels) {
            if (label.slug().equalsIgnoreCase(query)) {
                exactMatch = true;
            }
            out.append("""
                <button type="button" class="entity-selector-option"
                        hx-post="/avatar/_work-areas/%s/files/tags?path=%s&label=%s"
                        hx-target="#%s" hx-swap="innerHTML">
                  <span class="entity-selector-option-label">%s</span>
                  <code>%s</code>
                </button>
                """.formatted(
                urlPath(workAreaId),
                url(path),
                url(label.slug()),
                MODAL_ID,
                escape(label.displayName()),
                escape(label.slug())
            ));
        }
        if (!query.isBlank() && !exactMatch) {
            out.append("""
                <button type="button" class="entity-selector-option workspace-tag-create-option"
                        hx-post="/avatar/_work-areas/%s/files/tags?path=%s&label=%s"
                        hx-target="#%s" hx-swap="innerHTML">
                  <span class="entity-selector-option-label">Create</span>
                  <code>%s</code>
                </button>
                """.formatted(
                urlPath(workAreaId),
                url(path),
                url(normalizedQuery),
                MODAL_ID,
                escape(normalizedQuery)
            ));
        }
        if (out.isEmpty()) {
            out.append("<div class=\"entity-selector-empty\">No tags available</div>");
        }
        return out.toString();
    }

    static String tagEditorModal(
        String workAreaId,
        WorkAreaExplorerService.Entry entry,
        List<WorkspaceFileLabel> allTags,
        String panelState,
        String message
    ) {
        String selectedType = entry.directory() ? WorkspaceFileLabelTargetType.DIRECTORY.wireName()
            : WorkspaceFileLabelTargetType.FILE.wireName();
        String createForm = """
            <form class="avatar-stack-form" hx-post="/avatar/_work-areas/%s/modal/tag-editor/tags"
                  hx-target="#%s" hx-swap="innerHTML">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="panel" value="%s">
              <label>Tag slug<input type="text" name="label" placeholder="project-alpha" required></label>
              <label>Display name<input type="text" name="displayName" placeholder="Project Alpha"></label>
              <label>Target type
                <select name="targetType">
                  <option value="directory"%s>Directory</option>
                  <option value="file"%s>File</option>
                </select>
              </label>
              <label>Description<textarea name="description" rows="3"
                  placeholder="LLM-friendly context for when this tag should be used."></textarea></label>
              <button type="submit" class="button">Create Tag</button>
            </form>
            """.formatted(
            urlPath(workAreaId),
            MODAL_ID,
            escapeAttribute(entry.path()),
            escapeAttribute(panelState),
            WorkspaceFileLabelTargetType.DIRECTORY.wireName().equals(selectedType) ? " selected" : "",
            WorkspaceFileLabelTargetType.FILE.wireName().equals(selectedType) ? " selected" : ""
        );
        String body = """
            <div class="workspace-tag-editor-modal">
              <p class="file-entry-path" title="%s">%s</p>
              <div class="file-entry-tag-editor">
                <h5>Assigned Tags</h5>
                <div class="file-entry-tags">%s</div>
              </div>
              <div class="file-entry-tag-editor">
                <h5>Create Tag</h5>
                %s
              </div>
              <div class="file-entry-tag-editor">
                <h5>Directory Tags</h5>
                %s
              </div>
              <div class="file-entry-tag-editor">
                <h5>File Tags</h5>
                %s
              </div>
              %s
            </div>
            """.formatted(
            escapeAttribute(entry.path()),
            escape(entry.path()),
            tags(null, entry.path(), entry.tags(), true),
            createForm,
            tagGroup(
                "directory",
                selectedType,
                entry.path(),
                workAreaId,
                allTags,
                panelState
            ),
            tagGroup(
                "file",
                selectedType,
                entry.path(),
                workAreaId,
                allTags,
                panelState
            ),
            status(message, false)
        );
        return modal("Tag Editor", body, false);
    }

    private static String tagGroup(
        String groupType,
        String selectedType,
        String selectedPath,
        String workAreaId,
        List<WorkspaceFileLabel> allTags,
        String panelState
    ) {
        StringBuilder out = new StringBuilder();
        for (WorkspaceFileLabel label : allTags) {
            String targetType = metadataValue(label.metadataJson(), TARGET_TYPE_PATTERN);
            if (targetType != null
                && !groupType.equalsIgnoreCase(targetType)
                && !"any".equalsIgnoreCase(targetType)) {
                continue;
            }
            String description = metadataValue(label.metadataJson(), DESCRIPTION_PATTERN);
            String typeLabel = targetType == null ? "Any" : capitalize(targetType);
            boolean compatible = targetType == null || selectedType.equalsIgnoreCase(targetType) || "any".equalsIgnoreCase(targetType);
            out.append("""
                <div class="workspace-tag-editor-row">
                  <div class="workspace-tag-editor-meta">
                    <strong>%s</strong>
                    <code>%s</code>
                    <span class="tag tag-muted">%s</span>
                    <small>%s</small>
                  </div>
                  <form hx-post="/avatar/_work-areas/%s/modal/tag-editor/assign"
                        hx-target="#%s" hx-swap="innerHTML">
                    <input type="hidden" name="path" value="%s">
                    <input type="hidden" name="label" value="%s">
                    <input type="hidden" name="panel" value="%s">
                    <button type="submit" class="button button-secondary small"%s>Assign</button>
                  </form>
                </div>
                """.formatted(
                escape(label.displayName()),
                escape(label.slug()),
                escape(typeLabel),
                escape(description == null || description.isBlank() ? "No description provided." : description),
                urlPath(workAreaId),
                MODAL_ID,
                escapeAttribute(selectedPath),
                escapeAttribute(label.slug()),
                escapeAttribute(panelState),
                compatible ? "" : " disabled aria-disabled=\"true\" title=\"Incompatible with selected item type\""
            ));
        }
        if (out.isEmpty()) {
            return "<div class=\"entity-selector-empty\">No tags available.</div>";
        }
        return out.toString();
    }

    private static String row(
        String workAreaId,
        String currentPath,
        WorkAreaExplorerService.Entry entry,
        String selectedPath,
        boolean panelCollapsed
    ) {
        String selected = entry.path().equals(selectedPath) ? " selected" : "";
        String panel = panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED;
        String rowSelectRoute = "/avatar/_work-areas/" + urlPath(workAreaId) + "/explorer?path=" + url(currentPath)
            + "&selected=" + url(entry.path())
            + "&panel=" + panel;
        String nameAction = entry.directory()
            ? button(entry.name(), "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId)
                + "/explorer?path=" + url(entry.path()) + "&panel="
                + panel, "#" + SHELL_ID, "outerHTML")
            : button(entry.name(), "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId) + "/explorer?path=" + url(currentPath)
                + "&selected=" + url(entry.path())
                + "&panel=" + panel,
                "#" + SHELL_ID, "outerHTML");
        String open = entry.directory()
            ? button("Open", "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId)
                + "/explorer?path=" + url(entry.path()) + "&panel="
                + panel, "#" + SHELL_ID, "outerHTML")
            : entry.canView()
                ? button("View", "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId) + "/viewer?path=" + url(entry.path()), "#" + MODAL_ID, "innerHTML")
                : "";
        return """
            <tr class="workspace-explorer-row%s" data-workarea-path="%s"
                hx-get="%s"
                hx-trigger="click[!event.target.closest('button,a,input,select,textarea,label,summary')]"
                hx-target="#%s" hx-swap="outerHTML">
              <td class="workspace-explorer-name" title="%s">%s</td>
              <td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>
              <td class="avatar-row-actions">%s%s%s%s%s</td>
            </tr>
            """.formatted(
            selected,
            escapeAttribute(entry.path()),
            rowSelectRoute,
            SHELL_ID,
            escapeAttribute(entry.name()),
            nameAction,
            escape(entry.fileType()),
            escape(entry.sizeLabel()),
            time(entry.createdAt()),
            time(entry.modifiedAt()),
            tags(workAreaId, entry.path(), entry.tags(), false),
            open,
            modalButton(
                "Rename",
                workAreaId,
                "rename",
                entry.path(),
                panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED
            ),
            modalButton(
                "Delete",
                workAreaId,
                "delete",
                entry.path(),
                panelCollapsed ? INSPECTOR_PANEL_STATE_COLLAPSED : INSPECTOR_PANEL_STATE_EXPANDED
            ),
            pickerButton("Copy", workAreaId, "copy", entry.path()),
            pickerButton("Move", workAreaId, "move", entry.path())
        );
    }

    private static String createFolderForm(String workAreaId, String path, String panelState) {
        return """
            <form class="avatar-stack-form" hx-post="/avatar/_work-areas/%s/directories" hx-target="#%s" hx-swap="innerHTML">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="panel" value="%s">
              <input type="text" name="name" placeholder="Folder name">
              <button type="submit" class="button">Create Folder</button>
            </form>
            """.formatted(urlPath(workAreaId), MODAL_ID, escapeAttribute(path), escapeAttribute(panelState));
    }

    private static String createTextForm(String workAreaId, String action, String path, String panelState) {
        String kind = "create-markdown".equals(action) ? "markdown" : "text";
        String label = "markdown".equals(kind) ? "Create Markdown" : "Create Text File";
        return """
            <form class="avatar-stack-form" hx-post="/avatar/_work-areas/%s/text?kind=%s" hx-target="#%s" hx-swap="innerHTML">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="panel" value="%s">
              <input type="text" name="name" placeholder="File name">
              <button type="submit" class="button">%s</button>
            </form>
            """.formatted(urlPath(workAreaId), urlPath(kind), MODAL_ID, escapeAttribute(path), escapeAttribute(panelState), escape(label));
    }

    private static String form(
        String workAreaId,
        String route,
        String path,
        String panelState,
        String field,
        String placeholder,
        String label
    ) {
        return """
            <form class="avatar-stack-form" hx-post="/avatar/_work-areas/%s%s" hx-target="#%s" hx-swap="innerHTML">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="panel" value="%s">
              <input type="text" name="%s" placeholder="%s">
              <button type="submit" class="button">%s</button>
            </form>
            """.formatted(
            urlPath(workAreaId),
            route,
            MODAL_ID,
            escapeAttribute(path),
            escapeAttribute(panelState),
            escapeAttribute(field),
            escapeAttribute(placeholder),
            escape(label)
        );
    }

    static String copyMovePicker(
        String workAreaId,
        String action,
        String sourcePath,
        String browsePath,
        String destinationPath,
        String panelState,
        int x,
        int y
    ) {
        String normalizedBrowse = pathOrRoot(browsePath);
        String normalizedDestination = pathOrRoot(destinationPath);
        String title = "copy".equals(action) ? "Copy" : "Move";
        String picker = """
            <div class="workspace-directory-picker">
              <div class="workspace-directory-picker-header">
                <strong>Select destination</strong>
                <code title="%s">%s</code>
              </div>
              <div class="workspace-directory-picker-actions">
                <button type="button" class="button button-secondary small"
                        hx-get="/avatar/_work-areas/%s/files/action/%s/picker?path=%s&browse=%s&destination=%s&panel=%s&x=%d&y=%d"
                        hx-target="#%s" hx-swap="innerHTML">Use this folder</button>
              </div>
              %s
            </div>
            """.formatted(
            escapeAttribute(normalizedBrowse),
            escape(normalizedBrowse),
            urlPath(workAreaId),
            urlPath(action),
            url(sourcePath),
            url(normalizedBrowse),
            url(normalizedBrowse),
            escapeAttribute(panelState),
            x,
            y,
            MODAL_ID,
            directoryPickerList(workAreaId, action, sourcePath, normalizedBrowse, normalizedDestination, panelState, x, y)
        );
        String body = """
            %s
            <form class="avatar-stack-form" data-file-action="%s"
                  hx-post="/avatar/_work-areas/%s/files/action/%s"
                  hx-target="#%s" hx-swap="innerHTML">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="destination" value="%s">
              <input type="hidden" name="panel" value="%s">
              <label for="picker-name">Optional new name</label>
              <input id="picker-name" type="text" name="name" placeholder="Optional new name">
              <button type="submit" class="button">%s</button>
            </form>
            """.formatted(
            picker,
            escapeAttribute(action),
            urlPath(workAreaId),
            urlPath(action),
            MODAL_ID,
            escapeAttribute(sourcePath),
            escapeAttribute(normalizedDestination),
            escapeAttribute(panelState),
            escape(title)
        );
        return modalWithPosition(title + " " + fileName(sourcePath), body, x, y);
    }

    private static String directoryPickerList(
        String workAreaId,
        String action,
        String sourcePath,
        String browsePath,
        String destinationPath,
        String panelState,
        int x,
        int y
    ) {
        return """
            <div class="workspace-directory-picker-list"
                 hx-get="/avatar/_work-areas/%s/files/directories?path=%s&source=%s&action=%s&destination=%s&panel=%s&x=%d&y=%d"
                 hx-trigger="load"
                 hx-target="this"
                 hx-swap="innerHTML">
              Loading directories...
            </div>
            """.formatted(
            urlPath(workAreaId),
            url(pathOrRoot(browsePath)),
            url(sourcePath),
            urlPath(action),
            url(pathOrRoot(destinationPath)),
            escapeAttribute(panelState),
            x,
            y
        );
    }

    private static String deleteForm(String workAreaId, String action, String path, WorkAreaExplorerService.DeletePreflight preflight) {
        if ("delete-recursive".equals(action)
            || (preflight != null && preflight.requiredStep() == WorkAreaExplorerService.DeleteStep.FILE_CONFIRM)) {
            String step = "delete-recursive".equals(action) ? "DIRECTORY_RECURSIVE_CONFIRM" : "FILE_CONFIRM";
            return """
                <p>Delete %s?</p>
                <form class="avatar-stack-form" hx-post="/avatar/_work-areas/%s/files/delete" hx-target="#%s" hx-swap="innerHTML">
                  <input type="hidden" name="path" value="%s">
                  <input type="hidden" name="step" value="%s">
                  <button type="submit" class="button">Confirm Delete</button>
                </form>
                """.formatted(escape(fileName(path)), urlPath(workAreaId), MODAL_ID, escapeAttribute(path), step);
        }
        if (preflight != null && preflight.requiredStep() == WorkAreaExplorerService.DeleteStep.DIRECTORY_RECURSIVE_CONFIRM) {
            return """
                <p>This directory contains %d entries.</p>
                <button type="button" class="button" hx-get="/avatar/_work-areas/%s/modal/delete-recursive?path=%s" hx-target="#%s" hx-swap="innerHTML">Confirm Recursive Delete</button>
                """.formatted(preflight.candidateCount(), urlPath(workAreaId), url(path), MODAL_ID);
        }
        return "<p>Delete is unavailable for this path.</p>";
    }

    private static String textEditor(String workAreaId, WorkAreaExplorerService.FilePreview preview, boolean markdown) {
        String tabs = markdown
            ? "<button type=\"button\" class=\"button button-secondary small\" hx-get=\"/avatar/_work-areas/" + urlPath(workAreaId)
                + "/viewer/text?path=" + url(preview.path()) + "&tab=rendered\" hx-target=\"#" + MODAL_ID
                + "\" hx-swap=\"innerHTML\">Rendered</button><span class=\"avatar-tab-active\">Text</span>"
            : "<span class=\"avatar-tab-active\">Text</span>";
        return """
            <div class="avatar-workarea-viewer" data-viewer-kind="%s" data-active-tab="text">
              <div class="avatar-workarea-tabs">%s</div>
              <form class="avatar-stack-form" hx-put="/avatar/_work-areas/%s/text?path=%s" hx-target="#%s" hx-swap="innerHTML">
                <textarea name="content" rows="14">%s</textarea>
                <button type="submit" class="button">Save File</button>
              </form>
            </div>
            """.formatted(markdown ? "markdown" : "text", tabs, urlPath(workAreaId), url(preview.path()), MODAL_ID, escape(preview.content() == null ? "" : preview.content()));
    }

    private static String unsupportedViewerBody(WorkAreaExplorerService.FilePreview preview) {
        return """
            <div class="avatar-status">Viewer unavailable for this file type or size.</div>
            <dl class="avatar-workarea-viewer-meta">
              <dt>Path</dt><dd>%s</dd>
              <dt>Size</dt><dd>%s</dd>
              <dt>Reason</dt><dd>%s</dd>
            </dl>
            """.formatted(
            escape(preview.path()),
            escape(sizeLabel(preview.size())),
            escape(unsupportedReason(preview.kind()))
        );
    }

    private static String modal(String title, String body, boolean clear) {
        if (clear) {
            return "<div id=\"" + MODAL_ID + "\" hx-swap-oob=\"true\"></div>";
        }
        return modalWithPosition(title, body, -1, -1);
    }

    private static String modalWithPosition(String title, String body, int x, int y) {
        String style = x >= 0 && y >= 0
            ? " style=\"left:" + Math.max(8, x) + "px;top:" + Math.max(8, y) + "px;position:fixed;max-width:min(38rem,calc(100vw - 1rem));\""
            : "";
        return """
            <div class="avatar-modal" role="dialog" aria-modal="true">
              <div class="avatar-edit-panel avatar-workarea-panel"%s>
                <div class="avatar-edit-header"><h2>%s</h2><button type="button" class="button button-secondary small" hx-get="/avatar/_edit?close=true" hx-target="#%s" hx-swap="innerHTML">Close</button></div>
                %s
              </div>
            </div>
            """.formatted(style, escape(title), MODAL_ID, body);
    }

    private static String upButton(WorkAreaExplorerService.DirectoryListing listing) {
        String path = pathOrRoot(listing.path());
        if (".".equals(path)) {
            return """
                <button type="button" class="avatar-icon-toolbar-button" disabled aria-disabled="true" title="Back">
                  %s
                </button>
                """.formatted(iconSvg("back"));
        }
        return """
            <button type="button" class="avatar-icon-toolbar-button" title="Back" aria-label="Back"
                    hx-get="/avatar/_work-areas/%s/explorer?path=%s"
                    hx-target="#%s" hx-swap="outerHTML">%s</button>
            """.formatted(
            urlPath(listing.workArea().id()),
            url(parentPath(path)),
            SHELL_ID,
            iconSvg("back")
        );
    }

    private static String breadcrumbs(WorkAreaExplorerService.DirectoryListing listing) {
        String path = pathOrRoot(listing.path());
        StringBuilder out = new StringBuilder();
        out.append("<button type=\"button\" class=\"button button-link small\" hx-get=\"/avatar/_work-areas/")
            .append(urlPath(listing.workArea().id()))
            .append("/explorer?path=.\" hx-target=\"#")
            .append(SHELL_ID)
            .append("\" hx-swap=\"outerHTML\">.</button>");
        if (".".equals(path)) {
            return out.toString();
        }
        StringBuilder current = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isBlank()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append('/');
            }
            current.append(part);
            out.append(" / <button type=\"button\" class=\"button button-link small\" hx-get=\"/avatar/_work-areas/")
                .append(urlPath(listing.workArea().id()))
                .append("/explorer?path=")
                .append(url(current.toString()))
                .append("\" hx-target=\"#")
                .append(SHELL_ID)
                .append("\" hx-swap=\"outerHTML\">")
                .append(escape(part))
                .append("</button>");
        }
        return out.toString();
    }

    private static String button(String label, String hxVerb, String route, String target, String swap) {
        return """
            <button type="button" class="button button-secondary small" %s="%s" hx-target="%s" hx-swap="%s">%s</button>
            """.formatted(hxVerb, route, target, swap, escape(label));
    }

    private static String modalButton(
        String label,
        String workAreaId,
        String action,
        String path,
        String panelState
    ) {
        return button(label, "hx-get", "/avatar/_work-areas/" + urlPath(workAreaId) + "/modal/" + urlPath(action)
            + "?path=" + url(path)
            + "&panel=" + url(panelState), "#" + MODAL_ID, "innerHTML");
    }

    private static String pickerButton(String label, String workAreaId, String action, String path) {
        return """
            <button type="button" class="button button-secondary small"
                    hx-get="/avatar/_work-areas/%s/files/action/%s/picker?path=%s&browse=.&destination=.&x=48&y=96"
                    hx-vals='js:{x:event.clientX,y:event.clientY}'
                    hx-target="#%s" hx-swap="innerHTML">%s</button>
            """.formatted(urlPath(workAreaId), urlPath(action), url(path), MODAL_ID, escape(label));
    }

    private static String tags(String workAreaId, String path, List<WorkspaceFileLabel> tags, boolean removable) {
        if (tags == null || tags.isEmpty()) {
            return "<span class=\"tag tag-muted\">No tags</span>";
        }
        StringBuilder out = new StringBuilder();
        int visible = removable ? tags.size() : Math.min(tags.size(), 3);
        for (WorkspaceFileLabel tag : tags.stream().limit(visible).toList()) {
            out.append("<span class=\"tag\">").append(escape(tag.slug()));
            if (removable && workAreaId != null) {
                out.append(" <button type=\"button\" class=\"button button-link small workspace-tag-remove\" hx-delete=\"/avatar/_work-areas/")
                    .append(urlPath(workAreaId))
                    .append("/files/tags?path=")
                    .append(url(path))
                    .append("&amp;label=")
                    .append(url(tag.slug()))
                    .append("\" hx-target=\"#")
                    .append(MODAL_ID)
                    .append("\" hx-swap=\"innerHTML\">x</button>");
            }
            out.append("</span>");
        }
        if (!removable && tags.size() > visible) {
            out.append("<span class=\"tag tag-muted\">+").append(tags.size() - visible).append("</span>");
        }
        return out.toString();
    }

    private static String tagSelector(String workAreaId, String path, boolean directory) {
        String targetType = directory ? "directory" : "file";
        return """
            <div class="entity-selector workspace-tag-selector">
              <input type="hidden" name="path" value="%s">
              <input type="hidden" name="targetType" value="%s">
              <label class="entity-selector-label">
                <span>Find or Create Tag</span>
                <input type="text" name="label" placeholder="Search or type a new tag"
                       autocomplete="off"
                       hx-get="/avatar/_work-areas/%s/tags/options?path=%s"
                       hx-trigger="input changed delay:250ms, focus"
                       hx-target="#workarea-tag-options"
                       hx-include="closest .workspace-tag-selector"
                       hx-swap="innerHTML">
              </label>
              <div id="workarea-tag-options" class="entity-selector-results"></div>
              <div class="workspace-tag-create-row">
                <button type="button" class="button button-secondary small"
                        hx-post="/avatar/_work-areas/%s/files/tags?path=%s"
                        hx-include="closest .workspace-tag-selector"
                        hx-target="#%s" hx-swap="innerHTML">Add Typed Tag</button>
              </div>
            </div>
            """.formatted(
            escapeAttribute(path),
            targetType,
            urlPath(workAreaId),
            url(path),
            urlPath(workAreaId),
            url(path),
            MODAL_ID
        );
    }

    private static String viewerHint(WorkAreaExplorerService.Entry entry) {
        if (entry.viewerKind() == WorkAreaExplorerService.ViewerKind.MARKDOWN) {
            return "<p>Markdown file. Rendered and raw text views are available.</p>";
        }
        if (entry.viewerKind() == WorkAreaExplorerService.ViewerKind.TEXT) {
            return "<p>Text file. Raw text viewing and editing are available.</p>";
        }
        if (entry.viewerKind() == WorkAreaExplorerService.ViewerKind.IMAGE) {
            return "<p>Image file. Inline preview and download are available.</p>";
        }
        if (entry.directory()) {
            return "<p>Directory selected. Use Open, Move, Copy, Rename, or tags.</p>";
        }
        return "<p>Viewer unavailable for this file type or size.</p>";
    }

    static String renderedMarkdownForTest(String content, Function<String, String> renderer) {
        return renderMarkdown(content, renderer);
    }

    private static String safeMarkdown(String content) {
        return renderMarkdown(content, value -> new Markdown(value).render());
    }

    private static String renderMarkdown(String content, Function<String, String> renderer) {
        try {
            return renderer.apply(content == null ? "" : content);
        } catch (RuntimeException exception) {
            return """
                <div class="avatar-workarea-render-fallback">%s</div>
                <div class="avatar-status-error avatar-workarea-render-error">Markdown render failed. Raw text is still available.</div>
                """.formatted(escape(content == null ? "" : content));
        }
    }

    private static String unsupportedReason(String kind) {
        return switch (kind == null ? "" : kind) {
            case "too_large" -> "File is too large for the browser viewer.";
            case "invalid_utf8" -> "File is not valid UTF-8 text.";
            case "unsupported" -> "File type is not supported by the viewer.";
            default -> "Viewer is not available for this entry.";
        };
    }

    private static String iconSvg(String icon) {
        return switch (icon) {
            case "refresh" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 11a8 8 0 0 0-14.5-3.8"/><path d="M4 4v4h4"/>
                  <path d="M4 13a8 8 0 0 0 14.5 3.8"/><path d="M20 20v-4h-4"/>
                </svg>
                """;
            case "folder-plus" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 6h6l2 2h10v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M12 11v6"/><path d="M9 14h6"/>
                </svg>
                """;
            case "file-plus" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <path d="M14 2v6h6"/><path d="M12 12v6"/><path d="M9 15h6"/>
                </svg>
                """;
            case "back" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M19 12H5"/><path d="M11 6l-6 6 6 6"/>
                </svg>
                """;
            case "panel-open" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/><path d="M13 12h6"/>
                </svg>
                """;
            case "panel-close" -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M15 4v16"/><path d="M5 12h6"/>
                </svg>
                """;
            default -> """
                <svg class="avatar-control-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                     stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                </svg>
                """;
        };
    }

    private static String sizeLabel(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private static String status(String message, boolean error) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return "<div class=\"" + (error ? "avatar-status-error" : "avatar-status") + "\">" + escape(message) + "</div>";
    }

    private static String title(String action) {
        return switch (action) {
            case "rename" -> "Rename";
            case "copy" -> "Copy";
            case "move" -> "Move";
            case "tag" -> "Tags";
            case "create-folder" -> "Create Folder";
            case "create-text" -> "Create Text File";
            case "create-markdown" -> "Create Markdown File";
            case "delete", "delete-recursive" -> "Delete";
            default -> "File Action";
        };
    }

    private static String time(java.time.Instant instant) {
        return instant == null ? "Unknown" : TIME_FORMAT.format(instant);
    }

    private static String fileName(String path) {
        String normalized = pathOrRoot(path);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String parentPath(String path) {
        String normalized = pathOrRoot(path);
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) {
            return ".";
        }
        return normalized.substring(0, slash);
    }

    private static String pathOrRoot(String path) {
        return path == null || path.isBlank() ? "." : path;
    }

    private static String url(String value) {
        return URLEncoder.encode(pathOrRoot(value), StandardCharsets.UTF_8);
    }

    private static String urlPath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static String escapeAttribute(String value) {
        return escape(value);
    }

    private static String metadataValue(String metadataJson, Pattern pattern) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(metadataJson);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }
}
