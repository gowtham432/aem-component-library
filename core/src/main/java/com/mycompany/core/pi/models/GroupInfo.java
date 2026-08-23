package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.List;

/**
 * A single group in a user's membership graph, including every distinct
 * chain of memberships (userId -> ... -> groupId) by which it is reached.
 */
public class GroupInfo {

    private final String groupId;
    private final String groupPath;
    private final List<List<String>> membershipPaths = new ArrayList<>();
    private boolean direct;

    public GroupInfo(String groupId, String groupPath) {
        this.groupId = groupId;
        this.groupPath = groupPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupPath() {
        return groupPath;
    }

    /**
     * True if at least one recorded membership path is a direct
     * userId -> groupId edge (path length 2).
     */
    public boolean isDirect() {
        return direct;
    }

    public List<List<String>> getMembershipPaths() {
        return membershipPaths;
    }

    public void addMembershipPath(List<String> path) {
        membershipPaths.add(path);
        if (path.size() == 2) {
            direct = true;
        }
    }
}
