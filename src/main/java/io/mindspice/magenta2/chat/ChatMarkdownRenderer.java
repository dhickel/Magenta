package io.mindspice.magenta2.chat;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public class ChatMarkdownRenderer {

    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
        .allowElements(
            "p", "br", "hr",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "strong", "em", "code", "pre", "blockquote",
            "ul", "ol", "li",
            "a"
        )
        .allowAttributes("href", "title").onElements("a")
        .allowUrlProtocols("http", "https", "mailto")
        .requireRelNofollowOnLinks()
        .toFactory();

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String render(String markdown) {
        String source = markdown == null ? "" : markdown;
        String rendered = renderer.render(parser.parse(source));
        return rendered;
        //return HTML_POLICY.sanitize(rendered);
    }
}
