package io.mindspice.magenta2.api.web;

import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.simplypages.builders.BannerBuilder;
import io.mindspice.simplypages.builders.ShellBuilder;
import io.mindspice.simplypages.builders.ShellTemplate;
import io.mindspice.simplypages.builders.TopNavBuilder;
import io.mindspice.simplypages.components.RawHtml;
import io.mindspice.simplypages.core.Component;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller
public class FrontendController {

    private final ChatService chatService;

    Component topNavBar = TopNavBuilder.create()
            .addPrimaryLink("Home", "/")
            .addPrimaryLink("Chat", "/chat")
            .build();

    ShellTemplate pageShell = ShellBuilder.create()
            .withPageTitle("Magenta Portal")
            .withTopBanner(BannerBuilder.create()
                    .withLayout(BannerBuilder.BannerLayout.CENTERED)
                    .withTitle("Magenta Portal")
                    .withSubtitle("Portal to the agentic frontier")
                    .build()
            )
            .withTopNav(topNavBar)
            .buildTemplate();

    public FrontendController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/")
    @ResponseBody
    public String home(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            HttpServletResponse response
    ) {
        return pageShell.render();
    }

    private static String chatInterface = """
            <style>
                #chat-page {
                    padding-top: 0.25rem;
                }

                .chat-layout {
                    display: grid;
                    grid-template-columns: auto minmax(0, 1fr);
                    gap: 1rem;
                    align-items: start;
                    min-height: 72vh;
                }

                .chat-sessions details {
                    border: 1px solid #d7dce3;
                    border-radius: 8px;
                    background: #ffffff;
                    width: 17.5rem;
                    overflow: hidden;
                    transition: width 0.18s ease;
                }

                .chat-sessions details:not([open]) {
                    width: 2.8rem;
                }

                .chat-sessions summary {
                    display: flex;
                    align-items: center;
                    gap: 0.45rem;
                    cursor: pointer;
                    user-select: none;
                    list-style: none;
                    padding: 0.65rem 0.8rem;
                    font-size: 0.95rem;
                    font-weight: 700;
                    border-bottom: 1px solid #e7ebf0;
                }

                .chat-sessions details:not([open]) summary {
                    justify-content: center;
                    padding-left: 0;
                    padding-right: 0;
                    border-bottom: none;
                }

                .chat-sessions-label {
                    white-space: nowrap;
                }

                .chat-sessions details:not([open]) .chat-sessions-label {
                    width: 0;
                    opacity: 0;
                    overflow: hidden;
                }

                .chat-sessions summary::-webkit-details-marker {
                    display: none;
                }

                .chat-sessions summary::before {
                    content: "";
                    width: 0.42rem;
                    height: 0.42rem;
                    border-right: 2px solid #5f6774;
                    border-bottom: 2px solid #5f6774;
                    transform: rotate(-45deg);
                    transition: transform 0.16s ease;
                    margin-top: -0.08rem;
                }

                .chat-sessions details[open] summary::before {
                    transform: rotate(135deg);
                }

                #chat-session-list {
                    list-style: none;
                    margin: 0;
                    padding: 0.35rem 0.4rem 0.45rem 0.4rem;
                    display: flex;
                    flex-direction: column;
                    gap: 0.2rem;
                    max-height: 68vh;
                    overflow: auto;
                }

                .chat-session-entry {
                    display: block;
                    border: 1px solid #dbe2ec;
                    border-radius: 6px;
                    padding: 0.42rem 0.52rem;
                    text-decoration: none;
                    color: #2f3a4a;
                    background: #f8fafc;
                    user-select: text;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }

                .chat-session-entry:hover {
                    border-color: #b8c3d6;
                    background: #f0f5fb;
                }

                .chat-session-entry.active {
                    border-color: #4f7fd3;
                    background: #e9f1ff;
                    color: #1d3050;
                }

                .chat-session-empty {
                    color: #596476;
                    font-size: 0.92rem;
                    padding: 0.25rem 0.2rem;
                }

                .chat-main {
                    min-width: 0;
                }

                .chat-toolbar {
                    display: flex;
                    align-items: center;
                    gap: 0.55rem;
                    flex-wrap: wrap;
                    border: 1px solid #d7dce3;
                    border-radius: 8px;
                    background: #ffffff;
                    padding: 0.6rem 0.75rem;
                    margin-bottom: 0.7rem;
                }

                .chat-toolbar label,
                .chat-toolbar span {
                    color: #283548;
                    font-size: 0.9rem;
                    font-weight: 600;
                }

                #chat-model-select {
                    min-width: 210px;
                    padding: 0.28rem 0.4rem;
                    border: 1px solid #c9d2de;
                    border-radius: 4px;
                    background: #ffffff;
                }

                #chat-active-session {
                    padding: 0.16rem 0.32rem;
                    border-radius: 4px;
                    background: #f3f6fb;
                    border: 1px solid #dde4ef;
                }

                #chat-history {
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                    border: 1px solid #ddd;
                    border-radius: 8px;
                    padding: 1rem;
                    height: 58vh;
                    overflow: auto;
                    background: #fff;
                }

                .chat-message {
                    border-radius: 12px;
                    border: 1px solid transparent;
                    padding: 0.75rem 0.9rem;
                    line-height: 1.45;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }

                .chat-message-user {
                    margin-left: 0.35rem;
                    margin-right: 1.3rem;
                    background: #ecf3ff;
                    border-color: #d2def5;
                }

                .chat-message-assistant {
                    margin-left: 1.1rem;
                    margin-right: 0.5rem;
                    background: #f7f7f8;
                    border-color: #e1e3e6;
                }

                .chat-message-role {
                    margin-bottom: 0.35rem;
                    font-size: 0.78rem;
                    font-weight: 700;
                    text-transform: uppercase;
                    letter-spacing: 0.03em;
                    opacity: 0.82;
                }

                .chat-message-body {
                    padding: 0.1rem 0.2rem;
                }

                .chat-message-body > :first-child {
                    margin-top: 0;
                }

                .chat-message-body > :last-child {
                    margin-bottom: 0;
                }

                .chat-message-body p,
                .chat-message-body ul,
                .chat-message-body ol,
                .chat-message-body pre,
                .chat-message-body blockquote,
                .chat-message-body h1,
                .chat-message-body h2,
                .chat-message-body h3,
                .chat-message-body h4,
                .chat-message-body h5,
                .chat-message-body h6 {
                    margin: 0.45rem 0;
                }

                .chat-message-body ul,
                .chat-message-body ol {
                    margin-left: 0;
                    padding-left: 0;
                    list-style-position: inside;
                }

                .chat-message-body li {
                    margin: 0.2rem 0;
                }

                .chat-thinking {
                    margin: 0 0 0.6rem 0;
                    border: 1px solid #d8dce3;
                    border-radius: 10px;
                    background: #ffffff;
                }

                .chat-thinking-toggle {
                    display: flex;
                    align-items: center;
                    gap: 0.45rem;
                    padding: 0.45rem 0.65rem;
                    cursor: pointer;
                    user-select: none;
                    font-size: 0.78rem;
                    font-weight: 600;
                    color: #4b5568;
                    list-style: none;
                }

                .chat-thinking-toggle::-webkit-details-marker {
                    display: none;
                }

                .chat-thinking-toggle::before {
                    content: "";
                    width: 0.4rem;
                    height: 0.4rem;
                    border-right: 2px solid #6b7280;
                    border-bottom: 2px solid #6b7280;
                    transform: rotate(-45deg);
                    transition: transform 0.16s ease;
                    margin-top: -0.08rem;
                }

                .chat-thinking[open] .chat-thinking-toggle::before {
                    transform: rotate(45deg);
                }

                .chat-thinking-hide {
                    display: none;
                }

                .chat-thinking[open] .chat-thinking-show {
                    display: none;
                }

                .chat-thinking[open] .chat-thinking-hide {
                    display: inline;
                }

                .chat-thinking-body {
                    border-top: 1px solid #e5e7eb;
                    padding: 0.6rem 0.75rem 0.7rem 0.75rem;
                    color: #4b5563;
                    font-size: 0.92rem;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }

                .chat-thinking-body > :first-child {
                    margin-top: 0;
                }

                .chat-thinking-body > :last-child {
                    margin-bottom: 0;
                }

                .chat-thinking-body p,
                .chat-thinking-body ul,
                .chat-thinking-body ol,
                .chat-thinking-body pre,
                .chat-thinking-body blockquote,
                .chat-thinking-body h1,
                .chat-thinking-body h2,
                .chat-thinking-body h3,
                .chat-thinking-body h4,
                .chat-thinking-body h5,
                .chat-thinking-body h6 {
                    margin: 0.45rem 0;
                }

                .chat-thinking-body ul,
                .chat-thinking-body ol {
                    margin-left: 0;
                    padding-left: 0;
                    list-style-position: inside;
                }

                .chat-thinking-body li {
                    margin: 0.2rem 0;
                }

                #chat-form {
                    margin-top: 0.8rem;
                    display: flex;
                    gap: 0.55rem;
                    align-items: flex-end;
                }

                #chat-input {
                    flex: 1;
                    resize: vertical;
                    min-height: 8rem;
                }

                #chat-error {
                    min-height: 1.25rem;
                    margin-top: 0.45rem;
                    color: #842029;
                    font-size: 0.92rem;
                }

                @media (max-width: 980px) {
                    .chat-layout {
                        grid-template-columns: minmax(0, 1fr);
                        min-height: auto;
                    }

                    .chat-sessions details,
                    .chat-sessions details:not([open]) {
                        width: 100%%;
                    }

                    .chat-sessions details:not([open]) summary {
                        justify-content: flex-start;
                        padding-left: 0.8rem;
                        padding-right: 0.8rem;
                    }

                    .chat-sessions details:not([open]) .chat-sessions-label {
                        width: auto;
                        opacity: 1;
                    }

                    #chat-session-list {
                        max-height: 18rem;
                    }
                }
            </style>
            <section id="chat-page" data-chat-root="true" data-active-conversation-id="%s">
                <div class="chat-layout">
                    <aside class="chat-sessions">
                        <details open>
                            <summary><span class="chat-sessions-label">Sessions</span></summary>
                            <ul id="chat-session-list"></ul>
                        </details>
                    </aside>
                    <div class="chat-main">
                        <div class="chat-toolbar">
                            <label for="chat-model-select">Model</label>
                            <select id="chat-model-select">%s</select>
                            <span>Session</span>
                            <code id="chat-active-session">%s</code>
                        </div>
                        <div id="chat-history"></div>
                        <form id="chat-form">
                            <textarea id="chat-input" name="message" autocomplete="off" placeholder="Type a message (Enter to send, Shift+Enter newline)" rows="6"></textarea>
                            <button type="submit">Send</button>
                        </form>
                        <div id="chat-error" role="status" aria-live="polite"></div>
                    </div>
                </div>
            </section>
            """;

    ShellTemplate chatShell = ShellBuilder.create()
            .withPageTitle("Magenta Chat")
            .withTopBanner(BannerBuilder.create()
                    .withLayout(BannerBuilder.BannerLayout.CENTERED)
                    .withTitle("Magenta Chat")
                    .withSubtitle("Session-backed chat bootstrap")
                    .build())
            .withTopNav(topNavBar)
            .addCustomJs("/js/chat-client.js?v=7")
            .buildTemplate();

    @GetMapping("/chat")
    @ResponseBody
    public String chat(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            HttpServletResponse response
    ) {
        String conversationId = chatService.newConversationId();
        String view = chatInterface.formatted(conversationId, buildModelOptionsHtml(), conversationId);
        return chatShell.renderWithContent(RawHtml.create(view));
    }

    private String buildModelOptionsHtml() {
        String defaultModel = chatService.defaultModel();
        List<String> models = new ArrayList<>(chatService.availableModels());
        if (defaultModel != null && !defaultModel.isBlank() && !models.contains(defaultModel)) {
            models.add(0, defaultModel);
        }
        return models.stream()
            .map(model -> {
                String escaped = escapeHtml(model);
                String selected = defaultModel != null && defaultModel.equals(model) ? " selected" : "";
                return "<option value=\"" + escaped + "\"" + selected + ">" + escaped + "</option>";
            })
            .reduce("", String::concat);
    }

    private String escapeHtml(String value) {
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
}
