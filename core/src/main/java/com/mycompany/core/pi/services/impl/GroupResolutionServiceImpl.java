package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.GroupInfo;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.services.GroupResolutionService;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks the group membership graph breadth-first using
 * {@link Authorizable#declaredMemberOf()} at each step (direct parents only)
 * rather than {@link Authorizable#memberOf()}, which already returns the
 * full transitive closure and would make per-hop path tracking meaningless.
 * Each group is expanded (its own parents looked up) at most once, but every
 * distinct membership chain that reaches it is still recorded.
 */
@Component(service = GroupResolutionService.class, immediate = true)
public class GroupResolutionServiceImpl implements GroupResolutionService {

    private static final Logger LOG = LoggerFactory.getLogger(GroupResolutionServiceImpl.class);

    @Override
    public GroupResolutionResult resolveGroups(Session session, String userId) {
        try {
            UserManager userManager = ((JackrabbitSession) session).getUserManager();
            Authorizable authorizable = userManager.getAuthorizable(userId);
            if (authorizable == null) {
                return GroupResolutionResult.error(userId, "User '" + userId + "' not found");
            }

            GroupResolutionResult result = new GroupResolutionResult();
            result.setUserId(userId);
            result.setUserPath(authorizable.getPath());

            Map<String, GroupInfo> groupMap = result.getAllGroups();
            Set<String> expanded = new HashSet<>();
            Deque<QueueItem> queue = new ArrayDeque<>();

            Iterator<Group> direct = authorizable.declaredMemberOf();
            while (direct.hasNext()) {
                Group group = direct.next();
                result.getDirectGroups().add(group.getID());
                queue.add(new QueueItem(group, Arrays.asList(userId, group.getID())));
            }

            while (!queue.isEmpty()) {
                QueueItem item = queue.poll();
                String groupId = item.group.getID();

                GroupInfo info = groupMap.get(groupId);
                if (info == null) {
                    info = new GroupInfo(groupId, item.group.getPath());
                    groupMap.put(groupId, info);
                }
                info.addMembershipPath(item.path);

                if (expanded.add(groupId)) {
                    Iterator<Group> parents = item.group.declaredMemberOf();
                    while (parents.hasNext()) {
                        Group parent = parents.next();
                        List<String> newPath = new ArrayList<>(item.path);
                        newPath.add(parent.getID());
                        queue.add(new QueueItem(parent, newPath));
                    }
                }
            }

            return result;
        } catch (RepositoryException e) {
            LOG.error("Failed to resolve groups for user '{}': {}", userId, e.getMessage(), e);
            return GroupResolutionResult.error(userId, "Repository error: " + e.getMessage());
        }
    }

    private static final class QueueItem {
        private final Group group;
        private final List<String> path;

        private QueueItem(Group group, List<String> path) {
            this.group = group;
            this.path = path;
        }
    }
}
