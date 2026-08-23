package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.ACLCollectionResult;

import javax.jcr.Session;
import java.util.Set;

/**
 * Queries the repository for every ACE set for a given set of principals
 * (the caller is responsible for including the user's own ID and "everyone"
 * alongside their resolved groups, per {@link GroupResolutionService}),
 * relying on the {@code piPrincipalNameLookup} Oak property index on
 * {@code rep:principalName} to keep the lookup fast instead of a full
 * repository traversal.
 */
public interface ACLCollectionService {

    /**
     * @param session    an active JCR session with read access to the ACEs in question
     * @param principals principal names to collect ACEs for (groups, the user's own ID, "everyone")
     * @param rootPath   if non-null and not "/", restricts results to ACEs on rootPath or its
     *                   descendants, plus ACEs on ancestor paths (which inherit down into rootPath)
     * @return every matching ACE grouped by the content path it governs, or a result with
     *         {@code error} set if the query failed
     */
    ACLCollectionResult collectACLs(Session session, Set<String> principals, String rootPath);
}
