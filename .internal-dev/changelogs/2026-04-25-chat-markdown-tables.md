# Date

2026-04-25

# Change Summary

Enabled GitHub-flavored markdown table rendering for chat messages and added table styling for the browser chat surface.
Fixed the chat page format string so literal CSS percent signs do not break `/chat` rendering.

# Files

- `pom.xml`
- `src/main/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRenderer.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRendererTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

# Behavioral Impact

Assistant responses that contain markdown pipe tables now render as HTML tables instead of collapsed inline text. Table HTML is explicitly allowed by the sanitizer, and wide tables can scroll horizontally inside chat messages.

# Risks

Very wide generated tables may still require horizontal scrolling. The sanitizer strips unsupported table attributes such as alignment.

# Follow-up Items

None.
