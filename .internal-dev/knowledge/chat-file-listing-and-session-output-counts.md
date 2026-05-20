# Chat File Listing And Session Output Counts

## Topic

Ordinary chat file discovery for `/chat` session cards and the active-session outputs panel.

## Source References

- `WorkspaceDirectoryService.chatFiles(conversationId)` creates and confines persistent chat file directories at `chats/<conversationId>/files/`.
- `ChatFileService` lists and downloads regular files from that directory.
- `ChatService.session(...)` attaches `outputCount` to `ChatSession`.
- `ChatFileController` exposes listing and download routes under `/api/chat/{conversationId}/files`.

## Key Takeaways

- Ordinary chat files are separate from `run_output_artifacts`; they are filesystem content scoped to a chat conversation.
- File listing returns relative paths only and derives simple format labels from extensions.
- Download resolution normalizes the requested relative path, resolves the real path, and rejects traversal, directories, and files outside the chat file root.
- `WorkspaceDirectoryService.chatFiles(...)` creates the directory on read, so count/list calls can materialize empty chat file directories.

## Engine Relevance

Use `ChatFileService` for chat-file metadata and downloads instead of querying output artifacts. Keep artifact routes for run-owned outputs and chat file routes for conversation-owned files.

## Open Questions

- Whether output counts need caching or a persisted index if session lists grow large.
- Whether dotfiles should remain visible; the current behavior lists all regular files.
