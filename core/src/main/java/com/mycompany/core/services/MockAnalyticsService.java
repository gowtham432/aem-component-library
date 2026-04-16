package com.mycompany.core.services;

import com.mycompany.core.models.PageDocument;

/**
 * Service that generates and stores deterministic mock analytics data per page.
 */
public interface MockAnalyticsService {

    /**
     * Enrich a PageDocument with mock analytics data.
     * Analytics are deterministic based on the page path (seeded random).
     *
     * @param doc the PageDocument to enrich (modifies in place)
     */
    void enrichWithAnalytics(PageDocument doc);

    /**
     * Load previously persisted analytics from JCR for a given page.
     * Returns null if no persisted data exists.
     *
     * @param pagePath the page path
     * @return the PageDocument with analytics fields, or null
     */
    PageDocument loadFromJcr(String pagePath);

    /**
     * Persist the analytics fields of a PageDocument to JCR.
     *
     * @param doc the PageDocument to persist
     */
    void saveToJcr(PageDocument doc);
}
