package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of resolving a user's full group membership graph (direct + nested).
 * On failure, {@code error} is set and the other fields are left empty.
 */
public class GroupResolutionResult {

    private String userId;
    private String userPath;
    private List<String> directGroups = new ArrayList<>();
    private Map<String, GroupInfo> allGroups = new LinkedHashMap<>();
    private String error;

    public static GroupResolutionResult error(String userId, String message) {
        GroupResolutionResult result = new GroupResolutionResult();
        result.userId = userId;
        result.error = message;
        return result;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserPath() {
        return userPath;
    }

    public void setUserPath(String userPath) {
        this.userPath = userPath;
    }

    public List<String> getDirectGroups() {
        return directGroups;
    }

    public Map<String, GroupInfo> getAllGroups() {
        return allGroups;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}
