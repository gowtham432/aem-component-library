package com.mycompany.core.services;

/**
 * OSGi configuration service that holds all API keys and settings
 * for the Content Intelligence feature.
 */
public interface AIConfigService {

    String getOpenAiApiKey();

    String getOpenAiEmbeddingModel();

    String getOpenAiChatModel();

    String getPineconeApiKey();

    String getPineconeIndexName();

    String getPineconeBaseUrl();

    String getContentRootPath();
}
