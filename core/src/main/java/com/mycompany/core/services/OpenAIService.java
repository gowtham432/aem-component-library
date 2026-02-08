package com.mycompany.core.services;

import java.util.List;
import java.util.Map;

public interface OpenAIService {
    List<String> extractConcepts(String content);

    List<String> generateTagSuggestions(String pageContent, Map<String, String> availableTags);

    String classifyContentType(String content);
}
