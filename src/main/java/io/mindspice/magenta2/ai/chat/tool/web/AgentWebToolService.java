package io.mindspice.magenta2.ai.chat.tool.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.WebSearchConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentWebToolService {
    private static final int DEFAULT_SEARCH_RESULTS = 5;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int DEFAULT_FETCH_CHARS = 12_000;
    private static final int MAX_FETCH_CHARS = 20_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    // Maximum HTTP response body to read into memory before truncation (5 MB).
    // Override with system property: -Dmagenta.web.maxResponseBytes=5242880
    private static final long MAX_RESPONSE_BYTES =
        Long.parseLong(System.getProperty("magenta.web.maxResponseBytes", Long.toString(5_242_880)));

    // Maximum SearXNG JSON response body (2 MB).
    private static final long MAX_SEARCH_RESPONSE_BYTES =
        Long.parseLong(System.getProperty("magenta.web.maxSearchResponseBytes", Long.toString(2_097_152)));

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean allowPrivateFetchHosts;

    @Autowired
    public AgentWebToolService(AiConfig aiConfig, ObjectMapper objectMapper) {
        this(
            aiConfig,
            objectMapper,
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(REQUEST_TIMEOUT)
                .build(),
            false
        );
    }

    AgentWebToolService(AiConfig aiConfig, ObjectMapper objectMapper, HttpClient httpClient) {
        this(aiConfig, objectMapper, httpClient, true);
    }

    private AgentWebToolService(AiConfig aiConfig, ObjectMapper objectMapper, HttpClient httpClient, boolean allowPrivateFetchHosts) {
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.allowPrivateFetchHosts = allowPrivateFetchHosts;
    }

    public WebSearchResult search(String query, Integer maxResults) throws IOException, InterruptedException {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required");
        }
        WebSearchConfig config = requireSearxng();
        int limit = clamp(maxResults, DEFAULT_SEARCH_RESULTS, 1, MAX_SEARCH_RESULTS);
        URI uri = searchUri(config.baseUrl(), query);
        BoundedBody bounded = sendBounded(uri, "application/json", MAX_SEARCH_RESPONSE_BYTES);
        JsonNode root = objectMapper.readTree(bounded.content());
        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray()) {
            throw new IllegalStateException("web_search response did not contain a SearXNG results array");
        }
        List<WebSearchItem> results = new ArrayList<>();
        for (JsonNode result : resultsNode) {
            if (results.size() >= limit) {
                break;
            }
            String url = text(result, "url");
            if (!StringUtils.hasText(url)) {
                continue;
            }
            results.add(new WebSearchItem(
                text(result, "title"),
                url,
                text(result, "content"),
                text(result, "engine"),
                text(result, "publishedDate")
            ));
        }
        return new WebSearchResult(query.trim(), results, resultsNode.size() > results.size());
    }

    public WebFetchResult fetch(String url, Integer maxCharacters) throws IOException, InterruptedException {
        requireWebEnabled();
        URI uri = validatedHttpUri(url);
        int limit = clamp(maxCharacters, DEFAULT_FETCH_CHARS, 1_000, MAX_FETCH_CHARS);
        BoundedBody bounded = sendBounded(uri, "text/html,text/plain;q=0.9,*/*;q=0.1", MAX_RESPONSE_BYTES);
        String contentType = "text/html"; // approximate — Content-Type not available from bounded reader
        String text;
        String title = "";
        String body = bounded.content();
        if (body.contains("<html")) {
            Document document = Jsoup.parse(body, uri.toString());
            document.select("script,style,noscript,svg,canvas,form,nav,header,footer,aside").remove();
            title = document.title();
            text = document.body() == null ? document.text() : document.body().text();
        } else {
            text = body;
        }
        text = normalizeWhitespace(text);
        boolean truncated = bounded.bodyTruncated() || text.length() > limit;
        if (text.length() > limit) {
            text = text.substring(0, limit).trim();
        }
        return new WebFetchResult(uri.toString(), title, text, truncated, contentType);
    }

    private HttpResponse<String> send(URI uri, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", accept)
            .header("User-Agent", "Magenta/1.0 (+https://local.magenta)")
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private record BoundedBody(String content, boolean bodyTruncated) {}

    private BoundedBody sendBounded(URI uri, String accept, long maxBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", accept)
            .header("User-Agent", "Magenta/1.0 (+https://local.magenta)")
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            BoundedBody errorBody = readBounded(response, 8192);
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + errorBody.content());
        }
        return readBounded(response, maxBytes);
    }

    private BoundedBody readBounded(HttpResponse<InputStream> response, long maxBytes) throws IOException {
        try (InputStream in = response.body()) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long total = 0;
            int read;
            boolean truncated = false;
            while ((read = in.read(buffer)) != -1) {
                long remaining = maxBytes - total;
                if (remaining <= 0) {
                    truncated = true;
                    while (in.read(buffer) != -1) { /* discard remainder */ }
                    break;
                }
                int toWrite = (int) Math.min(read, remaining);
                out.write(buffer, 0, toWrite);
                total += toWrite;
                if (read > remaining) {
                    truncated = true;
                    while (in.read(buffer) != -1) { /* discard remainder */ }
                    break;
                }
            }
            String content = out.toString(StandardCharsets.UTF_8);
            if (truncated) {
                content += "\n\n[Response body exceeded maximum size of " + maxBytes
                    + " bytes. Content was truncated during download to prevent memory exhaustion.]";
            }
            return new BoundedBody(content, truncated);
        }
    }

    private WebSearchConfig requireSearxng() {
        WebSearchConfig config = requireWebEnabled();
        String provider = StringUtils.hasText(config.provider()) ? config.provider().trim().toLowerCase(Locale.ROOT) : "searxng";
        if (!"searxng".equals(provider)) {
            throw new IllegalStateException("Unsupported webSearch provider: " + config.provider());
        }
        if (!StringUtils.hasText(config.baseUrl())) {
            throw new IllegalStateException("webSearch.baseUrl is required for SearXNG");
        }
        return config;
    }

    private WebSearchConfig requireWebEnabled() {
        WebSearchConfig config = aiConfig == null ? null : aiConfig.webSearch();
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("web tools are not enabled in AI config");
        }
        return config;
    }

    private URI searchUri(String baseUrl, String query) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return URI.create(normalized + "/search?q=" + encodedQuery + "&format=json&pageno=1&safesearch=1");
    }

    private URI validatedHttpUri(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url is required");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("web_fetch only supports http and https URLs");
        }
        if (!allowPrivateFetchHosts && privateOrLocalHost(uri.getHost())) {
            throw new IllegalArgumentException("web_fetch only supports public web hosts");
        }
        return uri;
    }

    private boolean privateOrLocalHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress();
        } catch (IOException exception) {
            throw new IllegalArgumentException("web_fetch could not resolve host: " + host, exception);
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int actual = value == null ? defaultValue : value;
        return Math.min(max, Math.max(min, actual));
    }

    public record WebSearchResult(String query, List<WebSearchItem> results, boolean truncated) {
    }

    public record WebSearchItem(String title, String url, String snippet, String engine, String publishedDate) {
    }

    public record WebFetchResult(String url, String title, String text, boolean truncated, String contentType) {
    }
}
