package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.ACEInfo;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupInfo;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionEffect;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests -- no repository needed, matching the "no JCR APIs
 * needed" contract of PermissionResolverService.
 */
class PermissionResolverServiceImplTest {

    private final PermissionResolverServiceImpl resolver = new PermissionResolverServiceImpl();

    @Test
    void deeperPathWinsAndShallowerRuleIsOverridden() {
        GroupResolutionResult groups = groupsFor("john", group("groupA"));
        ACLCollectionResult acls = new ACLCollectionResult();
        acls.addACE(ace("groupA", "ALLOW", "/content", "/content", "jcr:read"));
        acls.addACE(ace("groupA", "DENY", "/content/mysite", "/content/mysite", "jcr:read"));
        acls.addACE(ace("groupA", "ALLOW", "/content/mysite/en", "/content/mysite/en", "jcr:write"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        PermissionEffect effect = result.getPermissions().get("/content/mysite/en").get("jcr:read");
        assertEquals("DENY", effect.getEffect());
        assertTrue(effect.isInherited());
        assertEquals("/content/mysite", effect.getDecidingRule().getSetAtPath());
        assertEquals(1, effect.getOverriddenRules().size());
        assertEquals("/content", effect.getOverriddenRules().get(0).getSetAtPath());
        assertEquals("OVERRIDE", effect.getAnomaly());
    }

    @Test
    void denyBeatsAllowAtSameLevelAsConflict() {
        GroupResolutionResult groups = groupsFor("john", group("groupA"), group("groupB"));
        ACLCollectionResult acls = new ACLCollectionResult();
        acls.addACE(ace("groupA", "ALLOW", "/content/mysite", "/content/mysite", "jcr:write"));
        acls.addACE(ace("groupB", "DENY", "/content/mysite", "/content/mysite", "jcr:write"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        PermissionEffect effect = result.getPermissions().get("/content/mysite").get("jcr:write");
        assertEquals("DENY", effect.getEffect());
        assertEquals("groupB", effect.getDecidingRule().getPrincipal());
        assertEquals("CONFLICT", effect.getAnomaly());
        assertEquals(1, effect.getOverriddenRules().size());
        assertEquals("groupA", effect.getOverriddenRules().get(0).getPrincipal());
    }

    @Test
    void globRestrictionNarrowsWhichDescendantsAreCovered() {
        GroupResolutionResult groups = groupsFor("john", group("groupA"));
        ACLCollectionResult acls = new ACLCollectionResult();
        ACEInfo restrictedDeny = ace("groupA", "DENY", "/content/mysite", "/content/mysite", "jcr:write");
        restrictedDeny.getRestrictions().put("rep:glob", "*/jcr:content/*");
        acls.addACE(restrictedDeny);
        acls.addACE(ace("groupA", "ALLOW", "/content/mysite/en/jcr:content/foo", "/content/mysite/en/jcr:content/foo", "jcr:read"));
        acls.addACE(ace("groupA", "ALLOW", "/content/mysite/en/other", "/content/mysite/en/other", "jcr:read"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        PermissionEffect matching = result.getPermissions().get("/content/mysite/en/jcr:content/foo").get("jcr:write");
        assertEquals("DENY", matching.getEffect());

        assertFalse(result.getPermissions().get("/content/mysite/en/other").containsKey("jcr:write"));
    }

    @Test
    void emptyGlobAppliesOnlyToTheExactNode() {
        GroupResolutionResult groups = groupsFor("john", group("groupA"));
        ACLCollectionResult acls = new ACLCollectionResult();
        ACEInfo exactOnlyDeny = ace("groupA", "DENY", "/content/mysite", "/content/mysite", "jcr:write");
        exactOnlyDeny.getRestrictions().put("rep:glob", "");
        acls.addACE(exactOnlyDeny);
        acls.addACE(ace("groupA", "ALLOW", "/content/mysite/child", "/content/mysite/child", "jcr:read"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        assertEquals("DENY", result.getPermissions().get("/content/mysite").get("jcr:write").getEffect());
        assertFalse(result.getPermissions().get("/content/mysite/child").containsKey("jcr:write"));
    }

    @Test
    void viaPicksTheShortestMembershipPath() {
        GroupInfo groupInfo = new GroupInfo("groupA", "/home/groups/g/groupA");
        groupInfo.addMembershipPath(Arrays.asList("john", "brand-a-team", "groupA"));
        groupInfo.addMembershipPath(Arrays.asList("john", "groupA"));

        GroupResolutionResult groups = new GroupResolutionResult();
        groups.setUserId("john");
        groups.getAllGroups().put("groupA", groupInfo);

        ACLCollectionResult acls = new ACLCollectionResult();
        acls.addACE(ace("groupA", "ALLOW", "/content", "/content", "jcr:read"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        assertEquals(List.of("john", "groupA"), result.getPermissions().get("/content").get("jcr:read").getDecidingRule().getVia());
    }

    @Test
    void ruleGrantedDirectlyToTheUserHasViaOfJustTheUser() {
        GroupResolutionResult groups = groupsFor("john");
        ACLCollectionResult acls = new ACLCollectionResult();
        acls.addACE(ace("john", "ALLOW", "/content", "/content", "jcr:read"));

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        assertEquals(List.of("john"), result.getPermissions().get("/content").get("jcr:read").getDecidingRule().getVia());
    }

    @Test
    void upstreamFailurePropagatesAsError() {
        GroupResolutionResult groups = GroupResolutionResult.error("john", "boom");
        ACLCollectionResult acls = new ACLCollectionResult();

        PermissionResolutionResult result = resolver.resolvePermissions(groups, acls);

        assertFalse(result.isSuccess());
        assertNull(result.getPermissions().get("/content"));
    }

    // -- fixtures --------------------------------------------------------

    private GroupResolutionResult groupsFor(String userId, GroupInfo... groups) {
        GroupResolutionResult result = new GroupResolutionResult();
        result.setUserId(userId);
        for (GroupInfo g : groups) {
            result.getAllGroups().put(g.getGroupId(), g);
        }
        return result;
    }

    private GroupInfo group(String id) {
        GroupInfo info = new GroupInfo(id, "/home/groups/x/" + id);
        info.addMembershipPath(Arrays.asList("john", id));
        return info;
    }

    private ACEInfo ace(String principal, String type, String nodePath, String contentPath, String... privileges) {
        List<String> privilegeList = Arrays.asList(privileges);
        return new ACEInfo(principal, type, privilegeList, privilegeList, nodePath + "/rep:policy/ace", contentPath);
    }
}
