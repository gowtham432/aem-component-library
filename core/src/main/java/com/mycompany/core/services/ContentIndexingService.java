package com.mycompany.core.services;

import com.mycompany.core.models.PageDocument;

import java.util.List;

/**
 * Service for crawling AEM pages and extracting content + component metadata.
 */
public interface ContentIndexingService {

    /**
     * Crawl all pages under the configured content root and return
     * a list of partially populated PageDocuments (content fields only,
     * no analytics).
     *
     * @return list of page content documents
     */
    List<PageDocument> indexAllPages();

    /**
     * Extract content metadata for a single page.
     *
     * @param pagePath JCR path to the cq:Page node
     * @return populated PageDocument (content fields only), or null if not a valid page
     */
    PageDocument indexPage(String pagePath);
}
