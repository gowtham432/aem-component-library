package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.PiRepositoryTestSupport;
import com.mycompany.core.pi.models.GroupInfo;
import com.mycompany.core.pi.models.GroupResolutionResult;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupResolutionServiceImplTest extends PiRepositoryTestSupport {

    private final GroupResolutionServiceImpl service = new GroupResolutionServiceImpl();

    @Test
    void unknownUserReturnsError() {
        GroupResolutionResult result = service.resolveGroups(adminSession, "doesNotExist");

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void directMembershipIsRecorded() throws Exception {
        User user = createUser("alice");
        Group group = createGroup("content-authors");
        addMember(group, user);

        GroupResolutionResult result = service.resolveGroups(adminSession, "alice");

        assertTrue(result.isSuccess());
        assertTrue(result.getDirectGroups().contains("content-authors"));
        GroupInfo info = result.getAllGroups().get("content-authors");
        assertNotNull(info);
        assertTrue(info.isDirect());
        assertEquals(List.of("alice", "content-authors"), info.getMembershipPaths().get(0));
    }

    /**
     * Regression test for the exact bug avoided in the resolver: using
     * Authorizable#memberOf() (transitive closure) instead of
     * declaredMemberOf() (direct parents only) inside the BFS would either
     * corrupt the recorded path length or fail to walk further than one
     * hop. A 3-level chain is the minimum case that would expose it.
     */
    @Test
    void nestedMembershipRecordsFullChainAsIndirect() throws Exception {
        User user = createUser("bob");
        Group child = createGroup("mysite-contributors");
        Group parent = createGroup("all-authors");
        addMember(child, user);
        addMember(parent, child);

        GroupResolutionResult result = service.resolveGroups(adminSession, "bob");

        assertTrue(result.getDirectGroups().contains("mysite-contributors"));
        assertFalse(result.getDirectGroups().contains("all-authors"));

        GroupInfo parentInfo = result.getAllGroups().get("all-authors");
        assertNotNull(parentInfo);
        assertFalse(parentInfo.isDirect());
        assertEquals(List.of("bob", "mysite-contributors", "all-authors"), parentInfo.getMembershipPaths().get(0));
    }

    @Test
    void groupReachedViaTwoParentsRecordsBothPaths() throws Exception {
        User user = createUser("carol");
        Group brandA = createGroup("brand-a-team");
        Group contentAuthors = createGroup("content-authors");
        Group shared = createGroup("mysite-contributors");

        addMember(brandA, user);
        addMember(contentAuthors, user);
        addMember(shared, brandA);
        addMember(shared, contentAuthors);

        GroupResolutionResult result = service.resolveGroups(adminSession, "carol");

        GroupInfo sharedInfo = result.getAllGroups().get("mysite-contributors");
        assertNotNull(sharedInfo);
        assertFalse(sharedInfo.isDirect());
        assertEquals(2, sharedInfo.getMembershipPaths().size());
    }

    @Test
    void userWithNoGroupsStillResolvesSuccessfully() throws Exception {
        createUser("dave");

        GroupResolutionResult result = service.resolveGroups(adminSession, "dave");

        assertTrue(result.isSuccess());
        assertTrue(result.getDirectGroups().isEmpty());
        assertNull(result.getError());
    }
}
