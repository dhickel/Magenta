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
                    position: relative;
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
                    width: 1rem;
                    height: 0.78rem;
                    border: 2px solid #5f6774;
                    border-radius: 0.42rem;
                    box-sizing: border-box;
                    position: relative;
                }

                .chat-sessions summary::after {
                    content: "";
                    width: 0.35rem;
                    height: 0.35rem;
                    border-left: 2px solid #5f6774;
                    border-bottom: 2px solid #5f6774;
                    transform: rotate(-18deg);
                    position: absolute;
                    left: 0.98rem;
                    top: 1.08rem;
                }

                .chat-sessions details[open] summary::before {
                    border-color: #40506a;
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

                .chat-session-bulk {
                    border-top: 1px solid #dde4ef;
                    padding: 0.35rem 0.4rem;
                }

                .chat-session-bulk-actions {
                    display: grid;
                    grid-template-columns: auto repeat(3, minmax(0, 1fr));
                    align-items: center;
                    gap: 0.25rem;
                }

                .chat-session-select-all {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    width: 1.8rem;
                    height: 1.8rem;
                    border: 1px solid #cbd5e1;
                    border-radius: 4px;
                    background: #ffffff;
                }

                .chat-session-select-all input {
                    width: 1rem;
                    height: 1rem;
                    margin: 0;
                    cursor: pointer;
                }

                .chat-session-bulk-actions button {
                    min-height: 1.8rem;
                    border: 1px solid #cbd5e1;
                    border-radius: 4px;
                    background: #ffffff;
                    color: #334155;
                    cursor: pointer;
                    font-size: 0.78rem;
                }

                .chat-session-item {
                    display: block;
                }

                .chat-session-entry {
                    display: flex;
                    flex-direction: column;
                    gap: 0.24rem;
                    border: 1px solid #dbe2ec;
                    border-radius: 6px;
                    padding: 0.36rem 0.45rem 0.45rem;
                    color: #2f3a4a;
                    background: #f8fafc;
                    user-select: text;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }

                .chat-session-topline {
                    display: grid;
                    grid-template-columns: auto minmax(0, 1fr) auto;
                    align-items: center;
                    gap: 0.35rem;
                    min-height: 1.75rem;
                }

                .chat-session-check {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    min-width: 1.4rem;
                    height: 1.4rem;
                }

                .chat-session-check input {
                    width: 1rem;
                    height: 1rem;
                    margin: 0;
                    cursor: pointer;
                }

                .chat-session-actions {
                    display: flex;
                    flex-direction: row;
                    flex-wrap: wrap;
                    justify-content: flex-end;
                    gap: 0.2rem;
                }

                .chat-session-actions button,
                .chat-session-rename button {
                    width: 1.75rem;
                    height: 1.75rem;
                    border: 1px solid #cbd5e1;
                    border-radius: 4px;
                    background: #ffffff;
                    color: #334155;
                    cursor: pointer;
                    line-height: 1;
                }

                .chat-session-actions button.favorite {
                    color: #9a6700;
                }

                .chat-session-actions button:hover,
                .chat-session-rename button:hover {
                    border-color: #94a3b8;
                    background: #f1f5f9;
                }

                .chat-session-rename {
                    display: grid;
                    grid-template-columns: minmax(0, 1fr) auto auto;
                    gap: 0.25rem;
                    align-items: center;
                }

                .chat-session-rename input {
                    min-width: 0;
                    height: 1.75rem;
                    box-sizing: border-box;
                    border: 1px solid #94a3b8;
                    border-radius: 4px;
                    padding: 0.2rem 0.35rem;
                }

                .chat-session-inline-hash {
                    min-width: 0;
                    justify-self: start;
                    color: #64748b;
                    font-size: 0.68rem;
                    line-height: 1;
                    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                .chat-session-title {
                    display: flex;
                    flex-direction: column;
                    align-items: flex-start;
                    text-decoration: none;
                    color: inherit;
                }

                .chat-session-title-label {
                    display: block;
                    max-width: 100%%;
                    color: #263246;
                    font-weight: 700;
                    font-size: 0.9rem;
                    line-height: 1.2;
                }

                .chat-session-title-text {
                    display: block;
                    overflow: hidden;
                    text-overflow: ellipsis;
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

                #chat-plan-status {
                    display: none;
                    flex-direction: column;
                    align-items: stretch;
                    gap: 0.75rem;
                    border: 1px solid #cdd7e3;
                    border-radius: 6px;
                    background: #f8fafc;
                    padding: 0.55rem 0.7rem;
                    margin-bottom: 0.7rem;
                    color: #263246;
                    font-size: 0.88rem;
                }

                #chat-plan-status.active {
                    display: flex;
                }

                .chat-plan-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 0.75rem;
                }

                #chat-plan-title {
                    font-weight: 700;
                }

                #chat-plan-hint {
                    color: #5a6779;
                    font-size: 0.82rem;
                }

                #chat-plan-evidence ul {
                    margin: 0;
                    padding-left: 1.1rem;
                    color: #344154;
                    font-size: 0.82rem;
                }

                #chat-planning-panel {
                    display: none;
                    border: 1px solid #cbd7e6;
                    border-radius: 8px;
                    background: #ffffff;
                    padding: 0.75rem;
                    margin-top: 0.8rem;
                    color: #243247;
                }

                #chat-planning-panel.active {
                    display: block;
                }

                .planning-panel-title {
                    font-weight: 700;
                    margin-bottom: 0.45rem;
                }

                .planning-panel-progress {
                    color: #5b687a;
                    font-size: 0.8rem;
                    font-weight: 700;
                    margin-bottom: 0.3rem;
                }

                .planning-panel-body {
                    display: flex;
                    flex-direction: column;
                    gap: 0.55rem;
                }

                .planning-options {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
                    gap: 0.4rem;
                }

                .planning-options label {
                    display: flex;
                    align-items: flex-start;
                    gap: 0.35rem;
                    border: 1px solid #d8e0ea;
                    border-radius: 6px;
                    padding: 0.45rem 0.5rem;
                    background: #f8fafc;
                    cursor: pointer;
                }

                #chat-planning-panel textarea {
                    width: 100%%;
                    min-height: 3.2rem;
                    box-sizing: border-box;
                    resize: vertical;
                }

                .planning-actions {
                    display: flex;
                    flex-wrap: wrap;
                    justify-content: flex-end;
                    gap: 0.45rem;
                }

                .planning-preview-document {
                    margin: 0;
                    word-break: break-word;
                    border: 1px solid #d7dee8;
                    border-radius: 6px;
                    background: #ffffff;
                    padding: 0.7rem 0.8rem;
                    color: #1f2937;
                    font-size: 0.9rem;
                    line-height: 1.45;
                }

                .planning-preview-document > :first-child {
                    margin-top: 0;
                }

                .planning-preview-document > :last-child {
                    margin-bottom: 0;
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

                .chat-message-system {
                    margin-left: 2rem;
                    margin-right: 2rem;
                    background: #fff8e5;
                    border-color: #ead9a7;
                    color: #4b4126;
                }

                .chat-message-tool {
                    margin-left: 1.2rem;
                    margin-right: 0.65rem;
                    background: #f4f7fa;
                    border-color: #d7e0ea;
                    color: #253244;
                }

                .chat-message-transient {
                    border-style: dashed;
                    color: #42526a;
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
                .chat-message-body table,
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

                .chat-message-body table,
                .chat-thinking-body table {
                    display: block;
                    max-width: 100%%;
                    overflow-x: auto;
                    border-collapse: collapse;
                    font-size: 0.92rem;
                    white-space: nowrap;
                }

                .chat-message-body th,
                .chat-message-body td,
                .chat-thinking-body th,
                .chat-thinking-body td {
                    border: 1px solid #d8dde6;
                    padding: 0.35rem 0.5rem;
                    text-align: left;
                    vertical-align: top;
                }

                .chat-message-body th,
                .chat-thinking-body th {
                    background: #eef2f7;
                    color: #263246;
                    font-weight: 700;
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
                .chat-thinking-body table,
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

                .chat-tool {
                    margin: 0;
                    border: 1px solid #ccd6e2;
                    border-radius: 8px;
                    background: #ffffff;
                    overflow: hidden;
                }

                .chat-tool-toggle {
                    display: grid;
                    grid-template-columns: auto minmax(5rem, 10rem) auto minmax(0, 1fr);
                    align-items: center;
                    gap: 0.45rem;
                    padding: 0.48rem 0.65rem;
                    cursor: pointer;
                    user-select: none;
                    list-style: none;
                    font-size: 0.82rem;
                }

                .chat-tool-toggle::-webkit-details-marker {
                    display: none;
                }

                .chat-tool-toggle::before {
                    content: "";
                    width: 0.4rem;
                    height: 0.4rem;
                    border-right: 2px solid #607087;
                    border-bottom: 2px solid #607087;
                    transform: rotate(-45deg);
                    transition: transform 0.16s ease;
                    margin-top: -0.08rem;
                }

                .chat-tool[open] .chat-tool-toggle::before {
                    transform: rotate(45deg);
                }

                .chat-tool-name {
                    font-weight: 700;
                    color: #223149;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }

                .chat-tool-status {
                    border: 1px solid #c9d3df;
                    border-radius: 999px;
                    padding: 0.05rem 0.38rem;
                    color: #405066;
                    background: #f6f8fb;
                    font-size: 0.74rem;
                    white-space: nowrap;
                }

                .chat-tool-summary {
                    min-width: 0;
                    color: #3e4c60;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                .chat-tool-body {
                    border-top: 1px solid #e3e8ee;
                    padding: 0.6rem 0.7rem 0.75rem 0.7rem;
                    display: flex;
                    flex-direction: column;
                    gap: 0.55rem;
                }

                .chat-tool-meta,
                .chat-tool-label,
                .chat-tool-muted {
                    color: #5d6a7d;
                    font-size: 0.78rem;
                }

                .chat-tool-label {
                    margin-bottom: 0.22rem;
                    font-weight: 700;
                }

                .chat-tool-section pre {
                    margin: 0;
                    max-height: 18rem;
                    overflow: auto;
                    white-space: pre-wrap;
                    word-break: break-word;
                    border: 1px solid #d7dee8;
                    border-radius: 6px;
                    background: #f8fafc;
                    padding: 0.55rem 0.62rem;
                    color: #1f2937;
                    font-size: 0.82rem;
                    line-height: 1.4;
                }

                #chat-form {
                    margin-top: 0.8rem;
                    display: flex;
                    flex-direction: column;
                    gap: 0.55rem;
                    align-items: stretch;
                }

                #chat-input {
                    width: 100%%;
                    box-sizing: border-box;
                    resize: vertical;
                    min-height: 8rem;
                }

                #chat-form button[type="submit"] {
                    align-self: flex-end;
                }

                #chat-error {
                    min-height: 1.25rem;
                    margin-top: 0.45rem;
                    color: #842029;
                    font-size: 0.92rem;
                }

                #chat-token-usage {
                    position: fixed;
                    right: 1rem;
                    bottom: 1rem;
                    z-index: 20;
                    min-width: 10.5rem;
                    border: 1px solid #c9d2de;
                    border-radius: 6px;
                    background: rgba(255, 255, 255, 0.96);
                    box-shadow: 0 0.25rem 0.85rem rgba(18, 27, 38, 0.12);
                    padding: 0.45rem 0.6rem;
                    color: #263246;
                    font-size: 0.82rem;
                }

                #chat-token-usage-label {
                    display: flex;
                    justify-content: space-between;
                    gap: 0.75rem;
                    margin-bottom: 0.28rem;
                    font-weight: 700;
                }

                #chat-token-usage-bar {
                    height: 0.38rem;
                    overflow: hidden;
                    border-radius: 999px;
                    background: #e8edf4;
                }

                #chat-token-usage-fill {
                    width: 0%%;
                    height: 100%%;
                    border-radius: inherit;
                    background: #4f7fd3;
                    transition: width 0.18s ease;
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
                            <div class="chat-session-bulk">
                                <div class="chat-session-bulk-actions">
                                    <label class="chat-session-select-all" title="Select all chats">
                                        <input type="checkbox" id="chat-session-select-all" aria-label="Select all chats">
                                    </label>
                                    <button type="button" data-bulk-action="delete">Delete</button>
                                    <button type="button" data-bulk-action="archive">Archive</button>
                                    <button type="button" data-bulk-action="favorite">Favorite</button>
                                </div>
                            </div>
                            <ul id="chat-session-list"></ul>
                        </details>
                    </aside>
                    <div class="chat-main">
                        <div class="chat-toolbar">
                            <label for="chat-model-select">Agent Model</label>
                            <select id="chat-model-select">%s</select>
                            <label for="chat-planning-model-select">Planning Model</label>
                            <select id="chat-planning-model-select">%s</select>
                            <span>Session</span>
                            <code id="chat-active-session">%s</code>
                        </div>
                        <div id="chat-plan-status" aria-live="polite">
                            <div class="chat-plan-header">
                                <span id="chat-plan-title"></span>
                                <span id="chat-plan-hint"></span>
                            </div>
                            <div id="chat-plan-evidence"></div>
                        </div>
                        <div id="chat-history"></div>
                        <div id="chat-planning-panel" aria-live="polite"></div>
                        <form id="chat-form">
                            <textarea id="chat-input" name="message" autocomplete="off" placeholder="Type a message (Enter to send, Shift+Enter newline)" rows="6"></textarea>
                            <button type="submit">Send</button>
                        </form>
                        <div id="chat-error" role="status" aria-live="polite"></div>
                    </div>
                </div>
                <div id="chat-token-usage" aria-live="polite">
                    <div id="chat-token-usage-label">
                        <span>Context</span>
                        <span id="chat-token-usage-text">0 / 0 (0%%)</span>
                    </div>
                    <div id="chat-token-usage-bar"><div id="chat-token-usage-fill"></div></div>
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
            .addCustomJs("/js/chat-client.js?v=23")
            .buildTemplate();

    @GetMapping("/chat")
    @ResponseBody
    public String chat(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            HttpServletResponse response
    ) {
        String view = chatInterface.formatted("", buildModelOptionsHtml(chatService.defaultModel()), buildModelOptionsHtml(chatService.planningModel()), "New chat");
        return chatShell.renderWithContent(RawHtml.create(view));
    }

    private String buildModelOptionsHtml(String defaultModel) {
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
