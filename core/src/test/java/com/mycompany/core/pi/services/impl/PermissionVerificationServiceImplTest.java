package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.PiRepositoryTestSupport;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionEffect;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.junit.jupiter.api.Test;

import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the full pipeline (GroupResolution -> ACLCollection ->
 * PermissionResolver -> PermissionVerification) against a real repository,
 * the same way the servlet wires them together.
 */
class PermissionVerificationServiceImplTest extends PiRepositoryTestSupport {

    private final GroupResolutionServiceImpl groupResolutionService = new GroupResolutionServiceImpl();
    private final ACLCollectionServiceImpl aclCollectionService = new ACLCollectionServiceImpl();
    private final PermissionResolverServiceImpl permissionResolverService = new PermissionResolverServiceImpl();
    private final PermissionVerificationServiceImpl verificationService = new PermissionVerificationServiceImpl();

    @Test
    void verifiedEffectMatchesResolverForAStraightforwardGrant() throws Exception {
        User john = createUser("john");
        Group groupA = createGroup("groupA");
        addMember(groupA, john);
        addAce("/content", groupA.getPrincipal(), true, null, "jcr:read");

        PermissionResolutionResult result = runPipeline("john");
        verificationService.verify(adminSession, principalsFor(result), result);

        PermissionEffect effect = result.getPermissions().get("/content").get("jcr:read");
        assertTrue(result.isVerified());
        assertEquals("ALLOW", effect.getVerifiedEffect());
        assertFalse(effect.isResolverMismatch());
    }

    /**
     * Impersonation was the original approach and was dropped: a user fully
     * denied read on the path being checked can't resolve that path in their
     * own impersonated session, so hasPrivileges() would throw instead of
     * answering -- exactly the case this tool most commonly exists to
     * diagnose. Principal-set evaluation on the admin's own session has no
     * such blind spot; this locks that in.
     */
    @Test
    void verifiesEvenWhenTheUserIsFullyDeniedReadOnTheTargetPathItself() throws Exception {
        User denied = createUser("denied-user");
        Group blockedGroup = createGroup("blocked-group");
        addMember(blockedGroup, denied);
        addAce("/content", blockedGroup.getPrincipal(), false, null, "jcr:read");

        PermissionResolutionResult result = runPipeline("denied-user");
        verificationService.verify(adminSession, principalsFor(result), result);

        PermissionEffect effect = result.getPermissions().get("/content").get("jcr:read");
        assertTrue(result.isVerified());
        assertEquals("DENY", effect.getVerifiedEffect());
    }

    /**
     * Reproduces, deterministically, the exact gap found by hand against a
     * real project instance while building this feature: the resolver only
     * understands rep:glob restrictions, so a DENY restricted by rep:ntNames
     * gets treated as if it were unconditional. Here that wrongly shadows an
     * unrestricted ALLOW for a node whose type doesn't match the restriction
     * -- the resolver says DENY, the real evaluator says ALLOW, and the
     * cross-check is what's supposed to catch that.
     */
    @Test
    void verificationCatchesResolverMissingNtNamesRestriction() throws Exception {
        User john = createUser("john");
        Group groupA = createGroup("groupA");
        addMember(groupA, john);

        addAce("/content", groupA.getPrincipal(), true, null, "jcr:all");
        addNtNamesRestrictedDeny("/content", groupA.getPrincipal(), "jcr:all", "nt:folder");
        // /content/child defaults to nt:unstructured via createPath(), which does not match
        // the "nt:folder" restriction above -- so the DENY should not apply to it in reality.
        addAce("/content/child", groupA.getPrincipal(), true, null, "jcr:read");

        PermissionResolutionResult result = runPipeline("john");
        verificationService.verify(adminSession, principalsFor(result), result);

        PermissionEffect effect = result.getPermissions().get("/content/child").get("jcr:all");
        assertEquals("DENY", effect.getEffect(), "resolver's simplified trace ignores rep:ntNames and wrongly denies");
        assertEquals("ALLOW", effect.getVerifiedEffect(), "real evaluator correctly excludes the ntNames-restricted deny");
        assertTrue(effect.isResolverMismatch());
        assertTrue(result.isVerified());
    }

    /**
     * A caller without jcr:readAccessControl at the path in question can't
     * be used to verify results -- confirm that fails per-entry (null,
     * logged) rather than throwing out of the servlet request.
     */
    @Test
    void checkPrivilegeReturnsNullWhenCallerLacksReadAccessControl() throws Exception {
        createUser("unprivileged-caller");
        User bob = createUser("bob");
        Group groupA = createGroup("groupA");
        addMember(groupA, bob);
        addAce("/content", groupA.getPrincipal(), true, null, "jcr:read");

        Set<String> principalsForBob = new HashSet<>(Set.of("groupA", "bob"));

        Session callerSession = repository.login(new SimpleCredentials("unprivileged-caller", "password".toCharArray()));
        try {
            String verified = verificationService.checkPrivilege(callerSession, principalsForBob, "/content", "jcr:read");
            assertNull(verified, "a caller with no rights at /content should not be able to introspect its ACL");
        } finally {
            callerSession.logout();
        }
    }

    private Set<String> principalsFor(PermissionResolutionResult result) {
        return principalsFor(result.getUserId());
    }

    private Set<String> principalsFor(String userId) {
        GroupResolutionResult groups = groupResolutionService.resolveGroups(adminSession, userId);
        Set<String> principals = new HashSet<>(groups.getAllGroups().keySet());
        principals.add(userId);
        return principals;
    }

    private PermissionResolutionResult runPipeline(String userId) {
        GroupResolutionResult groups = groupResolutionService.resolveGroups(adminSession, userId);
        Set<String> principals = principalsFor(userId);
        ACLCollectionResult acls = aclCollectionService.collectACLs(adminSession, principals, null);
        return permissionResolverService.resolvePermissions(groups, acls);
    }

    private void addNtNamesRestrictedDeny(String path, java.security.Principal principal, String privilegeName, String ntName)
            throws Exception {
        AccessControlManager acm = adminSession.getAccessControlManager();
        Privilege privilege = acm.privilegeFromName(privilegeName);
        JackrabbitAccessControlList acl = findOrCreateAcl(acm, path);
        Map<String, Value> restrictions = new HashMap<>();
        restrictions.put("rep:ntNames", adminSession.getValueFactory().createValue(ntName, javax.jcr.PropertyType.NAME));
        acl.addEntry(principal, new Privilege[]{privilege}, false, restrictions);
        acm.setPolicy(path, acl);
        adminSession.save();
    }

    private JackrabbitAccessControlList findOrCreateAcl(AccessControlManager acm, String path) throws Exception {
        for (AccessControlPolicy policy : acm.getPolicies(path)) {
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }
        AccessControlPolicyIterator it = acm.getApplicablePolicies(path);
        while (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }
        throw new IllegalStateException("No JackrabbitAccessControlList available at " + path);
    }
}
