package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class WorkspaceFileMetadataRepository {
    public static final String NOTE_LABEL = "note";
    public static final String WORK_AREA_LABEL = "work-area";

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceFileMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
        ensureSystemLabel(NOTE_LABEL, "Note");
        ensureSystemLabel(WORK_AREA_LABEL, "Work Area");
    }

    public WorkspaceFileLabel ensureLabel(String slug, String displayName, boolean system) {
        return ensureLabel(slug, displayName, system, null);
    }

    public WorkspaceFileLabel ensureLabel(
        String slug,
        String displayName,
        boolean system,
        WorkspaceFileLabelTargetType targetType
    ) {
        return ensureLabel(slug, displayName, system, targetType, null);
    }

    public WorkspaceFileLabel ensureLabel(
        String slug,
        String displayName,
        boolean system,
        WorkspaceFileLabelTargetType targetType,
        String description
    ) {
        String normalizedSlug = normalizeSlug(slug);
        Optional<WorkspaceFileLabel> existing = findLabel(normalizedSlug);
        if (existing.isPresent()) {
            WorkspaceFileLabel label = existing.orElseThrow();
            assertTargetTypeCompatibility(label, targetType);
            return existing.orElseThrow();
        }
        Instant now = Instant.now();
        String metadataJson = labelMetadataJson(targetType, description);
        jdbcTemplate.update(
            """
                insert into workspace_file_labels (
                    id, slug, display_name, color, system_flag, metadata_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID().toString(),
            normalizedSlug,
            StringUtils.hasText(displayName) ? displayName.trim() : normalizedSlug,
            null,
            system ? 1 : 0,
            metadataJson,
            now.toString(),
            now.toString()
        );
        return findLabel(normalizedSlug).orElseThrow();
    }

    public WorkspaceFileLabelAssignment addLabel(
        WorkArea workArea,
        String rootRelativePath,
        String fileRelativePath,
        String labelSlug
    ) {
        return addLabel(workArea, rootRelativePath, fileRelativePath, labelSlug, null);
    }

    public WorkspaceFileLabelAssignment addLabel(
        WorkArea workArea,
        String rootRelativePath,
        String fileRelativePath,
        String labelSlug,
        WorkspaceFileLabelTargetType targetType
    ) {
        WorkspaceFileLabel label = ensureLabel(labelSlug, labelSlug, false, targetType);
        assertTargetTypeCompatibility(label, targetType);
        Optional<WorkspaceFileLabelAssignment> existing = findAssignment(
            workArea.workspaceId(), normalizePath(fileRelativePath), label.id());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                insert into workspace_file_label_assignments (
                    id, workspace_id, owner_type, owner_id, root_relative_path, file_relative_path,
                    label_id, metadata_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, '{}', ?, ?)
                """,
            UUID.randomUUID().toString(),
            workArea.workspaceId(),
            workArea.ownerType().name(),
            workArea.ownerId(),
            normalizePath(rootRelativePath),
            normalizePath(fileRelativePath),
            label.id(),
            now.toString(),
            now.toString()
        );
        return findAssignment(workArea.workspaceId(), normalizePath(fileRelativePath), label.id()).orElseThrow();
    }

    public int removeLabel(WorkArea workArea, String fileRelativePath, String labelSlug) {
        Optional<WorkspaceFileLabel> label = findLabel(normalizeSlug(labelSlug));
        if (label.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(
            """
                delete from workspace_file_label_assignments
                where workspace_id = ? and file_relative_path = ? and label_id = ?
                """,
            workArea.workspaceId(),
            normalizePath(fileRelativePath),
            label.orElseThrow().id()
        );
    }

    public List<WorkspaceFileLabel> listLabels(String query, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 250));
        String normalizedQuery = StringUtils.hasText(query) ? query.trim().toLowerCase() : null;
        String like = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        return jdbcTemplate.query(
            """
                select *
                from workspace_file_labels
                where (
                    ? is null
                    or lower(slug) like ?
                    or lower(display_name) like ?
                )
                order by system_flag desc, slug
                limit ?
                """,
            (rs, rowNum) -> toLabel(rs),
            normalizedQuery,
            like,
            like,
            boundedLimit
        );
    }

    public List<WorkspaceFileLabelAssignment> labelsForPath(String workspaceId, String fileRelativePath) {
        return jdbcTemplate.query(
            """
                select a.*, l.id label_pk, l.slug, l.display_name, l.color, l.system_flag,
                    l.metadata_json label_metadata_json, l.created_at label_created_at, l.updated_at label_updated_at
                from workspace_file_label_assignments a
                join workspace_file_labels l on l.id = a.label_id
                where a.workspace_id = ? and a.file_relative_path = ?
                order by l.system_flag desc, l.slug
                """,
            (rs, rowNum) -> toAssignment(rs),
            workspaceId,
            normalizePath(fileRelativePath)
        );
    }

    public List<WorkspaceFileLabelAssignment> labelsForSubtree(String workspaceId, String subtreeRootRelativePath) {
        String normalized = normalizePath(subtreeRootRelativePath);
        return jdbcTemplate.query(
            """
                select a.*, l.id label_pk, l.slug, l.display_name, l.color, l.system_flag,
                    l.metadata_json label_metadata_json, l.created_at label_created_at, l.updated_at label_updated_at
                from workspace_file_label_assignments a
                join workspace_file_labels l on l.id = a.label_id
                where a.workspace_id = ? and (a.file_relative_path = ? or a.file_relative_path like ?)
                order by a.file_relative_path, l.slug
                """,
            (rs, rowNum) -> toAssignment(rs),
            workspaceId,
            normalized,
            normalized + "/%"
        );
    }

    public void moveSubtree(WorkArea workArea, String sourceRootRelativePath, String targetRootRelativePath) {
        List<WorkspaceFileLabelAssignment> assignments = labelsForSubtree(workArea.workspaceId(), sourceRootRelativePath);
        Instant now = Instant.now();
        for (WorkspaceFileLabelAssignment assignment : assignments) {
            String movedPath = replacePrefix(assignment.fileRelativePath(), sourceRootRelativePath, targetRootRelativePath);
            jdbcTemplate.update(
                """
                    update workspace_file_label_assignments
                    set root_relative_path = ?, file_relative_path = ?, updated_at = ?
                    where id = ?
                    """,
                normalizePath(targetRootRelativePath),
                movedPath,
                now.toString(),
                assignment.id()
            );
        }
    }

    public void copySubtree(WorkArea workArea, String sourceRootRelativePath, String targetRootRelativePath) {
        List<WorkspaceFileLabelAssignment> assignments = labelsForSubtree(workArea.workspaceId(), sourceRootRelativePath);
        Instant now = Instant.now();
        for (WorkspaceFileLabelAssignment assignment : assignments) {
            String copiedPath = replacePrefix(assignment.fileRelativePath(), sourceRootRelativePath, targetRootRelativePath);
            jdbcTemplate.update(
                """
                    insert or ignore into workspace_file_label_assignments (
                        id, workspace_id, owner_type, owner_id, root_relative_path, file_relative_path,
                        label_id, metadata_json, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                UUID.randomUUID().toString(),
                assignment.workspaceId(),
                assignment.ownerType().name(),
                assignment.ownerId(),
                normalizePath(targetRootRelativePath),
                copiedPath,
                assignment.label().id(),
                StringUtils.hasText(assignment.metadataJson()) ? assignment.metadataJson() : "{}",
                now.toString(),
                now.toString()
            );
        }
    }

    public int deleteSubtree(String workspaceId, String subtreeRootRelativePath) {
        String normalized = normalizePath(subtreeRootRelativePath);
        return jdbcTemplate.update(
            """
                delete from workspace_file_label_assignments
                where workspace_id = ? and (file_relative_path = ? or file_relative_path like ?)
                """,
            workspaceId,
            normalized,
            normalized + "/%"
        );
    }

    public List<WorkspaceFileLabel> listLabelsForTarget(WorkspaceFileLabelTargetType targetType, String query, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        String normalizedQuery = StringUtils.hasText(query) ? query.trim().toLowerCase() : null;
        String like = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        String target = targetType.wireName();
        return jdbcTemplate.query(
            """
                select *
                from workspace_file_labels
                where (
                    json_extract(metadata_json, '$.targetType') is null
                    or json_extract(metadata_json, '$.targetType') = ?
                )
                  and (
                    ? is null
                    or lower(slug) like ?
                    or lower(display_name) like ?
                  )
                order by system_flag desc, slug
                limit ?
                """,
            (rs, rowNum) -> toLabel(rs),
            target,
            normalizedQuery,
            like,
            like,
            boundedLimit
        );
    }

    private Optional<WorkspaceFileLabel> findLabel(String slug) {
        return jdbcTemplate.query(
            "select * from workspace_file_labels where slug = ?",
            rs -> rs.next() ? Optional.of(toLabel(rs)) : Optional.empty(),
            slug
        );
    }

    private Optional<WorkspaceFileLabelAssignment> findAssignment(String workspaceId, String fileRelativePath, String labelId) {
        return jdbcTemplate.query(
            """
                select a.*, l.id label_pk, l.slug, l.display_name, l.color, l.system_flag,
                    l.metadata_json label_metadata_json, l.created_at label_created_at, l.updated_at label_updated_at
                from workspace_file_label_assignments a
                join workspace_file_labels l on l.id = a.label_id
                where a.workspace_id = ? and a.file_relative_path = ? and a.label_id = ?
                """,
            rs -> rs.next() ? Optional.of(toAssignment(rs)) : Optional.empty(),
            workspaceId,
            fileRelativePath,
            labelId
        );
    }

    private void ensureSystemLabel(String slug, String displayName) {
        ensureLabel(slug, displayName, true);
    }

    private WorkspaceFileLabelAssignment toAssignment(ResultSet rs) throws SQLException {
        return new WorkspaceFileLabelAssignment(
            rs.getString("id"),
            rs.getString("workspace_id"),
            WorkspaceOwnerType.valueOf(rs.getString("owner_type")),
            rs.getString("owner_id"),
            rs.getString("root_relative_path"),
            rs.getString("file_relative_path"),
            new WorkspaceFileLabel(
                rs.getString("label_pk"),
                rs.getString("slug"),
                rs.getString("display_name"),
                rs.getString("color"),
                rs.getInt("system_flag") != 0,
                rs.getString("label_metadata_json"),
                Instant.parse(rs.getString("label_created_at")),
                Instant.parse(rs.getString("label_updated_at"))
            ),
            rs.getString("metadata_json"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private WorkspaceFileLabel toLabel(ResultSet rs) throws SQLException {
        return new WorkspaceFileLabel(
            rs.getString("id"),
            rs.getString("slug"),
            rs.getString("display_name"),
            rs.getString("color"),
            rs.getInt("system_flag") != 0,
            rs.getString("metadata_json"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }

    private String replacePrefix(String path, String sourcePrefix, String targetPrefix) {
        String normalizedPath = normalizePath(path);
        String normalizedSource = normalizePath(sourcePrefix);
        String normalizedTarget = normalizePath(targetPrefix);
        if (normalizedPath.equals(normalizedSource)) {
            return normalizedTarget;
        }
        return normalizedTarget + normalizedPath.substring(normalizedSource.length());
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("label slug is required");
        }
        String normalized = slug.trim().toLowerCase().replace(' ', '-');
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("label slug is invalid");
        }
        return normalized;
    }

    private void assertTargetTypeCompatibility(WorkspaceFileLabel label, WorkspaceFileLabelTargetType requestedType) {
        if (requestedType == null || label == null || !StringUtils.hasText(label.metadataJson())) {
            return;
        }
        String marker = "\"targetType\":\"" + requestedType.wireName() + "\"";
        if (label.metadataJson().contains("\"targetType\"") && !label.metadataJson().contains(marker)) {
            throw new IllegalArgumentException("label target type mismatch for " + label.slug());
        }
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("file path is required");
        }
        return path.trim().replace('\\', '/');
    }

    private String labelMetadataJson(WorkspaceFileLabelTargetType targetType, String description) {
        StringBuilder metadata = new StringBuilder("{");
        boolean appended = false;
        if (targetType != null) {
            metadata.append("\"targetType\":\"").append(escapeJson(targetType.wireName())).append('"');
            appended = true;
        }
        if (StringUtils.hasText(description)) {
            if (appended) {
                metadata.append(',');
            }
            metadata.append("\"description\":\"").append(escapeJson(description.trim())).append('"');
            appended = true;
        }
        if (!appended) {
            return "{}";
        }
        metadata.append('}');
        return metadata.toString();
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
            create table if not exists workspace_file_labels (
                id text primary key,
                slug text not null unique,
                display_name text not null,
                color text,
                system_flag integer not null default 0,
                metadata_json text not null default '{}',
                created_at text not null,
                updated_at text not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists workspace_file_label_assignments (
                id text primary key,
                workspace_id text not null,
                owner_type text not null,
                owner_id text not null,
                root_relative_path text not null,
                file_relative_path text not null,
                label_id text not null,
                metadata_json text not null default '{}',
                created_at text not null,
                updated_at text not null,
                unique(workspace_id, file_relative_path, label_id),
                foreign key(workspace_id) references workspaces(id),
                foreign key(label_id) references workspace_file_labels(id)
            )
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_label_assignments_path
                on workspace_file_label_assignments(workspace_id, file_relative_path)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_label_assignments_label
                on workspace_file_label_assignments(label_id)
            """);
        jdbcTemplate.execute("""
            create index if not exists idx_workspace_file_label_assignments_owner
                on workspace_file_label_assignments(owner_type, owner_id)
            """);
    }
}
