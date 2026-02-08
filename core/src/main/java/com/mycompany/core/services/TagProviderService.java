package com.mycompany.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.Map;

public interface TagProviderService {

    /**
     * Get all available tags in the system
     * @return Map of tag ID to tag title (e.g., "myaemproject:topic/automotive" -> "Automotive")
     */
    Map<String, String> getAllAvailableTags(ResourceResolver resolver);

//    /**
//     * Get formatted tags for AI prompt
//     * @return Formatted string of tags for AI
//     */
//    String getFormattedTagsForAI();
}
