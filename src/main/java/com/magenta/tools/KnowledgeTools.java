package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;

public class KnowledgeTools {

    public KnowledgeTools() {
    }

    @Tool("Store a piece of knowledge or a fact in the long-term memory.")
    public String storeKnowledge(String content) {
        return "Knowledge storage is disabled.";
    }

    @Tool("Retrieve relevant knowledge from long-term memory based on a query.")
    public String retrieveKnowledge(String query) {
        return "Knowledge retrieval is disabled.";
    }
}