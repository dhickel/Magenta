package com.magenta.tools;

import com.magenta.memory.VectorStoreService;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

public class KnowledgeTools {

    private final VectorStoreService vectorStoreService;

    public KnowledgeTools(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Tool("Store a piece of knowledge or a fact in the long-term memory.")
    public String storeKnowledge(String content) {
        vectorStoreService.store(content);
        return "Knowledge stored successfully.";
    }

    @Tool("Retrieve relevant knowledge from long-term memory based on a query.")
    public String retrieveKnowledge(String query) {
        List<String> results = vectorStoreService.search(query, 3);
        if (results.isEmpty()) {
            return "No relevant knowledge found.";
        }
        return "Found relevant knowledge:\n" + String.join("\n---\n", results);
    }
}
