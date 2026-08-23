package com.mycompany.core.pi.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Effective permission per path per privilege, resolved from the collected
 * ACEs and the user's group graph. On failure, {@code error} is set and
 * {@code permissions} is empty.
 */
public class PermissionResolutionResult {

    private String userId;
    private int totalPaths;
    private int totalConflicts;
    private int totalAllows;
    private int totalDenies;
    private Map<String, Map<String, PermissionEffect>> permissions = new LinkedHashMap<>();
    private String error;
    private boolean verified;
    private String verificationError;

    public static PermissionResolutionResult error(String userId, String message) {
        PermissionResolutionResult result = new PermissionResolutionResult();
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

    public int getTotalPaths() {
        return totalPaths;
    }

    public void setTotalPaths(int totalPaths) {
        this.totalPaths = totalPaths;
    }

    public int getTotalConflicts() {
        return totalConflicts;
    }

    public void incrementConflicts() {
        totalConflicts++;
    }

    public int getTotalAllows() {
        return totalAllows;
    }

    public void incrementAllows() {
        totalAllows++;
    }

    public int getTotalDenies() {
        return totalDenies;
    }

    public void incrementDenies() {
        totalDenies++;
    }

    public Map<String, Map<String, PermissionEffect>> getPermissions() {
        return permissions;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }

    /**
     * True once every entry in {@code permissions} has been cross-checked
     * against the real evaluator (see PermissionVerificationService). False
     * if verification was never run, or impersonation failed entirely --
     * check {@link #getVerificationError()} to tell the two apart.
     */
    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getVerificationError() {
        return verificationError;
    }

    public void setVerificationError(String verificationError) {
        this.verificationError = verificationError;
    }
}
