# Playwright Checks
1. `/chat`: saved plan disclosure is collapsed by default, expands, and renders markdown structure.
2. `/chat`: new agent-chat sessions do not appear in the session list.
3. `/agents`: compact card filtering works; cards do not clip; inline chat persists conversation id across sends.
4. `/agents/{id}` history: prior agent chats are visible.
5. `/plans`: continuation works with blank and nonblank optional instruction; list rows wrap; move buttons render as arrows and respect boundaries.
6. Cross-surface regression over `/chat`, `/agents`, and `/plans` with console/network capture.
