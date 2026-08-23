package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.GroupResolutionResult;

import javax.jcr.Session;

/**
 * Resolves a user's complete group membership graph: every group the user
 * belongs to, direct or nested, with the full chain of memberships that
 * leads to it (a group reached via more than one path records all of them).
 */
public interface GroupResolutionService {

    /**
     * @param session an active JCR session used to look up the user manager
     * @param userId  the AEM user ID to resolve
     * @return the resolved group graph, or a result with {@code error} set
     *         if the user does not exist or is not a User authorizable
     */
    GroupResolutionResult resolveGroups(Session session, String userId);
}
