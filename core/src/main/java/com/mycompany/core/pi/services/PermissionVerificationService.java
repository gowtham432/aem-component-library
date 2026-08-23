package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.PermissionResolutionResult;

import javax.jcr.Session;
import java.util.Set;

/**
 * Cross-checks {@link PermissionResolverService}'s trace against Oak's real
 * evaluation via {@code JackrabbitAccessControlManager#hasPrivileges(String,
 * Set, Privilege[])} -- evaluated for an explicit principal set on the
 * caller's own session, not by impersonating the target user. Impersonation
 * was tried first and dropped: a user who is fully denied read on the exact
 * path being diagnosed (the most common reason someone reaches for this
 * tool) can't resolve that path in their own impersonated session, so the
 * check would throw instead of answering. Principal-set evaluation has no
 * such blind spot and only requires the caller to have jcr:readAccessControl,
 * which is the appropriate permission model for someone auditing access.
 */
public interface PermissionVerificationService {

    /**
     * @param session        the caller's own session; needs read-access-control rights at the paths involved
     * @param principalNames every principal (groups + the user's own ID) whose combined effective
     *                       privileges should be evaluated
     * @param result         resolved permissions to verify and annotate in place
     */
    void verify(Session session, Set<String> principalNames, PermissionResolutionResult result);

    /**
     * One-off ground-truth check for a single path/privilege pair, independent
     * of any prior resolution -- used where the caller needs an authoritative
     * answer even for a privilege the resolver found no candidate ACE for
     * (which, under JCR's default-deny model, is itself a valid and common answer).
     *
     * @return "ALLOW" or "DENY", or null if verification could not be performed
     */
    String checkPrivilege(Session session, Set<String> principalNames, String path, String privilegeName);
}
