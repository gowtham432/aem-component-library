package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.PiRepositoryTestSupport;
import com.mycompany.core.pi.models.ACEInfo;
import com.mycompany.core.pi.models.ACLCollectionResult;
import org.apache.jackrabbit.api.security.user.Group;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ACLCollectionServiceImplTest extends PiRepositoryTestSupport {

    private final ACLCollectionServiceImpl service = new ACLCollectionServiceImpl();

    @Test
    void collectsAcesForGivenPrincipalsWithCorrectContentPath() throws Exception {
        Group groupA = createGroup("groupA");
        addAce("/content/mysite", groupA.getPrincipal(), true, null, "jcr:read");

        ACLCollectionResult result = service.collectACLs(adminSession, Set.of("groupA"), null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalACEs());
        List<ACEInfo> aces = result.getAclsByPath().get("/content/mysite");
        assertEquals(1, aces.size());
        assertEquals("groupA", aces.get(0).getPrincipal());
        assertEquals("ALLOW", aces.get(0).getType());
        assertEquals("/content/mysite", aces.get(0).getContentPath());
        assertTrue(aces.get(0).getPrivileges().contains("jcr:read"));
    }

    @Test
    void ignoresAcesForPrincipalsNotInTheGivenSet() throws Exception {
        Group groupA = createGroup("groupA");
        Group groupB = createGroup("groupB");
        addAce("/content/mysite", groupA.getPrincipal(), true, null, "jcr:read");
        addAce("/content/mysite", groupB.getPrincipal(), true, null, "jcr:read");

        ACLCollectionResult result = service.collectACLs(adminSession, Set.of("groupA"), null);

        assertEquals(1, result.getTotalACEs());
    }

    @Test
    void capturesGlobRestriction() throws Exception {
        Group groupA = createGroup("groupA");
        addAce("/content/mysite", groupA.getPrincipal(), false, "*/jcr:content/*", "jcr:write");

        ACLCollectionResult result = service.collectACLs(adminSession, Set.of("groupA"), null);

        ACEInfo ace = result.getAclsByPath().get("/content/mysite").get(0);
        assertEquals("DENY", ace.getType());
        assertEquals("*/jcr:content/*", ace.getRestrictions().get("rep:glob"));
    }

    @Test
    void rootPathFilterKeepsAncestorsAndDescendantsButExcludesUnrelatedBranches() throws Exception {
        Group groupA = createGroup("groupA");
        addAce("/content", groupA.getPrincipal(), true, null, "jcr:read");
        addAce("/content/mysite/en", groupA.getPrincipal(), true, null, "jcr:read");
        addAce("/content/othersite", groupA.getPrincipal(), true, null, "jcr:read");

        ACLCollectionResult result = service.collectACLs(adminSession, Set.of("groupA"), "/content/mysite");

        assertTrue(result.getAclsByPath().containsKey("/content"));
        assertTrue(result.getAclsByPath().containsKey("/content/mysite/en"));
        assertFalse(result.getAclsByPath().containsKey("/content/othersite"));
    }

    @Test
    void emptyPrincipalSetReturnsEmptyResultWithoutQuerying() throws Exception {
        ACLCollectionResult result = service.collectACLs(adminSession, Set.of(), null);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalACEs());
    }

    /**
     * A DENY jcr:all ACE must be understood as also covering jcr:read,
     * jcr:write, etc. -- otherwise PermissionResolverService's literal
     * privilege-name matching never sees it when someone asks specifically
     * about jcr:read, and wrongly reports no conflict.
     */
    @Test
    void expandsAggregatePrivilegesOnEachAce() throws Exception {
        Group groupA = createGroup("groupA");
        addAce("/content/mysite", groupA.getPrincipal(), false, null, "jcr:all");

        ACLCollectionResult result = service.collectACLs(adminSession, Set.of("groupA"), null);

        List<String> privileges = result.getAclsByPath().get("/content/mysite").get(0).getPrivileges();
        assertTrue(privileges.contains("jcr:all"));
        assertTrue(privileges.contains("jcr:read"), "jcr:all should expand to include jcr:read");
        assertTrue(privileges.contains("jcr:write"), "jcr:all should expand to include jcr:write");
    }
}
