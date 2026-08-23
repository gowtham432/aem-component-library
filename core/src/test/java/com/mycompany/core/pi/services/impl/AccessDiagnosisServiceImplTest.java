package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.PiRepositoryTestSupport;
import com.mycompany.core.pi.models.AccessDiagnosisResult;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import com.mycompany.core.pi.models.PrivilegeDiagnosis;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the exact real-world scenario this feature was built for:
 * a user in two groups, one granting jcr:read at a path, the other denying
 * jcr:all at the same path -- the tool must name the denying group as the
 * cause, not just report "denied" with no attribution.
 */
class AccessDiagnosisServiceImplTest extends PiRepositoryTestSupport {

    private final GroupResolutionServiceImpl groupResolutionService = new GroupResolutionServiceImpl();
    private final ACLCollectionServiceImpl aclCollectionService = new ACLCollectionServiceImpl();
    private final PermissionResolverServiceImpl permissionResolverService = new PermissionResolverServiceImpl();
    private final PermissionVerificationServiceImpl verificationService = new PermissionVerificationServiceImpl();
    private final AccessDiagnosisServiceImpl diagnosisService = new AccessDiagnosisServiceImpl();

    {
        diagnosisService.setVerificationServiceForTests(verificationService);
    }

    @Test
    void identifiesTheDenyingGroupAsTheCauseAmongMultipleGroups() throws Exception {
        String path = "/content/myaemproject/us";
        User groupOneUser = createUser("group1-user");
        Group group1 = createGroup("group-1");
        Group group2 = createGroup("group-2");
        addMember(group1, groupOneUser);
        addMember(group2, groupOneUser);

        addAce("/", group1.getPrincipal(), true, null, "jcr:read");
        addAce(path, group1.getPrincipal(), true, null, "jcr:read");

        addAce("/", group2.getPrincipal(), true, null, "jcr:read");
        addAce(path, group2.getPrincipal(), false, null, "jcr:all");

        AccessDiagnosisResult result = runDiagnosis("group1-user", path);

        PrivilegeDiagnosis readCheck = findCheck(result, "jcr:read");
        assertTrue(readCheck.isBlocked(), "group-2's DENY jcr:all must be recognized as covering jcr:read");
        assertEquals("group-2", readCheck.getDecidingRule().getPrincipal());
        assertEquals("DENY", readCheck.getDecidingRule().getType());
        assertEquals(1, readCheck.getOverriddenRules().size());
        assertEquals("group-1", readCheck.getOverriddenRules().get(0).getPrincipal());
        assertTrue(readCheck.getRemediation().contains("group-2"));
        assertFalse(readCheck.isMismatch(), "aggregate expansion should make the resolver's trace match the real evaluator");
    }

    @Test
    void generalizesToMoreThanTwoConflictingGroups() throws Exception {
        String path = "/content/myaemproject/us";
        User user = createUser("multi-group-user");
        Group allowGroupA = createGroup("allow-a");
        Group allowGroupB = createGroup("allow-b");
        Group denyGroup = createGroup("deny-group");
        addMember(allowGroupA, user);
        addMember(allowGroupB, user);
        addMember(denyGroup, user);

        addAce(path, allowGroupA.getPrincipal(), true, null, "jcr:read");
        addAce(path, allowGroupB.getPrincipal(), true, null, "jcr:read");
        addAce(path, denyGroup.getPrincipal(), false, null, "jcr:read");

        AccessDiagnosisResult result = runDiagnosis("multi-group-user", path);

        PrivilegeDiagnosis readCheck = findCheck(result, "jcr:read");
        assertTrue(readCheck.isBlocked());
        assertEquals("deny-group", readCheck.getDecidingRule().getPrincipal());
        assertEquals(2, readCheck.getOverriddenRules().size(), "both allow-only groups should be listed as overridden");
    }

    @Test
    void accessGrantedWhenNoDenyIsPresent() throws Exception {
        String path = "/content/myaemproject/us";
        User user = createUser("clean-user");
        Group group = createGroup("clean-group");
        addMember(group, user);
        addAce(path, group.getPrincipal(), true, null, "jcr:read");

        AccessDiagnosisResult result = runDiagnosis("clean-user", path);

        PrivilegeDiagnosis readCheck = findCheck(result, "jcr:read");
        assertFalse(readCheck.isBlocked());
        assertEquals("clean-group", readCheck.getDecidingRule().getPrincipal());
    }

    @Test
    void onlyChecksTheRequestedPrivileges() throws Exception {
        String path = "/content/myaemproject/us";
        User user = createUser("scoped-user");
        Group group = createGroup("scoped-group");
        addMember(group, user);
        addAce(path, group.getPrincipal(), true, null, "jcr:addChildNodes");

        AccessDiagnosisResult result = runDiagnosis("scoped-user", path, new HashSet<>(Set.of("jcr:addChildNodes")));

        assertEquals(1, result.getChecks().size(), "only the one requested privilege should be diagnosed");
        assertEquals("jcr:addChildNodes", result.getChecks().get(0).getPrivilege());
    }

    @Test
    void fallsBackToDefaultPrivilegesWhenNoneOfTheRequestedOnesAreRecognized() throws Exception {
        String path = "/content/myaemproject/us";
        User user = createUser("fallback-user");
        Group group = createGroup("fallback-group");
        addMember(group, user);
        addAce(path, group.getPrincipal(), true, null, "jcr:read");

        AccessDiagnosisResult result = runDiagnosis("fallback-user", path, new HashSet<>(Set.of("not-a-real-privilege")));

        assertEquals(3, result.getChecks().size(), "unrecognized privileges should fall back to the default three");
    }

    /**
     * Found via live testing against a real AEM instance: real Jackrabbit/Oak
     * does not evaluate "deny always wins at the same node" -- it evaluates
     * same-node ACEs in the order they were added, first match wins, allow or
     * deny. Our resolver's simplified model always assumes deny wins, so when
     * an ALLOW is added before a DENY on the same privilege/path, the real
     * evaluator (ground truth) can say ALLOW while the resolver's trace says
     * DENY. The bug this locks in: buildRemediation() used to graft the
     * resolver's (wrong-direction) decidingRule onto the verified outcome,
     * producing the self-contradictory "Access granted: DENY on &lt;group&gt;".
     * <p>
     * This regression test can't reproduce that exact ACE-order divergence --
     * the bundled {@code oak-jcr:2.2.0} test double doesn't replicate the
     * live instance's order-sensitive evaluation. So it triggers the same
     * class of bug (resolver and verifier disagreeing) through the other,
     * already-proven mismatch source in this test harness: the resolver
     * ignoring {@code rep:ntNames} restrictions (see
     * {@link PermissionVerificationServiceImplTest#verificationCatchesResolverMissingNtNamesRestriction()}).
     * Either source produces the same downstream bug; this is the one that's
     * reliable here.
     */
    @Test
    void remediationStaysConsistentWhenResolverAndVerifierDisagreeOnTheOutcome() throws Exception {
        String path = "/content/myaemproject/us";
        String childPath = path + "/mismatch-child";
        User user = createUser("mismatch-user");
        Group group = createGroup("mismatch-group");
        addMember(group, user);

        addAce(path, group.getPrincipal(), true, null, "jcr:all");
        addNtNamesRestrictedDeny(path, group.getPrincipal(), "jcr:all", "nt:folder");
        // childPath defaults to nt:unstructured via createPath(), which does not match
        // the "nt:folder" restriction above -- so the DENY should not apply to it in reality,
        // but the resolver's trace ignores rep:ntNames and wrongly thinks it does.
        addAce(childPath, group.getPrincipal(), true, null, "jcr:read");

        AccessDiagnosisResult result = runDiagnosis("mismatch-user", childPath, new HashSet<>(Set.of("jcr:all")));

        PrivilegeDiagnosis check = findCheck(result, "jcr:all");
        assertTrue(check.isMismatch(), "resolver's ntNames-blind trace should disagree with the real evaluator");
        assertFalse(check.isBlocked(), "the verified (real) outcome must win over the resolver's guess");
        assertFalse(check.getRemediation().contains("Access granted: DENY"),
                "must never assert a DENY rule as the reason access was granted");
        assertTrue(check.getRemediation().contains("unconfirmed lead"),
                "a mismatched decidingRule must be framed as a lead, not a confirmed cause");
    }

    private void addNtNamesRestrictedDeny(String path, java.security.Principal principal, String privilegeName, String ntName)
            throws Exception {
        javax.jcr.security.AccessControlManager acm = adminSession.getAccessControlManager();
        javax.jcr.security.Privilege privilege = acm.privilegeFromName(privilegeName);
        org.apache.jackrabbit.api.security.JackrabbitAccessControlList acl = findOrCreateAcl(acm, path);
        java.util.Map<String, javax.jcr.Value> restrictions = new java.util.HashMap<>();
        restrictions.put("rep:ntNames", adminSession.getValueFactory().createValue(ntName, javax.jcr.PropertyType.NAME));
        acl.addEntry(principal, new javax.jcr.security.Privilege[]{privilege}, false, restrictions);
        acm.setPolicy(path, acl);
        adminSession.save();
    }

    private org.apache.jackrabbit.api.security.JackrabbitAccessControlList findOrCreateAcl(
            javax.jcr.security.AccessControlManager acm, String path) throws Exception {
        for (javax.jcr.security.AccessControlPolicy policy : acm.getPolicies(path)) {
            if (policy instanceof org.apache.jackrabbit.api.security.JackrabbitAccessControlList) {
                return (org.apache.jackrabbit.api.security.JackrabbitAccessControlList) policy;
            }
        }
        javax.jcr.security.AccessControlPolicyIterator it = acm.getApplicablePolicies(path);
        while (it.hasNext()) {
            javax.jcr.security.AccessControlPolicy policy = it.nextAccessControlPolicy();
            if (policy instanceof org.apache.jackrabbit.api.security.JackrabbitAccessControlList) {
                return (org.apache.jackrabbit.api.security.JackrabbitAccessControlList) policy;
            }
        }
        throw new IllegalStateException("No JackrabbitAccessControlList available at " + path);
    }

    private AccessDiagnosisResult runDiagnosis(String userId, String path) {
        return runDiagnosis(userId, path, null);
    }

    private AccessDiagnosisResult runDiagnosis(String userId, String path, Set<String> requestedPrivileges) {
        GroupResolutionResult groups = groupResolutionService.resolveGroups(adminSession, userId);
        Set<String> principals = new HashSet<>(groups.getAllGroups().keySet());
        principals.add(userId);
        ACLCollectionResult acls = aclCollectionService.collectACLs(adminSession, principals, path);
        PermissionResolutionResult permissions = permissionResolverService.resolvePermissions(groups, acls);
        return diagnosisService.diagnose(adminSession, userId, path, principals, permissions, requestedPrivileges);
    }

    private PrivilegeDiagnosis findCheck(AccessDiagnosisResult result, String privilege) {
        Optional<PrivilegeDiagnosis> check = result.getChecks().stream()
                .filter(c -> c.getPrivilege().equals(privilege))
                .findFirst();
        assertTrue(check.isPresent(), "expected a diagnosis entry for " + privilege);
        return check.get();
    }
}
