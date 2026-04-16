package com.mycompany.core.services.impl;

import com.mycompany.core.services.AIConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(service = AIConfigService.class, immediate = true)
@Designate(ocd = AIConfigServiceImpl.Config.class)
public class AIConfigServiceImpl implements AIConfigService {

    @ObjectClassDefinition(name = "Content Intelligence - AI Configuration")
    public @interface Config {

        @AttributeDefinition(name = "OpenAI API Key", description = "Secret API key from platform.openai.com")
        String openai_api_key() default "";

        @AttributeDefinition(name = "OpenAI Embedding Model")
        String openai_embedding_model() default "text-embedding-3-small";

        @AttributeDefinition(name = "OpenAI Chat Model")
        String openai_chat_model() default "gpt-4.1-mini";

        @AttributeDefinition(name = "Pinecone API Key", description = "API key from app.pinecone.io")
        String pinecone_api_key() default "";

        @AttributeDefinition(name = "Pinecone Index Name")
        String pinecone_index_name() default "content-intelligence";

        @AttributeDefinition(name = "Pinecone Base URL",
                description = "Full host URL from Pinecone console, e.g. https://content-intelligence-abc123.svc.aped-4627-b74a.pinecone.io")
        String pinecone_base_url() default "";

        @AttributeDefinition(name = "Content Root Path",
                description = "JCR path to crawl for page indexing")
        String content_root_path() default "/content/myaemproject/us/en";
    }

    private String openAiApiKey;
    private String openAiEmbeddingModel;
    private String openAiChatModel;
    private String pineconeApiKey;
    private String pineconeIndexName;
    private String pineconeBaseUrl;
    private String contentRootPath;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.openAiApiKey = config.openai_api_key();
        this.openAiEmbeddingModel = config.openai_embedding_model();
        this.openAiChatModel = config.openai_chat_model();
        this.pineconeApiKey = config.pinecone_api_key();
        this.pineconeIndexName = config.pinecone_index_name();
        this.pineconeBaseUrl = config.pinecone_base_url();
        this.contentRootPath = config.content_root_path();
    }

    @Override public String getOpenAiApiKey() { return openAiApiKey; }
    @Override public String getOpenAiEmbeddingModel() { return openAiEmbeddingModel; }
    @Override public String getOpenAiChatModel() { return openAiChatModel; }
    @Override public String getPineconeApiKey() { return pineconeApiKey; }
    @Override public String getPineconeIndexName() { return pineconeIndexName; }
    @Override public String getPineconeBaseUrl() { return pineconeBaseUrl; }
    @Override public String getContentRootPath() { return contentRootPath; }
}
