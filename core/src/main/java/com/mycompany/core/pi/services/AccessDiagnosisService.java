package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.AccessDiagnosisResult;
import com.mycompany.core.pi.models.PermissionResolutionResult;

import javax.jcr.Session;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Answers the actual question this tool exists for: "why can't this user
 * read/edit/access this exact path" -- for whichever privileges the caller
 * asks about, identifies whichever group's rule is actually responsible
 * (ground-truthed against the real evaluator, not just the resolver's trace),
 * lists every other group's rule that was in play but lost, and generates a
 * plain-English explanation of what to do about it.
 */
public interface AccessDiagnosisService {

    /**
     * Every privilege this tool knows how to check and diagnose, in display
     * order. Both the servlet (validating the "privileges" request
     * parameter) and the frontend dropdown draw from this same vocabulary
     * so the two can never drift apart -- the client only ever sends back
     * keys this map produced.
     */
    Map<String, String> SUPPORTED_PRIVILEGES = buildSupportedPrivileges();

    /** Used when the caller doesn't specify which privileges to diagnose. */
    Set<String> DEFAULT_PRIVILEGES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("jcr:read", "jcr:write", "jcr:all")));

    static Map<String, String> buildSupportedPrivileges() {
        Map<String, String> privileges = new LinkedHashMap<>();
        privileges.put("jcr:read", "Read");
        privileges.put("jcr:modifyProperties", "Modify Properties");
        privileges.put("jcr:addChildNodes", "Create Content (Add Child Nodes)");
        privileges.put("jcr:removeNode", "Delete This Node");
        privileges.put("jcr:removeChildNodes", "Delete Child Content");
        privileges.put("jcr:write", "Edit (Modify / Create / Delete)");
        privileges.put("jcr:readAccessControl", "View Permissions (Who Has Access)");
        privileges.put("jcr:modifyAccessControl", "Manage Permissions");
        privileges.put("jcr:versionManagement", "Version Management");
        privileges.put("jcr:lockManagement", "Locking");
        privileges.put("crx:replicate", "Publish / Replicate");
        privileges.put("jcr:all", "Full Access");
        return Collections.unmodifiableMap(privileges);
    }

    /**
     * @param session            the caller's own session, used to verify results (needs jcr:readAccessControl)
     * @param userId             the user being diagnosed
     * @param path               the exact content path they can't access
     * @param principalNames     every principal (the user's groups + their own ID) to evaluate together
     * @param permissions        the already-resolved permission trace covering {@code path}
     *                           (the caller is expected to have scoped ACLCollectionService to this path first)
     * @param requestedPrivileges which privileges (keys of {@link #SUPPORTED_PRIVILEGES}) to check; any
     *                           unrecognized entries are ignored, and {@link #DEFAULT_PRIVILEGES} is used
     *                           if this is null, empty, or contains nothing recognized
     */
    AccessDiagnosisResult diagnose(Session session, String userId, String path, Set<String> principalNames,
            PermissionResolutionResult permissions, Set<String> requestedPrivileges);
}
