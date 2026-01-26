package com.magenta.tools;

import com.magenta.security.SecurityManager;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;

public class WebTools {

    @Tool("Fetch and extract the main text content from a given URL. Useful for reading documentation or articles.")
    public String fetchUrl(String url) {
        if (!SecurityManager.requireApproval("web_fetch", url)) {
            return "Error: URL fetch denied by user or security policy.";
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(10000)
                    .get();
            
            // Basic cleanup: extract body text
            String text = doc.body().text();
            
            // Truncate if too long (simple heuristic to avoid context overflow)
            if (text.length() > 10000) {
                return text.substring(0, 10000) + "... [Content Truncated]";
            }
            return text;
        } catch (IOException e) {
            return "Error fetching URL: " + e.getMessage();
        }
    }

    @Tool("Perform a web search for a query. Returns a list of result snippets. (Mock implementation until API key is configured)")
    public String webSearch(String query) {
        // Real implementation would use Google Custom Search API or DuckDuckGo
        // For now, we return a placeholder to avoid breaking if called.
        return "Search functionality is currently in mock mode. Please use fetchUrl if you have a specific link.";
    }
}
